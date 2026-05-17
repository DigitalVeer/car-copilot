package com.example.carcopilot.data

import com.example.carcopilot.model.Issue

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
        val entry = dtcTable.lookup(primary.code)
            ?: error("Unknown DTC ${primary.code} — no entry in DTCTable.")
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
            mechanicDraft = entry.mechanicDraft,
        )
    }

    /**
     * Mirrors the legacy id format `<compact UTC timestamp>-<DTC>`:
     * "2026-05-14T19:42:11Z" + "P0301" → "20260514T194211Z-P0301".
     */
    private fun issueId(code: String, capturedAt: String): String {
        val compact = capturedAt.replace("-", "").replace(":", "")
        return "$compact-$code"
    }
}
