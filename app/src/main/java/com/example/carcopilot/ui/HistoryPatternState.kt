package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface HistoryPatternState {
    data object Thinking : HistoryPatternState
    data class Streaming(val partial: String) : HistoryPatternState
    data class Ready(val body: String, val isFallback: Boolean) : HistoryPatternState
}

/**
 * Live partial-extraction for the history pattern envelope. The envelope is
 * `{"patterns": [{"pattern_type": "...", "synthesis": "...", "suggested_root_cause": "..."}]}`
 * — the `synthesis` field appears first inside `patterns[0]`, so the first
 * `"synthesis": "` opener in the streamed buffer is what we render live.
 * Delegates the escape-aware scan to the shared [extractJsonStringField].
 */
data class HistoryPatternProgress(val partial: String, val complete: Boolean)

fun extractHistoryPatternInProgress(buffer: String): HistoryPatternProgress =
    extractJsonStringField(buffer, "synthesis").let { HistoryPatternProgress(it.partial, it.complete) }

/**
 * Parse the assembled response. Joins the first pattern's `synthesis` +
 * `suggested_root_cause` for the displayed body. Falls back to [fallbackBody]
 * when the JSON is malformed, the patterns array is empty (model said no
 * meaningful pattern), or the first pattern is missing a synthesis.
 */
fun parseHistoryPatternOrFallback(assembled: String, fallbackBody: String): HistoryPatternState.Ready {
    val parsed = parseTolerantJsonObject(assembled)
    if (parsed == null) {
        Log.w("HistoryPattern", "parse failed; using fallback")
        return HistoryPatternState.Ready(body = fallbackBody, isFallback = true)
    }
    val patterns = (parsed["patterns"] as? JsonArray) ?: return HistoryPatternState.Ready(
        body = fallbackBody, isFallback = true
    )
    if (patterns.isEmpty()) {
        Log.i("HistoryPattern", "model returned empty patterns; using fallback")
        return HistoryPatternState.Ready(body = fallbackBody, isFallback = true)
    }
    val first = patterns.first().jsonObject
    val synthesis = first["synthesis"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return HistoryPatternState.Ready(body = fallbackBody, isFallback = true)
    val rootCause = first["suggested_root_cause"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    val body = if (rootCause != null) "$synthesis $rootCause" else synthesis
    return HistoryPatternState.Ready(body = body, isFallback = false)
}
