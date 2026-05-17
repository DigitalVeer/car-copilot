package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface WalkthroughStepState {
    data object Thinking : WalkthroughStepState
    data class Streaming(val partial: String) : WalkthroughStepState
    data class Ready(val body: String, val isFallback: Boolean) : WalkthroughStepState
}

private val tolerantStepJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Live partial-extraction for the per-step envelope `{"body": "..."}`.
 * Structurally parallel to [DraftProgress] / [extractDraftInProgress] — kept
 * separate so the Phase-5-locked single-string extractors stay untouched and
 * each surface owns its own opener regex.
 */
data class WalkthroughStepProgress(val partial: String, val complete: Boolean)

fun extractWalkthroughStepInProgress(buffer: String): WalkthroughStepProgress {
    val match = STEP_BODY_OPENER.find(buffer) ?: return WalkthroughStepProgress("", false)
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
                        if (i + 6 > buffer.length) return WalkthroughStepProgress(sb.toString(), false)
                        val cp = buffer.substring(i + 2, i + 6).toIntOrNull(16)
                            ?: return WalkthroughStepProgress(sb.toString(), false)
                        sb.append(cp.toChar()); i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return WalkthroughStepProgress(sb.toString(), true)
            else -> { sb.append(c); i++ }
        }
    }
    return WalkthroughStepProgress(sb.toString(), false)
}

private val STEP_BODY_OPENER = Regex("\"body\"\\s*:\\s*\"")

/**
 * Parse the assembled per-step response into a Ready state, or fall back to
 * [fallbackBody] if the JSON can't be recovered. Mirrors
 * [parseDraftOrFallback] in shape — the only schema difference is the
 * envelope key (`body` vs `draft`).
 */
fun parseWalkthroughStepOrFallback(assembled: String, fallbackBody: String): WalkthroughStepState.Ready {
    val parsed = tolerantStepParse(assembled)
    if (parsed == null) {
        Log.w("WalkthroughStep", "parse failed; using fallback")
        return WalkthroughStepState.Ready(body = fallbackBody, isFallback = true)
    }
    val body = parsed["body"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return WalkthroughStepState.Ready(body = fallbackBody, isFallback = true)
    return WalkthroughStepState.Ready(body = body, isFallback = false)
}

private fun tolerantStepParse(text: String): JsonObject? {
    var s = text.trim()
    s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace("```", "")
    s = s.trim()
    val first = s.indexOf('{')
    val last = s.lastIndexOf('}')
    if (first == -1 || last == -1 || last <= first) return null
    return try {
        tolerantStepJson.parseToJsonElement(s.substring(first, last + 1)) as? JsonObject
    } catch (_: Exception) {
        null
    }
}
