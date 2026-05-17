package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface MechanicDraftState {
    data object Thinking : MechanicDraftState
    data class Streaming(val partial: String) : MechanicDraftState
    data class Ready(val draft: String, val isFallback: Boolean) : MechanicDraftState
}

private val tolerantDraftJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Mirrors [SynthesisProgress] for the draft envelope. The draft envelope is
 * `{"draft": "..."}` — single field, no good_news companion.
 */
data class DraftProgress(val partial: String, val complete: Boolean)

/**
 * Extract the draft field's currently-streamed content from a partial JSON
 * buffer. Structurally parallel to [extractSynthesisInProgress] — kept separate
 * so the Phase-5-locked synthesis extractor stays untouched.
 *
 * Returns an empty partial until the opener `"draft": "` has been seen. Past
 * the opener, walks character by character and decodes JSON string escapes
 * incrementally so the displayed text never contains a stray backslash.
 */
fun extractDraftInProgress(buffer: String): DraftProgress {
    val match = DRAFT_OPENER.find(buffer) ?: return DraftProgress("", false)
    val sb = StringBuilder()
    var i = match.range.last + 1
    while (i < buffer.length) {
        val c = buffer[i]
        when {
            c == '\\' -> {
                if (i + 1 >= buffer.length) break
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
                        if (i + 6 > buffer.length) return DraftProgress(sb.toString(), false)
                        val cp = buffer.substring(i + 2, i + 6).toIntOrNull(16)
                            ?: return DraftProgress(sb.toString(), false)
                        sb.append(cp.toChar()); i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return DraftProgress(sb.toString(), true)
            else -> { sb.append(c); i++ }
        }
    }
    return DraftProgress(sb.toString(), false)
}

private val DRAFT_OPENER = Regex("\"draft\"\\s*:\\s*\"")

/**
 * Parse the assembled Gemma response (post-streaming) into a Ready state, or
 * fall back to [fallbackDraft] if the JSON can't be recovered. Mirrors
 * [parseOrFallback] for the synthesis surface.
 */
fun parseDraftOrFallback(assembled: String, fallbackDraft: String): MechanicDraftState.Ready {
    val parsed = tolerantDraftParse(assembled)
    if (parsed == null) {
        Log.w("Draft", "parse failed; using fallback")
        return MechanicDraftState.Ready(draft = fallbackDraft, isFallback = true)
    }
    val draft = parsed["draft"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return MechanicDraftState.Ready(draft = fallbackDraft, isFallback = true)
    return MechanicDraftState.Ready(draft = draft, isFallback = false)
}

private fun tolerantDraftParse(text: String): JsonObject? {
    var s = text.trim()
    s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace("```", "")
    s = s.trim()
    val first = s.indexOf('{')
    val last = s.lastIndexOf('}')
    if (first == -1 || last == -1 || last <= first) return null
    return try {
        tolerantDraftJson.parseToJsonElement(s.substring(first, last + 1)) as? JsonObject
    } catch (_: Exception) {
        null
    }
}
