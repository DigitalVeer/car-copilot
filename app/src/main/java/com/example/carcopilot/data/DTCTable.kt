package com.example.carcopilot.data

import com.example.carcopilot.model.IssueMeta
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.WalkthroughStep

/**
 * Per-DTC classifier output. Combined with an [OBDSnapshot] in 11B's
 * IssueBuilder to produce the [com.example.carcopilot.model.Issue] the UI
 * consumes today.
 *
 * Everything here is **derived from the code** — never from the adapter
 * and never from Gemma. CLAUDE.md is explicit: the DTC table is the
 * source of truth for severity, route, cost, and time; Gemma never
 * classifies. This type is the structural home for that data.
 *
 * [title], [subtitle], [walkthroughSteps], and [mechanicDraft] are the
 * canned baselines the UI falls back to when Gemma errors. Live
 * generation replaces them per-surface; the canned versions remain the
 * contract for the fail-soft path locked by Phase 5.
 *
 * Adding a new DTC is data work (one entry in [DTCTable.ENTRIES]) rather
 * than code work — that's the whole point of pulling this out of inline
 * construction.
 */
data class DTCEntry(
    val code: String,
    val description: String,
    val category: String,
    val severity: Severity,
    val route: Route,
    val title: String,
    val subtitle: String,
    val meta: IssueMeta,
    val walkthroughSteps: List<WalkthroughStep>,
    val mechanicDraft: String,
)

/**
 * Lookup from DTC code → [DTCEntry]. The shape lands in 11A so the
 * snapshot/builder boundary is visible at the call-site review; entries
 * are populated in 11B when the IssueBuilder wiring lands.
 */
object DTCTable {
    val ENTRIES: Map<String, DTCEntry> = emptyMap()

    fun lookup(code: String): DTCEntry? = ENTRIES[code]
}
