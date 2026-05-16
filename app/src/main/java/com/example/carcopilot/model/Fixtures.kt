package com.example.carcopilot.model

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds the misfire Issue from the bundled misfire.json fixture +
 * the deterministic P0301 entry from DTC_TABLE. Mirrors the Python
 * classifier + pipeline output (reference/python-src/{classifier,pipeline}.py).
 */
object Fixtures {
    fun loadMisfireIssue(context: Context): Issue {
        val raw = context.assets.open("misfire.json").bufferedReader().use { it.readText() }
        val fixture = Json.parseToJsonElement(raw) as JsonObject
        val vehicleJson = fixture["vehicle"] as JsonObject
        val liveData = fixture["live_data"] as JsonObject

        val vehicle = VehicleInfo(
            year = vehicleJson["year"]!!.jsonPrimitive.content.toInt(),
            make = vehicleJson["make"]!!.jsonPrimitive.content,
            model = vehicleJson["model"]!!.jsonPrimitive.content,
            mileage = vehicleJson["mileage"]?.jsonPrimitive?.content?.toIntOrNull(),
            displayName = vehicleJson["display_name"]!!.jsonPrimitive.content,
        )

        return Issue(
            id = "20260514T194211Z-P0301",
            vehicle = vehicle,
            severity = Severity.warning,
            route = Route.diy,
            category = "misfire",
            title = "Replace ignition coil — cylinder 1",
            subtitle = "A small black block on top of the engine. No lift needed. Just a 10mm socket.",
            meta = IssueMeta(
                costUsdMin = 40,
                costUsdMax = 60,
                timeMinutes = 30,
                difficulty = "easy",
                drivability = "safe for short trips",
            ),
            dtcs = listOf(DTC(code = "P0301", description = "Cylinder 1 misfire detected")),
            liveReadings = buildReadings(liveData),
            walkthroughSteps = MISFIRE_WALKTHROUGH,
            mechanicDraft = MISFIRE_MECHANIC_DRAFT,
        )
    }

    /** Mirrors pipeline._build_readings + _format_value. */
    private fun buildReadings(liveData: JsonObject): List<LiveReading> {
        return liveData.entries.map { (key, rawEl) ->
            val rawStr = rawEl.jsonPrimitive.content
            val asDouble = rawStr.toDoubleOrNull()
            val spec = ReadingSpec.SPECS[key]
            val displayKey = spec?.display ?: key
            val unit = spec?.unit
            val (status, note) = spec?.let { s ->
                if (asDouble != null) s.statusFn(asDouble) else LiveStatus.normal to null
            } ?: (LiveStatus.normal to null)
            val display = if (asDouble != null && asDouble == asDouble.toInt().toDouble())
                asDouble.toInt().toString() else rawStr
            LiveReading(key = displayKey, value = display, unit = unit, status = status, note = note)
        }
    }
}

private val MISFIRE_WALKTHROUGH: List<WalkthroughStep> = listOf(
    WalkthroughStep(
        number = 1,
        title = "Find the coils",
        body = "Pop the hood. You're looking for four small black rectangular blocks sitting on top of the engine, in a row. Each one has a single wire connector running to it. Those are the ignition coils. Cylinder 1 is closest to the timing belt side — that's the side facing the front of the car.",
        diagramHint = "Engine bay — cylinder 1 highlighted",
    ),
    WalkthroughStep(
        number = 2,
        title = "Disconnect coil 1",
        body = "Squeeze the little plastic tab on the wire connector and pull straight up — it'll release with a soft click. Then take your 10mm socket and remove the single bolt holding the coil down. Set the bolt somewhere you won't lose it (your pocket works).",
        diagramHint = "Connector + bolt — both on top of the coil",
    ),
    WalkthroughStep(
        number = 3,
        title = "Lift out the old coil",
        body = "Grab the coil at its base and pull straight up. It's seated onto the spark plug below — give it a wiggle if it's stubborn, but pull straight, not sideways. It should come out with maybe 8 inches of a thin black rubber boot attached. That's normal.",
        diagramHint = "Pull straight up — don't twist",
    ),
    WalkthroughStep(
        number = 4,
        title = "Drop in the new one",
        body = "Take the new coil out of the box. Push it straight down into the same hole until you feel it seat onto the spark plug. Bolt it back down (don't crank too hard — snug is fine). Reconnect the wire until you hear the click. Close the hood. Start the engine. The misfire should clear in a minute or two — I'll let you know.",
        diagramHint = "Snug, not tight — and listen for the click",
    ),
)

private val MISFIRE_MECHANIC_DRAFT: String = """Hi — my 2009 Corolla (187k miles) has been showing a P0301 misfire on cylinder 1 for the last few drives. O2 sensor is reading rich and idle is rough.

Looks like it might be an ignition coil but I wanted a pro to confirm before I buy parts. Could you give me a rough estimate?

I'm trying to keep costs down — happy to bring it in when you have time."""

private data class ReadingSpec(
    val display: String,
    val unit: String?,
    val statusFn: (Double) -> Pair<LiveStatus, String?>,
) {
    companion object {
        private val normal = LiveStatus.normal to null as String?
        val SPECS: Map<String, ReadingSpec> = mapOf(
            "rpm" to ReadingSpec("RPM (idle)", "rpm") { v ->
                when {
                    v < 600 -> LiveStatus.warning to "stalling"
                    v < 800 -> LiveStatus.warning to "rough"
                    else -> normal
                }
            },
            "coolant_temp_c" to ReadingSpec("Coolant temperature", "°C") { v ->
                when {
                    v > 105 -> LiveStatus.severe to "overheating"
                    v > 100 -> LiveStatus.warning to "hot"
                    v < 60 -> LiveStatus.warning to "cold"
                    else -> normal
                }
            },
            "battery_v" to ReadingSpec("Battery voltage", "V") { v ->
                when {
                    v < 11.5 -> LiveStatus.severe to "low"
                    v < 12.2 -> LiveStatus.warning to "weak"
                    else -> normal
                }
            },
            "o2_bank1_v" to ReadingSpec("O₂ sensor (bank 1)", "V") { v ->
                when {
                    v > 0.85 -> LiveStatus.warning to "rich"
                    v < 0.1 -> LiveStatus.warning to "lean"
                    else -> normal
                }
            },
            "fuel_trim_short_pct" to ReadingSpec("Short-term fuel trim", "%") { v ->
                if (kotlin.math.abs(v) >= 10) LiveStatus.warning to "compensating" else normal
            },
            "vehicle_speed_kph" to ReadingSpec("Vehicle speed", "kph") { _ -> normal },
        )
    }
}
