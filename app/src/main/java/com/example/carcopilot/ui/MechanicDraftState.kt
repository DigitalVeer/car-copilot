package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// Mirrors SynthesisState but for the mechanic draft surface.
// The mechanic_draft.md prompt produces: {"draft": "..."}

sealed interface MechanicDraftState {
    data object Thinking : MechanicDraftState
    data class Streaming(val partial: String) : MechanicDraftState
    data class Ready(val draft: String, val isFallback: Boolean) : MechanicDraftState
}

data class DraftProgress(val partial: String, val complete: Boolean)

private val tolerantJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val DRAFT_OPENER = Regex("\"draft\"\\s*:\\s*\"")

/**
 * Same incremental JSON-string extractor as [extractSynthesisInProgress],
 * targeting the "draft" field emitted by mechanic_draft.md.
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
                    '"'  -> sb.append('"').also  { i += 2 }
                    '\\' -> sb.append('\\').also { i += 2 }
                    '/'  -> sb.append('/').also  { i += 2 }
                    'n'  -> sb.append('\n').also  { i += 2 }
                    't'  -> sb.append('\t').also  { i += 2 }
                    'r'  -> sb.append('\r').also  { i += 2 }
                    'b'  -> sb.append('\b').also  { i += 2 }
                    'f'  -> sb.append('').also { i += 2 }
                    'u'  -> {
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

fun parseDraftOrFallback(assembled: String, fallback: String): MechanicDraftState.Ready {
    val parsed = tolerantParseDraft(assembled)
    if (parsed == null) {
        Log.w("MechanicDraft", "parse failed; using fallback")
        return MechanicDraftState.Ready(draft = fallback, isFallback = true)
    }
    val draft = parsed["draft"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: return MechanicDraftState.Ready(draft = fallback, isFallback = true)
    return MechanicDraftState.Ready(draft = draft, isFallback = false)
}

private fun tolerantParseDraft(text: String): JsonObject? {
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
