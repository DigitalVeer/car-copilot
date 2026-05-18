package com.example.carcopilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards extractWalkthroughPlanInProgress + parseWalkthroughPlanOrFallback in
 * WalkthroughPlanState.kt. The plan envelope is
 * `{"steps": [{"number": 1, "title": "...", "brief": "..."}, ...]}` — the
 * extractor reports how many full step objects have closed so the loading
 * state can show "Building plan… N of M steps." Parsing happens once the
 * full envelope arrives.
 *
 * Intentionally parallel-but-separate from SynthesisStateTest / MechanicDraft­
 * StateTest / HistoryPatternStateTest — the array-aware brace tracker is its
 * own concern and shares no code with the single-string extractors.
 */
class WalkthroughPlanStateTest {

    private val fallback = listOf(
        PlanStep(1, "Find the coils", "Open the hood, locate the coil pack."),
        PlanStep(2, "Swap and test", "Move coil 1 to cylinder 2 first."),
    )

    // ── extractWalkthroughPlanInProgress ──────────────────────────────────────

    @Test
    fun `returns zero before steps opener arrives`() {
        val result = extractWalkthroughPlanInProgress("")
        assertEquals(0, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `returns zero when only the envelope head has streamed`() {
        val result = extractWalkthroughPlanInProgress("""{"steps": [""")
        assertEquals(0, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `counts one complete step object`() {
        val buf = """{"steps": [{"number": 1, "title": "Find the coils", "brief": "Open the hood."}"""
        val result = extractWalkthroughPlanInProgress(buf)
        assertEquals(1, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `does not count an in-progress step at the tail`() {
        val buf = """{"steps": [{"number": 1, "title": "Find the coils", "brief": "Open the hood."}, {"number": 2, "title": "Disc"""
        val result = extractWalkthroughPlanInProgress(buf)
        assertEquals(1, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `marks complete when the closing array bracket arrives`() {
        val buf = """{"steps": [{"number": 1, "title": "Find", "brief": "Open."},{"number": 2, "title": "Done", "brief": "Close."}]}"""
        val result = extractWalkthroughPlanInProgress(buf)
        assertEquals(2, result.stepsSeen)
        assertTrue(result.complete)
    }

    @Test
    fun `is not fooled by a closing brace inside a string`() {
        // A `}` inside a title or brief would corrupt a naive depth counter.
        // The scanner must enter string-mode and ignore structural chars until the closing quote.
        val buf = """{"steps": [{"number": 1, "title": "Use this } shape", "brief": "Watch out."}"""
        val result = extractWalkthroughPlanInProgress(buf)
        assertEquals(1, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `is not fooled by an escaped quote inside a string`() {
        val buf = """{"steps": [{"number": 1, "title": "It's the \"big\" one", "brief": "Yes."}"""
        val result = extractWalkthroughPlanInProgress(buf)
        assertEquals(1, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `does not crash on buffer ending mid-escape`() {
        val result = extractWalkthroughPlanInProgress("""{"steps": [{"title": "Hello \""")
        assertEquals(0, result.stepsSeen)
        assertFalse(result.complete)
    }

    @Test
    fun `counts three full steps in a complete plan`() {
        val buf = """{"steps": [
            {"number": 1, "title": "Find", "brief": "Open."},
            {"number": 2, "title": "Swap", "brief": "Move."},
            {"number": 3, "title": "Install", "brief": "Bolt."}
        ]}""".trimIndent()
        val result = extractWalkthroughPlanInProgress(buf)
        assertEquals(3, result.stepsSeen)
        assertTrue(result.complete)
    }

    // ── parseWalkthroughPlanOrFallback ────────────────────────────────────────

    @Test
    fun `parses a valid three-step plan`() {
        val json = """{"steps": [
            {"number": 1, "title": "Find the coils", "brief": "Pop the hood."},
            {"number": 2, "title": "Disconnect coil", "brief": "Squeeze the tab."},
            {"number": 3, "title": "Install new coil", "brief": "Push it down, bolt it."}
        ]}""".trimIndent()
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(3, state.steps.size)
        assertEquals(PlanStep(1, "Find the coils", "Pop the hood."), state.steps[0])
        assertEquals(PlanStep(2, "Disconnect coil", "Squeeze the tab."), state.steps[1])
        assertEquals(PlanStep(3, "Install new coil", "Push it down, bolt it."), state.steps[2])
        assertFalse(state.isFallback)
    }

    @Test
    fun `strips markdown fences and parses`() {
        val json = "```json\n{\"steps\":[{\"number\":1,\"title\":\"Find\",\"brief\":\"Open.\"}]}\n```"
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(1, state.steps.size)
        assertEquals("Find", state.steps[0].title)
        assertFalse(state.isFallback)
    }

    @Test
    fun `trims whitespace from title and brief`() {
        val json = """{"steps":[{"number":1,"title":"  Find  ","brief":"\n Open. \n"}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals("Find", state.steps[0].title)
        assertEquals("Open.", state.steps[0].brief)
        assertFalse(state.isFallback)
    }

    @Test
    fun `defaults number from array index when missing`() {
        // Lenient on a missing number — fall back to position so the UI keeps rendering.
        val json = """{"steps":[{"title":"First","brief":"a"},{"title":"Second","brief":"b"}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(1, state.steps[0].number)
        assertEquals(2, state.steps[1].number)
        assertFalse(state.isFallback)
    }

    @Test
    fun `falls back when steps array is empty`() {
        val state = parseWalkthroughPlanOrFallback("""{"steps":[]}""", fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when steps key is missing`() {
        val state = parseWalkthroughPlanOrFallback("""{"summary":"no steps here"}""", fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when any step is missing title`() {
        // Strict on schema completeness — a half-built plan is worse than the canned baseline.
        val json = """{"steps":[{"number":1,"brief":"Open."}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when any step is missing brief`() {
        val json = """{"steps":[{"number":1,"title":"Find"}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when a title is blank`() {
        val json = """{"steps":[{"number":1,"title":"   ","brief":"Open."}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back on garbage input`() {
        val state = parseWalkthroughPlanOrFallback("not json at all", fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back on empty string`() {
        val state = parseWalkthroughPlanOrFallback("", fallback)
        assertEquals(fallback, state.steps)
        assertTrue(state.isFallback)
    }

    // ── tail balancing (real Gemma truncation shapes) ────────────────────────

    @Test
    fun `recovers when model omits the outer closing brace`() {
        // Captured 2026-05-18 on Pixel 9 + E4B: plan ends with `}]` (closing
        // last step + array) but no trailing `}`. Old parser used
        // lastIndexOf('}') which also dropped the `]`. New parser walks the
        // structure and balances at the tail.
        val truncated = """{"steps": [{"number": 1, "title": "Prep", "brief": "Check."}, {"number":2, "title": "Remove", "brief": "Pull."}, {"number":3, "title": "Install", "brief": "Push."}]"""
        val state = parseWalkthroughPlanOrFallback(truncated, fallback)
        assertEquals(3, state.steps.size)
        assertFalse(state.isFallback)
        assertEquals("Prep", state.steps[0].title)
        assertEquals("Install", state.steps[2].title)
    }

    @Test
    fun `recovers when both array and outer braces are missing`() {
        val truncated = """{"steps": [{"number": 1, "title": "Prep", "brief": "Check."}, {"number":2, "title": "Remove", "brief": "Pull."}"""
        val state = parseWalkthroughPlanOrFallback(truncated, fallback)
        assertEquals(2, state.steps.size)
        assertFalse(state.isFallback)
    }

    @Test
    fun `recovers when generation cut mid-string`() {
        // Cancelled-mid-stream from user navigation. The buffer ends inside
        // an open string — close the string, then close the open object,
        // array, and outer brace. The final step has a brief of "Sta" only,
        // which still satisfies the non-blank schema check.
        val truncated = """{"steps": [{"number": 1, "title": "Prep", "brief": "Check."}, {"number": 2, "title": "Start", "brief": "Sta"""
        val state = parseWalkthroughPlanOrFallback(truncated, fallback)
        assertEquals(2, state.steps.size)
        assertEquals("Sta", state.steps[1].brief)
        assertFalse(state.isFallback)
    }

    @Test
    fun `recovers when output ends on a structural trailing comma`() {
        // Model emitted the comma but cancelled before the next step object.
        // Strip the comma, close array + outer.
        val truncated = """{"steps": [{"number": 1, "title": "Prep", "brief": "Check."},"""
        val state = parseWalkthroughPlanOrFallback(truncated, fallback)
        assertEquals(1, state.steps.size)
        assertEquals("Prep", state.steps[0].title)
        assertFalse(state.isFallback)
    }

    @Test
    fun `already balanced input is returned unchanged shape`() {
        val balanced = """{"steps": [{"number": 1, "title": "Prep", "brief": "Check."}]}"""
        val state = parseWalkthroughPlanOrFallback(balanced, fallback)
        assertEquals(1, state.steps.size)
        assertFalse(state.isFallback)
    }

    @Test
    fun `brace inside a string is not counted as a structural close`() {
        // The literal `}` inside the brief text must not pop the structural
        // stack — otherwise the walker would think the object closed early.
        val json = """{"steps": [{"number": 1, "title": "Prep", "brief": "Watch the }} symbol."}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(1, state.steps.size)
        assertEquals("Watch the }} symbol.", state.steps[0].brief)
        assertFalse(state.isFallback)
    }

    @Test
    fun `escaped quote inside a string keeps the walker in-string`() {
        // The `\"` shouldn't be treated as a closing quote of the title.
        val json = """{"steps": [{"number": 1, "title": "She said \"go\"", "brief": "Open."}]}"""
        val state = parseWalkthroughPlanOrFallback(json, fallback)
        assertEquals(1, state.steps.size)
        assertEquals("She said \"go\"", state.steps[0].title)
    }
}
