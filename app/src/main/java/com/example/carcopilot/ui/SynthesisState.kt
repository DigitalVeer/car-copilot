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
 * Returns an empty partial until the opener `"synthesis": "` has been
 * seen. Past the opener, walks the buffer character by character and
 * decodes JSON string escapes incrementally so the displayed text never
 * contains a stray backslash or half-finished `\u` escape.
 */
fun extractSynthesisInProgress(buffer: String): SynthesisProgress {
    val match = SYNTHESIS_OPENER.find(buffer) ?: return SynthesisProgress("", false)
    val sb = StringBuilder()
    var i = match.range.last + 1
    while (i < buffer.length) {
        val c = buffer[i]
        when {
            c == '\\' -> {
                if (i + 1 >= buffer.length) break  // wait for the escape char
                when (val esc = buffer[i + 1]) {
                    '"' -> sb.append('"').also { i += 2 }
                    '\\' -> sb.append('\\').also { i += 2 }
                    '/' -> sb.append('/').also { i += 2 }
                    'n' -> sb.append('\n').also { i += 2 }
                    't' -> sb.append('\t').also { i += 2 }
                    'r' -> sb.append('\r').also { i += 2 }
                    'b' -> sb.append('\b').also { i += 2 }
                    'f' -> sb.append('\u000C').also { i += 2 }
                    'u' -> {
                        if (i + 6 > buffer.length) return SynthesisProgress(sb.toString(), false)
                        val cp = buffer.substring(i + 2, i + 6).toIntOrNull(16)
                            ?: return SynthesisProgress(sb.toString(), false)
                        sb.append(cp.toChar()); i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return SynthesisProgress(sb.toString(), true)
            else -> { sb.append(c); i++ }
        }
    }
    return SynthesisProgress(sb.toString(), false)
}

private val SYNTHESIS_OPENER = Regex("\"synthesis\"\\s*:\\s*\"")

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
