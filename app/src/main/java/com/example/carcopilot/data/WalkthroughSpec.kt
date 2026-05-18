package com.example.carcopilot.data

/**
 * A single canonical spec value pulled from a curated repair procedure.
 * Rendered as a chip in the walkthrough screen so the user always has the
 * authoritative torque, gap, time, pressure, or tool-size value visible —
 * regardless of whether Gemma paraphrased that number correctly in the
 * generated step body.
 *
 * Phase W2.1 film-around for Gemma's numeric drift: the model demonstrably
 * mangles two-digit torque values (30 Nm → 3 Nm) and ranges (20-30 →
 * 2-3) on less-rehearsed DTC paths, even under temperature 0.1 + top-p 0.5
 * and a strengthened numeric-preservation prompt. The drift survives
 * sampler and prompt tightening — so the application pins canonical values
 * deterministically beside the streamed prose. Drift in the body becomes
 * a visible delta against an authoritative chip, not invisible safety-
 * critical damage to the user's repair.
 *
 * Lives in the data layer (not model/Schema.kt) so the Phase 5 Issue
 * contract stays untouched — the walkthrough screen looks specs up by
 * DTC code via [DTCTable.lookup], the same pattern it already uses for
 * [DTCEntry.defaultHighlights].
 */
data class WalkthroughSpec(
    val label: String,
    val value: String,
)
