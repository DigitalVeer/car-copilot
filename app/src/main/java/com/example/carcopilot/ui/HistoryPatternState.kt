package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface HistoryPatternState {
    data object Thinking : HistoryPatternState
    data class Streaming(val partial: String) : HistoryPatternState
    data class Ready(val body: String, val isFallback: Boolean) : HistoryPatternState
}

private val tolerantHistoryJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Live partial-extraction for the history pattern envelope. The envelope is
 * `{"patterns": [{"pattern_type": "...", "synthesis": "...", "suggested_root_cause": "..."}]}`
 * — the `synthesis` field appears first inside `patterns[0]`, so the
 * first `"synthesis": "` opener in the streamed buffer is what we want to
 * render live. Parallel in shape to [extractSynthesisInProgress] and
 * [extractDraftInProgress] — duplicated to leave the Phase-5-locked
 * synthesis extractor untouched.
 */
data class HistoryPatternProgress(val partial: String, val complete: Boolean)

fun extractHistoryPatternInProgress(buffer: String): HistoryPatternProgress {
    val match = HISTORY_SYNTHESIS_OPENER.find(buffer) ?: return HistoryPatternProgress("", false)
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
                        if (i + 6 > buffer.length) return HistoryPatternProgress(sb.toString(), false)
                        val cp = buffer.substring(i + 2, i + 6).toIntOrNull(16)
                            ?: return HistoryPatternProgress(sb.toString(), false)
                        sb.append(cp.toChar()); i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return HistoryPatternProgress(sb.toString(), true)
            else -> { sb.append(c); i++ }
        }
    }
    return HistoryPatternProgress(sb.toString(), false)
}

private val HISTORY_SYNTHESIS_OPENER = Regex("\"synthesis\"\\s*:\\s*\"")

/**
 * Parse the assembled response. Joins the first pattern's `synthesis` +
 * `suggested_root_cause` for the displayed body. Falls back to [fallbackBody]
 * when the JSON is malformed, the patterns array is empty (model said no
 * meaningful pattern), or the first pattern is missing a synthesis.
 */
fun parseHistoryPatternOrFallback(assembled: String, fallbackBody: String): HistoryPatternState.Ready {
    val parsed = tolerantHistoryParse(assembled)
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

private fun tolerantHistoryParse(text: String): JsonObject? {
    var s = text.trim()
    s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace("```", "")
    s = s.trim()
    val first = s.indexOf('{')
    val last = s.lastIndexOf('}')
    if (first == -1 || last == -1 || last <= first) return null
    return try {
        tolerantHistoryJson.parseToJsonElement(s.substring(first, last + 1)) as? JsonObject
    } catch (_: Exception) {
        null
    }
}
