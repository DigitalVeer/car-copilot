package com.example.carcopilot.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared parsing for every Gemma streaming surface. Each surface wraps its
 * narrative in a JSON envelope (`{"synthesis": "..."}`, `{"draft": "..."}`,
 * `{"body": "..."}`, the history `patterns` array, the walkthrough `steps`
 * array). The two jobs below — decode a string field mid-stream, and recover a
 * whole object once streaming ends — used to be copy-pasted into every
 * `*State.kt`. They now live here once so a fix lands everywhere and no surface
 * can drift onto a stale variant.
 */

/** Tolerant JSON reader: unknown keys ignored, lenient syntax. Shared by all surfaces. */
internal val tolerantJsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Progress snapshot for a single streamed JSON string field. [partial] is the
 * decoded content seen so far (JSON escapes resolved); [complete] is true once
 * the unescaped closing quote arrives.
 */
data class JsonFieldProgress(val partial: String, val complete: Boolean)

/**
 * Extract the in-progress value of the first `"[field]": "..."` occurrence from
 * a partial streaming [buffer], decoding JSON string escapes incrementally so
 * the displayed text never contains a stray backslash or a half-finished `\u`
 * escape.
 *
 * Returns an empty partial until the opener has been seen. Defensive against a
 * buffer that ends mid-escape (waits for the next char) or mid-`\u` sequence
 * (returns what's decoded so far, not complete).
 */
fun extractJsonStringField(buffer: String, field: String): JsonFieldProgress {
    val match = openerFor(field).find(buffer) ?: return JsonFieldProgress("", false)
    val sb = StringBuilder()
    var i = match.range.last + 1
    while (i < buffer.length) {
        val c = buffer[i]
        when {
            c == '\\' -> {
                if (i + 1 >= buffer.length) break // wait for the escape char
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
                        if (i + 6 > buffer.length) return JsonFieldProgress(sb.toString(), false)
                        val cp = buffer.substring(i + 2, i + 6).toIntOrNull(16)
                            ?: return JsonFieldProgress(sb.toString(), false)
                        sb.append(cp.toChar()); i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return JsonFieldProgress(sb.toString(), true)
            else -> { sb.append(c); i++ }
        }
    }
    return JsonFieldProgress(sb.toString(), false)
}

/** Per-field opener regexes are compiled once and cached — these run per token. */
private val openerCache = ConcurrentHashMap<String, Regex>()
private fun openerFor(field: String): Regex =
    openerCache.getOrPut(field) { Regex("\"" + Regex.escape(field) + "\"\\s*:\\s*\"") }

/**
 * Strip markdown fences and parse the first JSON object out of [text], closing
 * any structure the model left unbalanced at the tail. Returns null when no
 * recoverable object is present.
 *
 * Built on [balanceJsonTail] so every surface gets the tolerant-tail recovery
 * the walkthrough plan parser pioneered — a stream cancelled mid-object or a
 * model that stopped before the outer `}` is recovered instead of cropped with
 * `lastIndexOf('}')` (which silently discarded trailing `]` and bounced valid
 * content to fallback; see balanceJsonTail's doc for the captured failure).
 */
fun parseTolerantJsonObject(text: String): JsonObject? {
    var s = text.trim()
    s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace("```", "")
    s = s.trim()
    val first = s.indexOf('{')
    if (first == -1) return null
    val balanced = balanceJsonTail(s, first) ?: return null
    return try {
        tolerantJsonParser.parseToJsonElement(balanced) as? JsonObject
    } catch (_: Exception) {
        null
    }
}

/**
 * Walk [text] from [startAt] tracking string boundaries and brace/bracket
 * depth, returning the substring with any unclosed structure closed at the
 * tail. Handles three real-world Gemma output shapes we hit on device:
 *
 * - Output ends with `}]` (closed last step + closed steps array) but no
 *   outer `}` — observed on the P0301 plan run, parse used to fail because
 *   `s.lastIndexOf('}')` cropped off the trailing `]` too.
 * - Output ends mid-string when generation is cancelled — close the string
 *   before closing structural braces so the JSON parser sees a valid value.
 * - Output ends with a trailing comma inside an object/array — strip it
 *   before closing or the parser rejects the dangling separator.
 *
 * Returns null when the input from [startAt] is empty (caller treats as no
 * recoverable JSON). Returns the input unchanged when it was already balanced.
 */
internal fun balanceJsonTail(text: String, startAt: Int): String? {
    if (startAt >= text.length) return null
    val sb = StringBuilder()
    var inString = false
    var escape = false
    val closers = ArrayDeque<Char>()
    var i = startAt
    while (i < text.length) {
        val c = text[i]
        sb.append(c)
        if (inString) {
            when {
                escape -> escape = false
                c == '\\' -> escape = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> closers.addLast('}')
                '[' -> closers.addLast(']')
                '}' -> if (closers.lastOrNull() == '}') closers.removeLast()
                ']' -> if (closers.lastOrNull() == ']') closers.removeLast()
            }
        }
        i++
    }
    if (sb.isEmpty()) return null
    // Mid-escape at EOF: drop the orphan backslash so the close-quote below
    // doesn't accidentally extend an escape sequence.
    if (escape && sb.isNotEmpty() && sb.last() == '\\') sb.deleteCharAt(sb.length - 1)
    // Close an open string before touching structural braces, otherwise a
    // dangling `, "title": "Star` becomes invalid JSON the moment we try
    // to append `]` or `}`.
    if (inString) sb.append('"')
    // Drop a trailing structural comma (and any whitespace after it). Only
    // strip what's outside a string — we already closed any open string.
    while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.deleteCharAt(sb.length - 1)
    if (sb.isNotEmpty() && sb.last() == ',') {
        sb.deleteCharAt(sb.length - 1)
        while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.deleteCharAt(sb.length - 1)
    }
    while (closers.isNotEmpty()) sb.append(closers.removeLast())
    return sb.toString()
}
