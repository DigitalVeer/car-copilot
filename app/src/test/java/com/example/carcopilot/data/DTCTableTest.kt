package com.example.carcopilot.data

import com.example.carcopilot.model.IssueMeta
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.WalkthroughStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards DTCTable's lookup behavior and the DEFAULT table's content
 * invariants. CLAUDE.md is explicit that DTCTable is the source of truth
 * for severity/route/cost/time — every screen reads from here, so a
 * silent corruption of a default entry shows up as a UX regression
 * everywhere at once. The structural tests below catch that early.
 */
class DTCTableTest {

    // ── lookup behavior ───────────────────────────────────────────────────────

    @Test
    fun `DEFAULT returns the P0301 misfire entry`() {
        val entry = DTCTable.DEFAULT.lookup("P0301")
        assertNotNull("DEFAULT must carry P0301 — the only bundled scenario", entry)
        assertEquals("P0301", entry!!.code)
    }

    @Test
    fun `DEFAULT returns null for an unknown code`() {
        // Unknown DTCs must fail loudly through IssueBuilder rather than
        // returning a "looks-similar" entry — protects against the model
        // diagnosing something the deterministic classifier doesn't know.
        assertNull(DTCTable.DEFAULT.lookup("P9999"))
    }

    @Test
    fun `lookup is case-sensitive`() {
        // ELM327 traffic always uppercases; if a lowercase code ever
        // arrives, something upstream is broken and we want to know.
        assertNull(DTCTable.DEFAULT.lookup("p0301"))
    }

    @Test
    fun `custom table built from an empty map returns null for any code`() {
        val empty = DTCTable(emptyMap())
        assertNull(empty.lookup("P0301"))
        assertNull(empty.lookup(""))
    }

    @Test
    fun `custom table built from a custom map returns the injected entry`() {
        val entry = DTCTable.DEFAULT.lookup("P0301")!!.copy(
            title = "Injected for testing",
        )
        val custom = DTCTable(mapOf("P0301" to entry))
        assertEquals("Injected for testing", custom.lookup("P0301")?.title)
    }

    // ── DEFAULT entry content invariants ──────────────────────────────────────
    //
    // These tests don't deeply inspect canned strings — content edits should
    // be free. They lock the shape: severity/route enum values, meta numeric
    // ranges, walkthrough non-empty + sequential, mechanic draft non-empty.

    @Test
    fun `P0301 entry has the contract-defined severity and route`() {
        val entry = DTCTable.DEFAULT.lookup("P0301")!!
        assertEquals(Severity.warning, entry.severity)
        assertEquals(Route.diy, entry.route)
    }

    @Test
    fun `P0301 entry has positive cost and time bounds with min less than or equal to max`() {
        val meta = DTCTable.DEFAULT.lookup("P0301")!!.meta
        val min = meta.costUsdMin
        val max = meta.costUsdMax
        val time = meta.timeMinutes
        assertNotNull(min); assertNotNull(max); assertNotNull(time)
        assertTrue("cost_min must be positive: $min", min!! > 0)
        assertTrue("cost_max must be >= cost_min: $min..$max", max!! >= min)
        assertTrue("time_minutes must be positive: $time", time!! > 0)
    }

    @Test
    fun `P0301 entry's drivability text uses friendly voice not engineer voice`() {
        // CLAUDE.md voice rules: no "telemetry" / "execution" / "agentic" — and
        // drivability text bleeds straight into the AI strip when Gemma falls
        // back. A friendlier phrase is short and unjargony.
        val drivability = DTCTable.DEFAULT.lookup("P0301")!!.meta.drivability!!
        val banned = listOf("execution", "telemetry", "diagnostic", "polling", "agentic")
        banned.forEach { word ->
            assertFalse(
                "drivability text should avoid '$word' (engineer voice): $drivability",
                drivability.contains(word, ignoreCase = true),
            )
        }
    }

    @Test
    fun `P0301 entry has at least one walkthrough step`() {
        assertTrue(DTCTable.DEFAULT.lookup("P0301")!!.walkthroughSteps.isNotEmpty())
    }

    @Test
    fun `P0301 walkthrough steps are numbered sequentially from 1`() {
        val steps = DTCTable.DEFAULT.lookup("P0301")!!.walkthroughSteps
        steps.forEachIndexed { idx, step ->
            assertEquals("step at index $idx", idx + 1, step.number)
        }
    }

    @Test
    fun `P0301 walkthrough steps all have title and body`() {
        DTCTable.DEFAULT.lookup("P0301")!!.walkthroughSteps.forEach { step ->
            assertTrue("step ${step.number} title blank", step.title.isNotBlank())
            assertTrue("step ${step.number} body blank", step.body.isNotBlank())
        }
    }

    @Test
    fun `P0301 mechanic draft is non-blank`() {
        val draft = DTCTable.DEFAULT.lookup("P0301")!!.mechanicDraft
        assertNotNull(draft)
        assertTrue("mechanic draft must not be blank", draft!!.isNotBlank())
    }

    @Test
    fun `lookup-then-copy round-trips through a new table`() {
        // Sanity check on the data-class shape — protects the canned content
        // pipeline (read DEFAULT, mutate for a test, build a new table).
        val original = DTCTable.DEFAULT.lookup("P0301")!!
        val modified = original.copy(
            description = "modified",
            meta = IssueMeta(costUsdMin = 1, costUsdMax = 1, timeMinutes = 1),
            walkthroughSteps = listOf(WalkthroughStep(1, "t", "b")),
        )
        val table = DTCTable(mapOf(original.code to modified))
        val roundTrip = table.lookup(original.code)
        assertEquals("modified", roundTrip!!.description)
        assertEquals(1, roundTrip.walkthroughSteps.size)
        assertEquals(original.code, roundTrip.code)
    }
}
