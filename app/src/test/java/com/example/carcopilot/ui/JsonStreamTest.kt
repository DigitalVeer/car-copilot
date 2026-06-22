package com.example.carcopilot.ui

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared streaming-JSON helpers in JsonStream.kt that back every
 * surface's extractor and tolerant parser. The per-surface *StateTest classes
 * still assert each surface's own behavior; this file pins the shared core so a
 * regression here is caught once rather than five times.
 */
class JsonStreamTest {

    // ── extractJsonStringField ────────────────────────────────────────────────

    @Test
    fun `returns empty before the field opener arrives`() {
        val r = extractJsonStringField("", "synthesis")
        assertEquals("", r.partial)
        assertFalse(r.complete)
    }

    @Test
    fun `ignores a different field's opener`() {
        val r = extractJsonStringField("""{"draft": "wrong"}""", "synthesis")
        assertEquals("", r.partial)
        assertFalse(r.complete)
    }

    @Test
    fun `returns partial before the closing quote`() {
        val r = extractJsonStringField("""{"body": "Pop the hood""", "body")
        assertEquals("Pop the hood", r.partial)
        assertFalse(r.complete)
    }

    @Test
    fun `marks complete on the closing quote`() {
        val r = extractJsonStringField("""{"draft": "Take a look."}""", "draft")
        assertEquals("Take a look.", r.partial)
        assertTrue(r.complete)
    }

    @Test
    fun `decodes escaped quote, backslash, newline and unicode`() {
        val r = extractJsonStringField("{\"v\": \"a \\\"b\\\" c\\\\d\\ne 88\\u00b0\"}", "v")
        assertEquals("a \"b\" c\\d\ne 88°", r.partial)
        assertTrue(r.complete)
    }

    @Test
    fun `decodes form-feed escape`() {
        val r = extractJsonStringField("{\"v\": \"a\\fb\"}", "v")
        assertEquals("ab", r.partial)
        assertTrue(r.complete)
    }

    @Test
    fun `waits when buffer ends mid-escape`() {
        val r = extractJsonStringField("""{"v": "Hello \""", "v")
        assertEquals("Hello ", r.partial)
        assertFalse(r.complete)
    }

    @Test
    fun `waits when buffer ends mid-unicode escape`() {
        val r = extractJsonStringField("""{"v": "Hi \u00""", "v")
        assertEquals("Hi ", r.partial)
        assertFalse(r.complete)
    }

    @Test
    fun `escaping a field name with regex metachars still matches literally`() {
        // Defensive: openerFor must Regex.escape the field name.
        val r = extractJsonStringField("""{"a.b": "x"}""", "a.b")
        assertEquals("x", r.partial)
        assertTrue(r.complete)
    }

    // ── parseTolerantJsonObject ───────────────────────────────────────────────

    @Test
    fun `parses a clean object`() {
        val obj = parseTolerantJsonObject("""{"k": "v"}""")
        assertNotNull(obj)
        assertEquals("v", obj!!["k"]!!.jsonPrimitive.content)
    }

    @Test
    fun `strips markdown fences`() {
        val obj = parseTolerantJsonObject("```json\n{\"k\": \"v\"}\n```")
        assertEquals("v", obj!!["k"]!!.jsonPrimitive.content)
    }

    @Test
    fun `returns null on input with no opening brace`() {
        assertNull(parseTolerantJsonObject("not json at all"))
        assertNull(parseTolerantJsonObject(""))
    }

    @Test
    fun `recovers a single-field object missing its closing brace`() {
        // This is the behavior the four single-field surfaces lacked while only
        // the plan parser had balanceJsonTail. Now every surface recovers here.
        val obj = parseTolerantJsonObject("""{"body": "Snug the bolt.""")
        assertNotNull(obj)
        assertEquals("Snug the bolt.", obj!!["body"]!!.jsonPrimitive.content)
    }

    @Test
    fun `recovers when generation is cut mid-string`() {
        val obj = parseTolerantJsonObject("""{"draft": "Hi, my Corolla has a P017""")
        assertEquals("Hi, my Corolla has a P017", obj!!["draft"]!!.jsonPrimitive.content)
    }

    @Test
    fun `drops a trailing structural comma before closing`() {
        val obj = parseTolerantJsonObject("""{"k": "v",""")
        assertEquals("v", obj!!["k"]!!.jsonPrimitive.content)
    }

    @Test
    fun `is not fooled by a closing brace inside a string`() {
        val obj = parseTolerantJsonObject("""{"k": "watch the } brace"}""")
        assertEquals("watch the } brace", obj!!["k"]!!.jsonPrimitive.content)
    }

    // ── balanceJsonTail ───────────────────────────────────────────────────────

    @Test
    fun `balanceJsonTail returns null past end of input`() {
        assertNull(balanceJsonTail("{}", 5))
    }

    @Test
    fun `balanceJsonTail leaves already-balanced input intact`() {
        val s = """{"k": "v"}"""
        assertEquals(s, balanceJsonTail(s, 0))
    }
}
