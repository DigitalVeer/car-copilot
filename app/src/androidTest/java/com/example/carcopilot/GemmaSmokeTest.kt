package com.example.carcopilot

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

private const val MODEL_FILE = "gemma-4-E4B-it.litertlm"
private const val STAGED_PATH = "/data/local/tmp/$MODEL_FILE"

/**
 * Phase 5 Checkpoint A — verify Gemma 4 E4B loads on the target device via
 * LiteRT-LM and produces parseable JSON for the production issue_synthesis
 * prompt with the misfire fixture. No UI, no app scaffold — just the one call.
 *
 * The model must be pushed to /data/local/tmp/gemma-4-E4B-it.litertlm before
 * running. See the Checkpoint A report for the adb push command.
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest
 *      adb logcat -s GemmaSmoke:V
 */
class GemmaSmokeTest {

    private val tag = "GemmaSmoke"

    @Test
    fun runSynthesisSmokeTest() {
        runBlocking { runSynthesisSmokeTestImpl() }
    }

    private suspend fun runSynthesisSmokeTestImpl() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // The GPU delegate writes a sidecar weights cache next to the model.
        // /data/local/tmp/ isn't writable by the app's UID, so we stage the
        // model in the app's private filesDir on first run.
        val modelInAppDir = stageModel(ctx.filesDir)

        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val systemPrompt = testCtx.assets.open("system.md").bufferedReader().use { it.readText() }
        val template = testCtx.assets.open("issue_synthesis.md").bufferedReader().use { it.readText() }
        val userPrompt = renderIssueSynthesisPrompt(template)

        Log.i(tag, "=== Checkpoint A start ===")
        Log.i(tag, "model: ${modelInAppDir.absolutePath} (${modelInAppDir.length()} bytes)")
        Log.i(tag, "system prompt (${systemPrompt.length} chars):\n$systemPrompt")
        Log.i(tag, "user prompt (${userPrompt.length} chars):\n$userPrompt")

        var initMs = -1L
        var inferMs = -1L
        var raw = ""

        val engine = Engine(
            EngineConfig(
                modelPath = modelInAppDir.absolutePath,
                backend = Backend.GPU()
            )
        )
        var initialized = false
        try {
            initMs = measureTimeMillis { engine.initialize() }
            initialized = true
            Log.i(tag, "engine.initialize() ms: $initMs")

            val conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    samplerConfig = SamplerConfig(
                        topK = 40,
                        topP = 0.95,
                        temperature = 0.3
                    )
                )
            )
            try {
                inferMs = measureTimeMillis {
                    raw = conversation.sendMessage(userPrompt).toString()
                }
                Log.i(tag, "inference ms: $inferMs")
            } finally {
                conversation.close()
            }

            // Streaming probe — empirically verify sendMessageAsync semantics
            // (delta token vs cumulative running text). Reuses the already
            // initialized engine, short prompt so cost is ~5s, not ~80s.
            probeStreaming(engine, systemPrompt)
        } finally {
            if (initialized) engine.close()
        }

        // Raw output goes to logcat in one block so it survives line-truncation.
        Log.i(tag, "=== RAW OUTPUT BEGIN ===")
        for (line in raw.split('\n')) Log.i(tag, line)
        Log.i(tag, "=== RAW OUTPUT END ===")

        val parsed = tolerantParse(raw)
        if (parsed == null) {
            Log.w(tag, "JSON parse FAILED (after tolerant cleanup)")
        } else {
            val synthesis = parsed["synthesis"]?.jsonPrimitive?.content
            val goodNews = parsed["good_news"]?.jsonPrimitive?.content
            Log.i(tag, "JSON parse OK")
            Log.i(tag, "synthesis: $synthesis")
            Log.i(tag, "good_news: $goodNews")
        }

        Log.i(tag, "=== Checkpoint A end ===")
        Log.i(tag, "TIMINGS init=${initMs}ms infer=${inferMs}ms total=${initMs + inferMs}ms")
    }

    /**
     * Empirically determine whether sendMessageAsync emits per-token deltas or
     * cumulative running text. Critical for the Compose wiring: deltas need
     * append, cumulative needs replace.
     */
    private suspend fun probeStreaming(engine: Engine, systemPrompt: String) {
        Log.i(tag, "=== STREAMING PROBE start ===")
        val convo = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)
            )
        )
        try {
            val emissions = mutableListOf<Pair<Long, String>>()
            val startNs = System.nanoTime()
            convo.sendMessageAsync("Say hi in five words. JSON: {\"reply\": <string>}")
                .collectIndexed { idx, message ->
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                    val text = message.toString()
                    emissions.add(elapsedMs to text)
                    Log.i(tag, "STREAM[$idx] +${elapsedMs}ms len=${text.length} text=${text.replace("\n", "\\n")}")
                }
            Log.i(tag, "STREAM total emissions: ${emissions.size}")
            if (emissions.size >= 2) {
                val first = emissions[0].second
                val second = emissions[1].second
                val last = emissions.last().second
                val secondStartsWithFirst = second.startsWith(first)
                Log.i(tag, "STREAM semantics: second.startsWith(first)=$secondStartsWithFirst")
                Log.i(tag, "STREAM semantics: first.len=${first.length} second.len=${second.length} last.len=${last.length}")
                Log.i(tag, "STREAM verdict: ${if (secondStartsWithFirst) "CUMULATIVE (each Message is full text so far)" else "DELTA (each Message is new tokens)"}")
            }
        } finally {
            convo.close()
        }
        Log.i(tag, "=== STREAMING PROBE end ===")
    }

    /**
     * Copy the staged model from /data/local/tmp into the app's private files
     * directory if it isn't already there with the matching size. Reused on
     * subsequent runs. Returns the path to use as Engine.modelPath.
     */
    private fun stageModel(filesDir: File): File {
        val staged = File(STAGED_PATH)
        require(staged.exists()) {
            "Model missing at $STAGED_PATH. Push first: " +
                "adb push $MODEL_FILE /data/local/tmp/"
        }
        val target = File(filesDir, MODEL_FILE)
        if (target.exists() && target.length() == staged.length()) {
            Log.i(tag, "model already staged at ${target.absolutePath}")
            return target
        }
        Log.i(tag, "copying model ${staged.length()} bytes → ${target.absolutePath}")
        val copyMs = measureTimeMillis {
            staged.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
            }
        }
        Log.i(tag, "model copy ms: $copyMs")
        return target
    }

    /**
     * Builds the user prompt by filling issue_synthesis.md with values derived
     * from misfire.json + the deterministic DTC table entry for P0301.
     *
     * Matches the Python pipeline + gemma_adapter exactly:
     *   - title comes from DTC_TABLE["P0301"].title_template with {cylinder}=1
     *   - subtitle, cost, time, severity, route, drivability from DTC_TABLE
     *   - live_readings rendered per _build_readings + _format_readings
     *   - dtcs rendered per _format_dtcs
     * The fixture itself (misfire.json) is read so the vehicle and live_data
     * values are sourced from the on-disk fixture, not hardcoded.
     */
    private fun renderIssueSynthesisPrompt(template: String): String {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val fixtureText = ctx.assets.open("misfire.json").bufferedReader().use { it.readText() }
        val fixture = Json.parseToJsonElement(fixtureText).let { it as JsonObject }

        val vehicle = fixture["vehicle"] as JsonObject
        val vehicleDisplay = vehicle["display_name"]!!.jsonPrimitive.content
        val mileage = vehicle["mileage"]!!.jsonPrimitive.content
        val liveData = fixture["live_data"] as JsonObject

        // Deterministic classification — these would be set by classifier.py
        // from DTC_TABLE["P0301"] with template_vars {"cylinder": "1"}.
        val severity = "warning"
        val route = "diy"
        val title = "Replace ignition coil — cylinder 1"
        val subtitle =
            "A small black block on top of the engine. No lift needed. Just a 10mm socket."
        val costMin = 40
        val costMax = 60
        val timeMinutes = 30
        val drivability = "safe for short trips"

        val dtcs = formatDtcs(listOf("P0301" to "Cylinder 1 misfire detected"))
        val readings = formatReadings(liveData)

        return template
            .replace("{vehicle}", vehicleDisplay)
            .replace("{mileage}", mileage)
            .replace("{severity}", severity)
            .replace("{route}", route)
            .replace("{title}", title)
            .replace("{subtitle}", subtitle)
            .replace("{cost_min}", costMin.toString())
            .replace("{cost_max}", costMax.toString())
            .replace("{time_minutes}", timeMinutes.toString())
            .replace("{drivability}", drivability)
            .replace("{dtcs}", dtcs)
            .replace("{live_readings}", readings)
            .replace("{language}", "en")
    }

    private fun formatDtcs(pairs: List<Pair<String, String>>): String =
        if (pairs.isEmpty()) "(none)"
        else pairs.joinToString("\n") { (code, desc) -> "- $code: $desc" }

    private data class ReadingSpec(
        val display: String,
        val unit: String?,
        val status: (Double) -> Pair<String, String?>
    )

    private val normal = "normal" to null as String?
    private val readingSpecs: Map<String, ReadingSpec> = mapOf(
        "rpm" to ReadingSpec("RPM (idle)", "rpm") { v ->
            when {
                v < 600 -> "warning" to "stalling"
                v < 800 -> "warning" to "rough"
                else -> normal
            }
        },
        "coolant_temp_c" to ReadingSpec("Coolant temperature", "°C") { v ->
            when {
                v > 105 -> "severe" to "overheating"
                v > 100 -> "warning" to "hot"
                v < 60 -> "warning" to "cold"
                else -> normal
            }
        },
        "battery_v" to ReadingSpec("Battery voltage", "V") { v ->
            when {
                v < 11.5 -> "severe" to "low"
                v < 12.2 -> "warning" to "weak"
                else -> normal
            }
        },
        "o2_bank1_v" to ReadingSpec("O₂ sensor (bank 1)", "V") { v ->
            when {
                v > 0.85 -> "warning" to "rich"
                v < 0.1 -> "warning" to "lean"
                else -> normal
            }
        },
        "fuel_trim_short_pct" to ReadingSpec("Short-term fuel trim", "%") { v ->
            if (kotlin.math.abs(v) >= 10) "warning" to "compensating" else normal
        },
        "vehicle_speed_kph" to ReadingSpec("Vehicle speed", "kph") { _ -> normal }
    )

    private fun formatReadings(liveData: JsonObject): String {
        if (liveData.isEmpty()) return "(none)"
        return liveData.entries.joinToString("\n") { (key, raw) ->
            val rawStr = raw.jsonPrimitive.content
            val asDouble = rawStr.toDoubleOrNull()
            val spec = readingSpecs[key]
            val displayKey = spec?.display ?: key
            val unitPart = spec?.unit?.let { " $it" } ?: ""
            val (status, note) =
                if (spec != null && asDouble != null) spec.status(asDouble) else normal
            val notePart = note?.let { " — $it" } ?: ""
            val display = if (asDouble != null && asDouble == asDouble.toInt().toDouble())
                asDouble.toInt().toString() else rawStr
            "- $displayKey: $display$unitPart ($status)$notePart"
        }
    }

    /**
     * Tolerant JSON extraction matching the Python adapter's resilience:
     * strip markdown fences, strip leading prose, find the first {...} block.
     * Returns null if no parseable object can be recovered.
     */
    private fun tolerantParse(text: String): JsonObject? {
        var s = text.trim()

        // Strip ```json fences and bare ``` fences anywhere they appear.
        s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
        s = s.replace("```", "")
        s = s.trim()

        // First {...} block (greedy to last matching brace).
        val first = s.indexOf('{')
        val last = s.lastIndexOf('}')
        if (first == -1 || last == -1 || last <= first) return null
        val candidate = s.substring(first, last + 1)

        return try {
            Json { ignoreUnknownKeys = true; isLenient = true }
                .parseToJsonElement(candidate) as? JsonObject
        } catch (e: Exception) {
            Log.w(tag, "tolerantParse: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
