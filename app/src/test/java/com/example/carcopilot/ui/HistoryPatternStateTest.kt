package com.example.carcopilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards extractHistoryPatternInProgress and parseHistoryPatternOrFallback in
 * HistoryPatternState.kt. The envelope is
 * `{"patterns": [{"pattern_type": "...", "synthesis": "...", "suggested_root_cause": "..."}]}`
 * — the parser surfaces the first pattern's synthesis (plus the trailing root
 * cause when present) for the History screen's pattern card.
 *
 * Parallel in shape to SynthesisStateTest and MechanicDraftStateTest — kept
 * separate because each parser owns its own extractor and tolerant parser.
 */
class HistoryPatternStateTest {

    private val fallback = "Pattern unavailable. Try again later."

    // ── extractHistoryPatternInProgress ───────────────────────────────────────

    @Test
    fun `returns empty before any synthesis opener arrives`() {
        val result = extractHistoryPatternInProgress("")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns empty for envelope without synthesis yet`() {
        val result = extractHistoryPatternInProgress("""{"patterns": [{"pattern_type": "coil_recurrence"}""")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns partial when synthesis is mid-stream inside first pattern`() {
        val buf = """{"patterns": [{"pattern_type": "coil_recurrence", "synthesis": "Two coils in seven months"""
        val result = extractHistoryPatternInProgress(buf)
        assertEquals("Two coils in seven months", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `marks complete when synthesis closing quote arrives`() {
        val buf = """{"patterns": [{"pattern_type": "coil_recurrence", "synthesis": "Two coils in seven months."}]}"""
        val result = extractHistoryPatternInProgress(buf)
        assertEquals("Two coils in seven months.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped quote inside synthesis value`() {
        val buf = """{"patterns": [{"synthesis": "It's the \"coil\" again."}]}"""
        val result = extractHistoryPatternInProgress(buf)
        assertEquals("""It's the "coil" again.""", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped newline in synthesis value`() {
        val buf = "{\"patterns\": [{\"synthesis\": \"first line\\nsecond line\"}]}"
        val result = extractHistoryPatternInProgress(buf)
        assertEquals("first line\nsecond line", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `does not crash on buffer ending mid-escape`() {
        val result = extractHistoryPatternInProgress("""{"patterns": [{"synthesis": "Hello \""")
        assertFalse(result.complete)
    }

    @Test
    fun `picks up the first synthesis when multiple patterns are present`() {
        // Only the first pattern's synthesis is what the UI shows — verify the
        // extractor doesn't accidentally skip into a later pattern.
        val buf = """{"patterns": [{"synthesis": "First."},{"synthesis": "Second."}]}"""
        val result = extractHistoryPatternInProgress(buf)
        assertEquals("First.", result.partial)
        assertTrue(result.complete)
    }

    // ── parseHistoryPatternOrFallback ─────────────────────────────────────────

    @Test
    fun `parses synthesis only (no suggested_root_cause)`() {
        val json = """{"patterns": [{"pattern_type": "coil_recurrence", "synthesis": "Two coils in seven months."}]}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals("Two coils in seven months.", state.body)
        assertFalse(state.isFallback)
    }

    @Test
    fun `parses synthesis joined with suggested_root_cause`() {
        val json = """{"patterns": [{"synthesis": "Two coils in seven months.", "suggested_root_cause": "Likely a valve cover oil leak fouling the coils."}]}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals(
            "Two coils in seven months. Likely a valve cover oil leak fouling the coils.",
            state.body,
        )
        assertFalse(state.isFallback)
    }

    @Test
    fun `strips markdown fences and parses`() {
        val json = "```json\n{\"patterns\": [{\"synthesis\": \"Repeating issue.\"}]}\n```"
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals("Repeating issue.", state.body)
        assertFalse(state.isFallback)
    }

    @Test
    fun `falls back when patterns array is empty`() {
        // Empty array means the model decided there's no meaningful pattern to
        // surface — the History card shows the canned fallback rather than nothing.
        val json = """{"patterns": []}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when patterns key is missing`() {
        val json = """{"summary": "no patterns here"}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when first pattern is missing synthesis`() {
        val json = """{"patterns": [{"pattern_type": "coil_recurrence"}]}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when synthesis is blank`() {
        val json = """{"patterns": [{"synthesis": "   "}]}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back on garbage input`() {
        val state = parseHistoryPatternOrFallback("not json at all", fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back on empty string`() {
        val state = parseHistoryPatternOrFallback("", fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `blank suggested_root_cause is treated as absent`() {
        // A whitespace-only root_cause shouldn't sneak a trailing " " into the body.
        val json = """{"patterns": [{"synthesis": "Pattern body.", "suggested_root_cause": "   "}]}"""
        val state = parseHistoryPatternOrFallback(json, fallback)
        assertEquals("Pattern body.", state.body)
        assertFalse(state.isFallback)
    }
}
