package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.jsonPrimitive

sealed interface MechanicDraftState {
    data object Thinking : MechanicDraftState
    data class Streaming(val partial: String) : MechanicDraftState
    data class Ready(val draft: String, val isFallback: Boolean) : MechanicDraftState
}

/**
 * Mirrors [SynthesisProgress] for the draft envelope. The draft envelope is
 * `{"draft": "..."}` — single field, no good_news companion.
 */
data class DraftProgress(val partial: String, val complete: Boolean)

/**
 * Extract the draft field's currently-streamed content from a partial JSON
 * buffer. Delegates to the shared [extractJsonStringField]; only the field
 * name differs from the synthesis surface.
 */
fun extractDraftInProgress(buffer: String): DraftProgress =
    extractJsonStringField(buffer, "draft").let { DraftProgress(it.partial, it.complete) }

/**
 * Parse the assembled Gemma response (post-streaming) into a Ready state, or
 * fall back to [fallbackDraft] if the JSON can't be recovered. Mirrors
 * [parseOrFallback] for the synthesis surface.
 */
fun parseDraftOrFallback(assembled: String, fallbackDraft: String): MechanicDraftState.Ready {
    val parsed = parseTolerantJsonObject(assembled)
    if (parsed == null) {
        Log.w("Draft", "parse failed; using fallback")
        return MechanicDraftState.Ready(draft = fallbackDraft, isFallback = true)
    }
    val draft = parsed["draft"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return MechanicDraftState.Ready(draft = fallbackDraft, isFallback = true)
    return MechanicDraftState.Ready(draft = draft, isFallback = false)
}
