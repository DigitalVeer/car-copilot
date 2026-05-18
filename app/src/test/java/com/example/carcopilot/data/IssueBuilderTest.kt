package com.example.carcopilot.data

import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.IssueMeta
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards IssueBuilder.build — the seam between adapter-grade [OBDSnapshot]
 * and the UI-grade [com.example.carcopilot.model.Issue]. Every screen in
 * the app reads an Issue; a regression here drops fields silently. The
 * fail-loud paths (empty DTCs, unknown DTC) matter because data-layer
 * incoherence shouldn't be papered over by Phase 5's UI fallback.
 */
class IssueBuilderTest {

    private val corolla = VehicleInfo(
        year = 2009, make = "Toyota", model = "Corolla",
        mileage = 187_000, displayName = "2009 Corolla",
    )

    private val misfireDtc = DTC("P0301", "Cylinder 1 misfire detected")

    private val rpmReading = LiveReading(
        key = "RPM (idle)", value = "740", unit = "rpm",
        status = LiveStatus.warning, note = "rough",
    )

    private fun snapshot(
        dtcs: List<DTC> = listOf(misfireDtc),
        pendingDtcs: List<DTC> = emptyList(),
        permanentDtcs: List<DTC> = emptyList(),
        liveReadings: List<LiveReading> = listOf(rpmReading),
        capturedAt: String = "2026-05-14T19:42:11Z",
    ) = OBDSnapshot(
        source = DataSource.FIXTURE,
        capturedAt = capturedAt,
        vehicle = corolla,
        engineFamily = EngineFamily.PETROL,
        dtcs = dtcs,
        pendingDtcs = pendingDtcs,
        permanentDtcs = permanentDtcs,
        liveReadings = liveReadings,
    )

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    fun `builds issue from snapshot plus default table`() {
        val issue = IssueBuilder.build(snapshot(), DTCTable.DEFAULT)
        assertEquals("Replace ignition coil — cylinder 1", issue.title)
        assertEquals(Severity.warning, issue.severity)
        assertEquals(Route.diy, issue.route)
        assertEquals("misfire", issue.category)
        assertEquals(40, issue.meta.costUsdMin)
        assertEquals(60, issue.meta.costUsdMax)
        assertEquals(30, issue.meta.timeMinutes)
        assertEquals("safe for short trips", issue.meta.drivability)
    }

    @Test
    fun `copies vehicle, DTCs, and live readings straight through from snapshot`() {
        // The adapter owns these fields; the builder must not mutate or replace them.
        val snap = snapshot()
        val issue = IssueBuilder.build(snap, DTCTable.DEFAULT)
        assertEquals(snap.vehicle, issue.vehicle)
        assertEquals(snap.dtcs, issue.dtcs)
        assertEquals(snap.liveReadings, issue.liveReadings)
    }

    @Test
    fun `walkthrough and mechanic draft come from the table entry`() {
        val issue = IssueBuilder.build(snapshot(), DTCTable.DEFAULT)
        assertTrue("walkthrough must not be empty", issue.walkthroughSteps.isNotEmpty())
        assertEquals(1, issue.walkthroughSteps.first().number)
        assertTrue(
            "mechanic draft should look like a real draft",
            issue.mechanicDraft?.startsWith("Hi") == true,
        )
    }

    @Test
    fun `picks the first DTC when multiple are present`() {
        // The current product spec is "primary DTC = first entry"; if that
        // changes (e.g. sort by severity), this test flags it explicitly so
        // the change is intentional, not accidental.
        val table = DTCTable(
            mapOf(
                "P0301" to DTCTable.DEFAULT.lookup("P0301")!!,
                "P0420" to DTCTable.DEFAULT.lookup("P0301")!!.copy(
                    code = "P0420", title = "Catalyst issue",
                ),
            )
        )
        val snap = snapshot(
            dtcs = listOf(
                DTC("P0301", "Cylinder 1 misfire detected"),
                DTC("P0420", "Catalyst efficiency below threshold"),
            )
        )
        val issue = IssueBuilder.build(snap, table)
        assertEquals("Replace ignition coil — cylinder 1", issue.title)
    }

    @Test
    fun `id format is compact UTC timestamp dash DTC`() {
        val issue = IssueBuilder.build(snapshot(capturedAt = "2026-05-14T19:42:11Z"), DTCTable.DEFAULT)
        assertEquals("20260514T194211Z-P0301", issue.id)
    }

    @Test
    fun `custom DTC table replaces the default entry`() {
        // Verifies the injection seam works — Phase 11B passes DTCTable by
        // value, not as a singleton, so tests and future scenario selection
        // can swap tables freely.
        val customEntry = DTCTable.DEFAULT.lookup("P0301")!!.copy(
            title = "Custom title for testing",
            meta = IssueMeta(costUsdMin = 999, costUsdMax = 999),
        )
        val table = DTCTable(mapOf("P0301" to customEntry))
        val issue = IssueBuilder.build(snapshot(), table)
        assertEquals("Custom title for testing", issue.title)
        assertEquals(999, issue.meta.costUsdMin)
    }

    // ── fail-loud paths ───────────────────────────────────────────────────────
    //
    // IssueBuilder's contract says these throw rather than fall back — Phase 5's
    // fail-soft path covers Gemma failures, not data-layer incoherence.

    @Test
    fun `throws when snapshot has no DTCs`() {
        try {
            IssueBuilder.build(snapshot(dtcs = emptyList()), DTCTable.DEFAULT)
            fail("expected IllegalStateException for empty DTC list")
        } catch (e: IllegalStateException) {
            assertTrue(
                "error message should mention DTCs",
                e.message?.contains("DTC", ignoreCase = true) == true,
            )
        }
    }

    @Test
    fun `unknown DTC falls back to generic entry instead of throwing`() {
        val snap = snapshot(dtcs = listOf(DTC("P9999", "Unknown fault")))
        val issue = IssueBuilder.build(snap, DTCTable.DEFAULT)
        assertEquals("P9999", issue.dtcs.first().code)
        assertTrue(
            "title should surface the raw description",
            issue.title.contains("Unknown fault"),
        )
    }

    // ── adapter-layer fields not yet surfaced in Issue ────────────────────────

    @Test
    fun `pending and permanent DTCs are not silently merged into issue dtcs`() {
        // The Issue's `dtcs` field is exactly the active list — pending and
        // permanent stay on the snapshot for whoever consumes them next.
        val snap = snapshot(
            dtcs = listOf(misfireDtc),
            pendingDtcs = listOf(DTC("P0101", "MAF range")),
            permanentDtcs = listOf(DTC("P0420", "Catalyst")),
        )
        val issue = IssueBuilder.build(snap, DTCTable.DEFAULT)
        assertEquals(listOf("P0301"), issue.dtcs.map { it.code })
    }

    @Test
    fun `walkthrough step list is shared by reference (no copy)`() {
        // The classifier table is the source of truth for canned walkthrough
        // text — the builder hands a reference, not a copy, so a regression
        // that starts deep-copying the list would show as a structural drift.
        val entry = DTCTable.DEFAULT.lookup("P0301")!!
        val issue = IssueBuilder.build(snapshot(), DTCTable.DEFAULT)
        assertSame(entry.walkthroughSteps, issue.walkthroughSteps)
    }

    @Test
    fun `walkthrough steps are sequential and non-empty`() {
        // A safety net for the canned content — a future edit that deletes
        // a step or renumbers it out of order should fail loudly here, not
        // ship as a broken Walkthrough screen.
        val issue = IssueBuilder.build(snapshot(), DTCTable.DEFAULT)
        issue.walkthroughSteps.forEachIndexed { idx, step ->
            assertEquals(idx + 1, step.number)
            assertTrue("step ${step.number} title", step.title.isNotBlank())
            assertTrue("step ${step.number} body", step.body.isNotBlank())
        }
    }

}
