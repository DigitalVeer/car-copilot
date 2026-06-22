package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.jsonPrimitive

sealed interface WalkthroughStepState {
    data object Thinking : WalkthroughStepState
    data class Streaming(val partial: String) : WalkthroughStepState
    data class Ready(val body: String, val isFallback: Boolean) : WalkthroughStepState
}

/**
 * Live partial-extraction for the per-step envelope `{"body": "..."}`.
 * Mirrors [DraftProgress] — same shape, different envelope key.
 */
data class WalkthroughStepProgress(val partial: String, val complete: Boolean)

fun extractWalkthroughStepInProgress(buffer: String): WalkthroughStepProgress =
    extractJsonStringField(buffer, "body").let { WalkthroughStepProgress(it.partial, it.complete) }

/**
 * Parse the assembled per-step response into a Ready state, or fall back to
 * [fallbackBody] if the JSON can't be recovered. Mirrors
 * [parseDraftOrFallback] in shape — the only schema difference is the
 * envelope key (`body` vs `draft`).
 */
fun parseWalkthroughStepOrFallback(assembled: String, fallbackBody: String): WalkthroughStepState.Ready {
    val parsed = parseTolerantJsonObject(assembled)
    if (parsed == null) {
        Log.w("WalkthroughStep", "parse failed; using fallback")
        return WalkthroughStepState.Ready(body = fallbackBody, isFallback = true)
    }
    val body = parsed["body"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return WalkthroughStepState.Ready(body = fallbackBody, isFallback = true)
    return WalkthroughStepState.Ready(body = body, isFallback = false)
}
