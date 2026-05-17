package com.example.carcopilot.ui

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed plan step. [number] is 1-indexed to match the JSON envelope and the
 * StepPill/StepProgress conventions already in use. [brief] is the one-sentence
 * preview that doubles as the per-step prompt input — keep it terse.
 */
data class PlanStep(val number: Int, val title: String, val brief: String)

sealed interface WalkthroughPlanState {
    data object Thinking : WalkthroughPlanState

    /**
     * Plan is still streaming. [stepsSeen] counts complete `{...}` objects
     * spotted inside the `steps` array so the loading state can render
     * "Building plan… 2 of 3 steps so far" without parsing every partial title.
     */
    data class Streaming(val stepsSeen: Int) : WalkthroughPlanState

    data class Ready(val steps: List<PlanStep>, val isFallback: Boolean) : WalkthroughPlanState
}

private val tolerantPlanJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Progress snapshot for a partially-streamed plan envelope.
 *
 * [stepsSeen] is the number of fully-closed `{...}` objects observed inside
 * the `"steps": [...]` array; the in-progress object at the tail is not
 * counted. [complete] is true once the matching `]` for the array arrives.
 *
 * Kept deliberately coarser than the single-string extractors — partial title
 * text inside an in-progress step object is not surfaced, since the UI only
 * needs an "X of Y" counter while it waits.
 */
data class WalkthroughPlanProgress(val stepsSeen: Int, val complete: Boolean)

/**
 * Walk a partial plan buffer counting complete step objects in `"steps": [...]`.
 *
 * Structurally parallel to [extractSynthesisInProgress] in role (live view of a
 * still-streaming JSON envelope) but with array-aware semantics — the plan
 * payload is an array of objects, not a single string field. Intentionally
 * duplicated rather than generalized: the single-string extractors stay simple
 * and Phase-5-locked, this one carries its own brace tracker.
 *
 * The scanner is string-aware (won't be fooled by `"}"` inside a title or
 * brief) and escape-aware (won't be fooled by `\"`). Defensive against
 * a buffer that ends in the middle of an escape, the middle of a string,
 * or the middle of an object.
 */
fun extractWalkthroughPlanInProgress(buffer: String): WalkthroughPlanProgress {
    val arrayStart = STEPS_OPENER.find(buffer)?.range?.last?.plus(1)
        ?: return WalkthroughPlanProgress(0, false)
    var i = arrayStart
    var depth = 0
    var inString = false
    var escape = false
    var stepsSeen = 0
    while (i < buffer.length) {
        val c = buffer[i]
        if (inString) {
            when {
                escape -> escape = false
                c == '\\' -> escape = true
                c == '"' -> inString = false
            }
            i++
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) stepsSeen++
            }
            ']' -> {
                if (depth == 0) return WalkthroughPlanProgress(stepsSeen, true)
            }
        }
        i++
    }
    return WalkthroughPlanProgress(stepsSeen, false)
}

private val STEPS_OPENER = Regex("\"steps\"\\s*:\\s*\\[")

/**
 * Parse the assembled plan envelope into a Ready state, or fall back if the
 * JSON can't be recovered. Mirrors [parseOrFallback]'s tolerant-parse +
 * markdown-strip shape; the caller supplies the canned fallback derived from
 * [com.example.carcopilot.data.DTCEntry.walkthroughSteps].
 *
 * A pattern is considered valid only when every step object has all three
 * fields (number, title, brief) non-blank. Partial schemas fall back rather
 * than show a half-built plan — the failure mode that does the least damage.
 */
fun parseWalkthroughPlanOrFallback(
    assembled: String,
    fallback: List<PlanStep>,
): WalkthroughPlanState.Ready {
    val parsed = tolerantPlanParse(assembled)
    if (parsed == null) {
        Log.w("WalkthroughPlan", "parse failed; using fallback")
        return WalkthroughPlanState.Ready(steps = fallback, isFallback = true)
    }
    val stepsArray = (parsed["steps"] as? JsonArray) ?: return WalkthroughPlanState.Ready(
        steps = fallback, isFallback = true,
    )
    if (stepsArray.isEmpty()) {
        Log.w("WalkthroughPlan", "empty steps array; using fallback")
        return WalkthroughPlanState.Ready(steps = fallback, isFallback = true)
    }
    val steps = mutableListOf<PlanStep>()
    for ((idx, element) in stepsArray.withIndex()) {
        val obj = element as? JsonObject
            ?: return WalkthroughPlanState.Ready(steps = fallback, isFallback = true)
        val number = obj["number"]?.jsonPrimitive?.intOrNull ?: (idx + 1)
        val title = obj["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return WalkthroughPlanState.Ready(steps = fallback, isFallback = true)
        val brief = obj["brief"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return WalkthroughPlanState.Ready(steps = fallback, isFallback = true)
        steps.add(PlanStep(number = number, title = title.trim(), brief = brief.trim()))
    }
    return WalkthroughPlanState.Ready(steps = steps, isFallback = false)
}

private fun tolerantPlanParse(text: String): JsonObject? {
    var s = text.trim()
    s = s.replace(Regex("```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
    s = s.replace("```", "")
    s = s.trim()
    val first = s.indexOf('{')
    val last = s.lastIndexOf('}')
    if (first == -1 || last == -1 || last <= first) return null
    return try {
        tolerantPlanJson.parseToJsonElement(s.substring(first, last + 1)) as? JsonObject
    } catch (_: Exception) {
        null
    }
}
