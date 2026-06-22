package com.example.carcopilot.inference

import android.util.Log
import com.example.carcopilot.data.OBDSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud counterpart to [GemmaService] — a thin-slice proof of concept for the
 * direction in `STRATEGY.md`. It POSTs an [OBDSnapshot] to the `/diagnose`
 * service ([server/diagnose_service.py]) and streams the synthesis back as
 * per-token deltas, exactly like [GemmaService.streamSynthesis].
 *
 * INTENTIONALLY NOT WIRED into [com.example.carcopilot.MainActivity]. The Phase-5
 * functional contract (GemmaService, the NavHost, the LaunchedEffect collectors)
 * is locked per CLAUDE.md, and the shipped app's central claim is that no AI
 * request leaves the device. This class is an isolated demonstration of how the
 * brains would move server-side without disturbing the on-device build. To adopt
 * it for real, you'd select between this and [GemmaService] behind a shared
 * interface (the strategy keeps GemmaService as the offline fallback).
 *
 * The emitted deltas carry the same JSON envelope
 * (`{"synthesis": ..., "good_news": ...}`) the on-device path produces, so the
 * existing [com.example.carcopilot.ui.SynthesisState.extractSynthesisInProgress]
 * extractor consumes them unchanged.
 */
class CloudInferenceService(
    private val baseUrl: String = "http://10.0.2.2:8000",
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) {
    /**
     * Stream the friend-voice synthesis for [snapshot]. Emits per-token deltas
     * on [Dispatchers.IO]. Mirrors the streaming contract of
     * [GemmaService.streamSynthesis]; failures surface as an empty stream so the
     * caller can fall back to canned text / the on-device path, never crashing
     * the user (CLAUDE.md: "Fail soft").
     */
    fun streamSynthesis(snapshot: OBDSnapshot): Flow<String> = flow {
        val body = snapshotToRequestJson(snapshot).toString()
        val conn = (URL("$baseUrl/diagnose").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "diagnose HTTP ${conn.responseCode}")
                return@flow
            }
            conn.inputStream.bufferedReader().use { reader ->
                emitSseDeltas(reader) { delta -> emit(delta) }
            }
        } catch (t: Throwable) {
            // Fail soft — let the caller drop to fallback text.
            Log.w(TAG, "cloud synthesis failed: ${t.message}")
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Parse a `text/event-stream` body. Each `data:` line carries a
     * JSON-encoded token-delta string (newlines preserved through the encoding);
     * `event: done` ends the stream. [onDelta] is suspending so it can `emit`.
     */
    private suspend inline fun emitSseDeltas(
        reader: BufferedReader,
        onDelta: (String) -> Unit,
    ) {
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.startsWith("event: done") -> break
                line.startsWith("data: ") -> {
                    val payload = line.removePrefix("data: ").trim()
                    if (payload.isEmpty() || payload == "{}") continue
                    // payload is a JSON string literal, e.g. "\"Cylinder 1...\"".
                    val decoded = JSONTokener(payload).nextValue()
                    if (decoded is String) onDelta(decoded)
                }
            }
        }
    }

    /**
     * Serialize an [OBDSnapshot] into the snake_case request shape the
     * `/diagnose` service expects — the same shape as
     * `app/src/main/assets/misfire.json`.
     */
    private fun snapshotToRequestJson(snapshot: OBDSnapshot): JSONObject {
        val vehicle = JSONObject().apply {
            put("year", snapshot.vehicle.year)
            put("make", snapshot.vehicle.make)
            put("model", snapshot.vehicle.model)
            snapshot.vehicle.mileage?.let { put("mileage", it) }
            put("display_name", snapshot.vehicle.displayName)
            snapshot.vehicle.vin?.let { put("vin", it) }
        }
        val rawDtcs = JSONArray().apply { snapshot.dtcs.forEach { put(it.code) } }
        val liveData = JSONObject().apply {
            snapshot.liveReadings.forEach { put(it.key, it.value) }
        }
        return JSONObject().apply {
            put("captured_at", snapshot.capturedAt)
            put("engine_family", snapshot.engineFamily.name.lowercase())
            put("vehicle", vehicle)
            put("raw_dtcs", rawDtcs)
            put("live_data", liveData)
        }
    }

    private companion object {
        const val TAG = "CloudInference"
    }
}
