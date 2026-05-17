package com.example.carcopilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards extractDraftInProgress and parseDraftOrFallback in MechanicDraftState.kt.
 * The mechanic draft prompt produces {"draft": "..."} — these parsers strip the
 * JSON envelope so the user sees plain text, not JSON syntax.
 */
class MechanicDraftStateTest {

    private val fallback = "Hi, my car has a fault code. Can you take a look?"

    // ── extractDraftInProgress ────────────────────────────────────────────────

    @Test
    fun `returns empty before draft opener arrives`() {
        val result = extractDraftInProgress("")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns empty for unrelated JSON fields`() {
        val result = extractDraftInProgress("""{"synthesis": "something"}""")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns partial text mid-stream`() {
        val result = extractDraftInProgress("""{"draft": "Hi, my 2009 Corolla""")
        assertEquals("Hi, my 2009 Corolla", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `marks complete when closing quote arrives`() {
        val result = extractDraftInProgress("""{"draft": "Please take a look."}""")
        assertEquals("Please take a look.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped quote in draft text`() {
        val result = extractDraftInProgress("""{"draft": "The \"check engine\" light is on."}""")
        assertEquals("""The "check engine" light is on.""", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped newline in draft text`() {
        val result = extractDraftInProgress("{\"draft\": \"Hi.\\nThanks.\"}")
        assertEquals("Hi.\nThanks.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `does not crash on buffer ending mid-escape`() {
        val result = extractDraftInProgress("""{"draft": "Hello \""")
        assertFalse(result.complete)
    }

    // ── parseDraftOrFallback ──────────────────────────────────────────────────

    @Test
    fun `parses valid draft field`() {
        val json = """{"draft": "Hi, my Toyota has a P0171 code. Could you check it?"}"""
        val state = parseDraftOrFallback(json, fallback) as MechanicDraftState.Ready
        assertEquals("Hi, my Toyota has a P0171 code. Could you check it?", state.draft)
        assertFalse(state.isFallback)
    }

    @Test
    fun `strips markdown fences and parses`() {
        val json = "```json\n{\"draft\": \"Please inspect the MAF sensor.\"}\n```"
        val state = parseDraftOrFallback(json, fallback) as MechanicDraftState.Ready
        assertEquals("Please inspect the MAF sensor.", state.draft)
        assertFalse(state.isFallback)
    }

    @Test
    fun `uses caller-supplied fallback on garbage input`() {
        val state = parseDraftOrFallback("not json", fallback) as MechanicDraftState.Ready
        assertEquals(fallback, state.draft)
        assertTrue(state.isFallback)
    }

    @Test
    fun `uses fallback when draft field is blank`() {
        val json = """{"draft": "  "}"""
        val state = parseDraftOrFallback(json, fallback) as MechanicDraftState.Ready
        assertEquals(fallback, state.draft)
        assertTrue(state.isFallback)
    }

    @Test
    fun `uses fallback when draft key is missing`() {
        val json = """{"synthesis": "wrong field"}"""
        val state = parseDraftOrFallback(json, fallback) as MechanicDraftState.Ready
        assertEquals(fallback, state.draft)
        assertTrue(state.isFallback)
    }

    @Test
    fun `uses fallback on empty string`() {
        val state = parseDraftOrFallback("", fallback) as MechanicDraftState.Ready
        assertEquals(fallback, state.draft)
        assertTrue(state.isFallback)
    }
}
