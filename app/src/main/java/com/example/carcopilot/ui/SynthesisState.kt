package com.example.carcopilot.ui

import android.util.Log
import com.example.carcopilot.model.FALLBACK_GOOD_NEWS_MISFIRE
import com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE
import kotlinx.serialization.json.jsonPrimitive

sealed interface SynthesisState {
    data object Thinking : SynthesisState
    data class Streaming(val partial: String) : SynthesisState
    data class Ready(
        val synthesis: String,
        val goodNews: String?,
        val isFallback: Boolean,
    ) : SynthesisState
}

/**
 * Result of scanning a partial streaming buffer for the in-progress
 * `synthesis` field value. [partial] is the decoded content seen so far
 * (with JSON escapes resolved). [complete] is true once the unescaped
 * closing quote is reached.
 */
data class SynthesisProgress(val partial: String, val complete: Boolean)

/**
 * Extract the synthesis field's currently-streamed content from a partial
 * JSON buffer. Used during streaming to show only the synthesis text in
 * the AI strip — not the raw `{"synthesis": "..."` envelope.
 *
 * Delegates to the shared [extractJsonStringField] (escape-aware,
 * incremental). Behavior is identical to the hand-rolled scanner this surface
 * shipped through Phase 5; only the implementation is now shared so a fix
 * can't drift between surfaces.
 */
fun extractSynthesisInProgress(buffer: String): SynthesisProgress =
    extractJsonStringField(buffer, "synthesis").let { SynthesisProgress(it.partial, it.complete) }

/**
 * Parse the assembled Gemma response (post-streaming) into a Ready state, or
 * fall back if the JSON can't be recovered. Mirrors the tolerant parser in
 * GemmaSmokeTest + gemma_adapter.py.
 */
fun parseOrFallback(assembled: String): SynthesisState.Ready {
    val parsed = parseTolerantJsonObject(assembled)
    if (parsed == null) {
        Log.w("Synthesis", "parse failed; using fallback")
        return SynthesisState.Ready(
            synthesis = FALLBACK_SYNTHESIS_MISFIRE,
            goodNews = FALLBACK_GOOD_NEWS_MISFIRE,
            isFallback = true,
        )
    }
    val synthesis = parsed["synthesis"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return SynthesisState.Ready(
            synthesis = FALLBACK_SYNTHESIS_MISFIRE,
            goodNews = FALLBACK_GOOD_NEWS_MISFIRE,
            isFallback = true,
        )
    val goodNews = parsed["good_news"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    return SynthesisState.Ready(synthesis = synthesis, goodNews = goodNews, isFallback = false)
}
