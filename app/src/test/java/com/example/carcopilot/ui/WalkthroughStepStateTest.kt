package com.example.carcopilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards extractWalkthroughStepInProgress and parseWalkthroughStepOrFallback
 * in WalkthroughStepState.kt. The per-step prompt produces `{"body": "..."}`
 * — same shape as the mechanic draft envelope, just a different key.
 *
 * Parallel-but-separate from MechanicDraftStateTest by design — each surface
 * owns its own extractor and tolerant parser; tests live in their own file
 * so a change to one extractor can't accidentally regress another's coverage.
 */
class WalkthroughStepStateTest {

    private val fallback = "Snug the bolt, reconnect the wire, and start the engine."

    // ── extractWalkthroughStepInProgress ──────────────────────────────────────

    @Test
    fun `returns empty before body opener arrives`() {
        val result = extractWalkthroughStepInProgress("")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns empty for unrelated JSON fields`() {
        // synthesis is the wrong key — must not match.
        val result = extractWalkthroughStepInProgress("""{"synthesis": "something"}""")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns empty when only draft envelope present`() {
        // Belt-and-braces: an old draft-shaped buffer mustn't smuggle text through here.
        val result = extractWalkthroughStepInProgress("""{"draft": "wrong envelope"}""")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns partial text mid-stream`() {
        val result = extractWalkthroughStepInProgress("""{"body": "Pop the hood. You're looking for""")
        assertEquals("Pop the hood. You're looking for", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `marks complete when closing quote arrives`() {
        val result = extractWalkthroughStepInProgress("""{"body": "Snug, not tight."}""")
        assertEquals("Snug, not tight.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped quote in body text`() {
        val result = extractWalkthroughStepInProgress("""{"body": "It's the \"check engine\" light."}""")
        assertEquals("""It's the "check engine" light.""", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped newline in body text`() {
        val result = extractWalkthroughStepInProgress("{\"body\": \"First line.\\nSecond line.\"}")
        assertEquals("First line.\nSecond line.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `decodes unicode escape`() {
        val result = extractWalkthroughStepInProgress("""{"body": "°F is warm."}""")
        assertEquals("°F is warm.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `does not crash on buffer ending mid-escape`() {
        val result = extractWalkthroughStepInProgress("""{"body": "Hello \""")
        assertFalse(result.complete)
    }

    @Test
    fun `does not crash on buffer ending mid-unicode-escape`() {
        val result = extractWalkthroughStepInProgress("""{"body": "Hello \u00""")
        assertEquals("Hello ", result.partial)
        assertFalse(result.complete)
    }

    // ── parseWalkthroughStepOrFallback ────────────────────────────────────────

    @Test
    fun `parses valid body field`() {
        val json = """{"body": "Squeeze the plastic tab and pull the connector straight up."}"""
        val state = parseWalkthroughStepOrFallback(json, fallback)
        assertEquals("Squeeze the plastic tab and pull the connector straight up.", state.body)
        assertFalse(state.isFallback)
    }

    @Test
    fun `strips markdown fences and parses`() {
        val json = "```json\n{\"body\": \"Pop the hood.\"}\n```"
        val state = parseWalkthroughStepOrFallback(json, fallback)
        assertEquals("Pop the hood.", state.body)
        assertFalse(state.isFallback)
    }

    @Test
    fun `uses caller-supplied fallback on garbage input`() {
        val state = parseWalkthroughStepOrFallback("not json", fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `uses fallback when body field is blank`() {
        val json = """{"body": "  "}"""
        val state = parseWalkthroughStepOrFallback(json, fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `uses fallback when body key is missing`() {
        val json = """{"draft": "wrong field"}"""
        val state = parseWalkthroughStepOrFallback(json, fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }

    @Test
    fun `uses fallback on empty string`() {
        val state = parseWalkthroughStepOrFallback("", fallback)
        assertEquals(fallback, state.body)
        assertTrue(state.isFallback)
    }
}
