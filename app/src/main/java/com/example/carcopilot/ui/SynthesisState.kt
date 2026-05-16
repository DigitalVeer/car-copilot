package com.example.carcopilot.ui

import android.util.Log
import com.example.carcopilot.model.FALLBACK_GOOD_NEWS_MISFIRE
import com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

private val tolerantJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Parse the assembled Gemma response (post-streaming) into a Ready state, or
 * fall back if the JSON can't be recovered. Mirrors the tolerant parser in
 * GemmaSmokeTest + gemma_adapter.py.
 */
fun parseOrFallback(assembled: String): SynthesisState.Ready {
    val parsed = tolerantParse(assembled)
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

private fun tolerantParse(text: String): JsonObject? {
    var s = text.trim()
    s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace("```", "")
    s = s.trim()
    val first = s.indexOf('{')
    val last = s.lastIndexOf('}')
    if (first == -1 || last == -1 || last <= first) return null
    return try {
        tolerantJson.parseToJsonElement(s.substring(first, last + 1)) as? JsonObject
    } catch (_: Exception) {
        null
    }
}
