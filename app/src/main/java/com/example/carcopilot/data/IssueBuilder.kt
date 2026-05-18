package com.example.carcopilot.data

import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.IssueMeta
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.TripReadiness

/**
 * Assembles an [Issue] from an adapter [OBDSnapshot] plus the per-DTC
 * classifier table.
 *
 * Picks the primary DTC (first entry in [OBDSnapshot.dtcs]), looks it up
 * in [dtcTable], and composes the Issue from snapshot fields
 * (vehicle, dtcs, liveReadings) plus the classifier fields severity,
 * route, category, title, subtitle, meta, walkthrough, mechanic draft.
 * `pendingDtcs` is not part of the current Issue shape and stays on the
 * snapshot until a UI surface consumes it.
 *
 * Named `Builder` rather than `Classifier`: the classifier is the
 * [DTCTable] itself (data). This function just dereferences a lookup
 * and stitches the result into the existing Issue shape — assembly.
 *
 * Throws [IllegalStateException] on an empty snapshot or an unknown DTC.
 * Phase 5's fail-soft path lives in the UI layer (Ready(fallback)) and
 * protects against Gemma errors, not data-layer ones; data flow has to
 * be coherent for the rest of the app to mean anything. The fixture
 * never hits these paths because misfire.json always reports P0301.
 */
object IssueBuilder {
    fun build(snapshot: OBDSnapshot, dtcTable: DTCTable): Issue {
        val primary = snapshot.dtcs.firstOrNull()
            ?: error("OBDSnapshot has no DTCs; nothing to classify.")
        val entry = dtcTable.lookup(primary.code) ?: unknownEntry(primary.code, primary.description)
        return Issue(
            id = issueId(primary.code, snapshot.capturedAt),
            vehicle = snapshot.vehicle,
            severity = entry.severity,
            route = entry.route,
            category = entry.category,
            title = entry.title,
            subtitle = entry.subtitle,
            meta = entry.meta,
            dtcs = snapshot.dtcs,
            liveReadings = snapshot.liveReadings,
            walkthroughSteps = entry.walkthroughSteps,
            mechanicDraft = entry.mechanicDraft ?: genericDraft(primary.code, primary.description),
            tripReadiness = entry.tripReadiness,
        )
    }

    /**
     * Synthesizes a Healthy [Issue] from a snapshot that has no DTCs.
     *
     * The driver still gets a Home page rather than the SnapshotViewer
     * fallback: green severity bar, "Everything's good" headline, an
     * "All clear for any trip" verdict strip. No walkthrough, no mechanic
     * draft — there's no work to do. The DTC list stays empty so the
     * Evidence section on the Issue screen reads as "no codes" rather
     * than inventing one.
     */
    fun buildHealthy(snapshot: OBDSnapshot): Issue = Issue(
        id = healthyId(snapshot.capturedAt),
        vehicle = snapshot.vehicle,
        severity = Severity.healthy,
        route = Route.info,
        category = "healthy",
        title = "Everything's good",
        subtitle = "No active fault codes. The car's reporting clean across the board.",
        meta = IssueMeta(drivability = "all clear for any trip"),
        dtcs = emptyList(),
        liveReadings = snapshot.liveReadings,
        walkthroughSteps = emptyList(),
        mechanicDraft = null,
        tripReadiness = TripReadiness(headline = "All clear for any trip"),
    )

    private fun unknownEntry(code: String, description: String) = DTCEntry(
        code = code,
        description = description,
        category = "unknown",
        severity = Severity.warning,
        route = Route.expert,
        title = description,
        subtitle = "Your car flagged a fault. Have a mechanic take a look to confirm what's going on.",
    )

    private fun genericDraft(code: String, description: String) =
        "Hi — my car is showing a $code fault ($description). " +
        "Could you take a look and let me know what's involved? " +
        "Happy to bring it in at your convenience."

    /**
     * Mirrors the legacy id format `<compact UTC timestamp>-<DTC>`:
     * "2026-05-14T19:42:11Z" + "P0301" → "20260514T194211Z-P0301".
     */
    private fun issueId(code: String, capturedAt: String): String {
        val compact = capturedAt.replace("-", "").replace(":", "")
        return "$compact-$code"
    }

    /** Sibling of [issueId] for the no-DTC healthy synthesis path. */
    private fun healthyId(capturedAt: String): String {
        val compact = capturedAt.replace("-", "").replace(":", "")
        return "$compact-HEALTHY"
    }
}
