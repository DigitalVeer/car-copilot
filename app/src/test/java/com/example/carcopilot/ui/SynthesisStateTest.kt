package com.example.carcopilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the streaming JSON parser and tolerant full-parse logic in SynthesisState.kt.
 * These functions sit between raw Gemma token output and what the user reads — a silent
 * regression here shows as garbled or missing text on screen.
 *
 * Add a test any time you change extractSynthesisInProgress or parseOrFallback.
 */
class SynthesisStateTest {

    // ── extractSynthesisInProgress ────────────────────────────────────────────

    @Test
    fun `returns empty before synthesis opener arrives`() {
        val result = extractSynthesisInProgress("")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns empty for unrelated JSON fields`() {
        val result = extractSynthesisInProgress("""{"good_news": "everything is fine"}""")
        assertEquals("", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns partial text after opener, before closing quote`() {
        val result = extractSynthesisInProgress("""{"synthesis": "Your engine is""")
        assertEquals("Your engine is", result.partial)
        assertFalse(result.complete)
    }

    @Test
    fun `returns complete text and marks complete when closing quote arrives`() {
        val result = extractSynthesisInProgress("""{"synthesis": "Your engine is misfiring."}""")
        assertEquals("Your engine is misfiring.", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped quote inside synthesis value`() {
        val result = extractSynthesisInProgress("""{"synthesis": "It's a \"coil\" issue."}""")
        assertEquals("""It's a "coil" issue.""", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped backslash`() {
        val result = extractSynthesisInProgress("""{"synthesis": "path\\to\\fix"}""")
        assertEquals("""path\to\fix""", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles escaped newline`() {
        val result = extractSynthesisInProgress("{\"synthesis\": \"line one\\nline two\"}")
        assertEquals("line one\nline two", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `handles unicode escape sequence`() {
        // ° = degree symbol °
        val result = extractSynthesisInProgress("""{"synthesis": "temp is 88°C"}""")
        assertEquals("temp is 88°C", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `returns partial safely when buffer ends mid-escape`() {
        // Buffer cut off after backslash — should return what we have so far, not crash
        val result = extractSynthesisInProgress("""{"synthesis": "Hello \""")
        // partial is whatever was accumulated before the trailing backslash
        assertFalse(result.complete)
    }

    @Test
    fun `works with extra whitespace around colon`() {
        val result = extractSynthesisInProgress("""{"synthesis" :  "text here"}""")
        assertEquals("text here", result.partial)
        assertTrue(result.complete)
    }

    @Test
    fun `real world partial buffer mid-stream`() {
        // Simulates Gemma emitting the JSON envelope token by token
        val partial = """{"synthesis": "Your cylind"""
        val result = extractSynthesisInProgress(partial)
        assertEquals("Your cylind", result.partial)
        assertFalse(result.complete)
    }

    // ── parseOrFallback ───────────────────────────────────────────────────────

    @Test
    fun `parses valid synthesis and good_news`() {
        val json = """{"synthesis": "The MAF sensor is dirty.", "good_news": "I can walk you through it."}"""
        val state = parseOrFallback(json) as SynthesisState.Ready
        assertEquals("The MAF sensor is dirty.", state.synthesis)
        assertEquals("I can walk you through it.", state.goodNews)
        assertFalse(state.isFallback)
    }

    @Test
    fun `parses synthesis with missing good_news`() {
        val json = """{"synthesis": "Your coolant is low."}"""
        val state = parseOrFallback(json) as SynthesisState.Ready
        assertEquals("Your coolant is low.", state.synthesis)
        assertNull(state.goodNews)
        assertFalse(state.isFallback)
    }

    @Test
    fun `strips markdown fences and parses`() {
        val json = "```json\n{\"synthesis\": \"Lean condition detected.\"}\n```"
        val state = parseOrFallback(json) as SynthesisState.Ready
        assertEquals("Lean condition detected.", state.synthesis)
        assertFalse(state.isFallback)
    }

    @Test
    fun `ignores unknown keys in JSON`() {
        val json = """{"synthesis": "Misfire on cylinder 1.", "extra_field": "ignored", "good_news": null}"""
        val state = parseOrFallback(json) as SynthesisState.Ready
        assertEquals("Misfire on cylinder 1.", state.synthesis)
        assertFalse(state.isFallback)
    }

    @Test
    fun `falls back on garbage input`() {
        val state = parseOrFallback("this is not json at all") as SynthesisState.Ready
        assertTrue(state.isFallback)
        assertTrue(state.synthesis.isNotBlank())
    }

    @Test
    fun `falls back on empty string`() {
        val state = parseOrFallback("") as SynthesisState.Ready
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when synthesis field is blank`() {
        val json = """{"synthesis": "   ", "good_news": "All clear."}"""
        val state = parseOrFallback(json) as SynthesisState.Ready
        assertTrue(state.isFallback)
    }

    @Test
    fun `falls back when synthesis key is missing`() {
        val json = """{"good_news": "Looks fine.", "other": "data"}"""
        val state = parseOrFallback(json) as SynthesisState.Ready
        assertTrue(state.isFallback)
    }
}
