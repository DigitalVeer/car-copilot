package com.example.carcopilot.data

import com.example.carcopilot.model.IssueMeta
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.TripReadiness
import com.example.carcopilot.model.WalkthroughStep

/**
 * Per-DTC classifier output. Combined with an [OBDSnapshot] in
 * [IssueBuilder] to produce the [com.example.carcopilot.model.Issue] the UI
 * consumes.
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
 * Adding a new DTC is data work (one entry in [DTCTable.DEFAULT]) rather
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
    val tripReadiness: TripReadiness,
    /**
     * Engine-bay diagram targets the walkthrough screen falls back to when a
     * plan step has no specific keyword match. For misfire codes this is the
     * affected coil; future codes can extend to brake-related, cooling-loop,
     * etc. See [highlightsForPlanStep] for the per-step override logic.
     */
    val defaultHighlights: List<DiagramTarget> = emptyList(),
)

/**
 * Lookup from DTC code → [DTCEntry]. Passed by value into [IssueBuilder]
 * so tests / future scenario selection can inject alternative tables
 * without mutating a singleton.
 */
class DTCTable(private val entries: Map<String, DTCEntry>) {
    fun lookup(code: String): DTCEntry? = entries[code]

    companion object {
        /** The production table. One entry today; add more as data work. */
        val DEFAULT: DTCTable = DTCTable(
            mapOf(
                "P0301" to DTCEntry(
                    code = "P0301",
                    description = "Cylinder 1 misfire detected",
                    category = "misfire",
                    severity = Severity.warning,
                    route = Route.diy,
                    title = "Replace ignition coil — cylinder 1",
                    subtitle = "A small black block on top of the engine. No lift needed. Just a 10mm socket.",
                    meta = IssueMeta(
                        costUsdMin = 40,
                        costUsdMax = 60,
                        timeMinutes = 30,
                        difficulty = "easy",
                        drivability = "safe for short trips",
                    ),
                    walkthroughSteps = listOf(
                        WalkthroughStep(
                            number = 1,
                            title = "Find the coils",
                            body = "Pop the hood. You're looking for four small black rectangular blocks sitting on top of the engine, in a row. Each one has a single wire connector running to it. Those are the ignition coils. Cylinder 1 is closest to the timing belt side — that's the side facing the front of the car.",
                            diagramHint = "Engine bay — cylinder 1 highlighted",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Disconnect coil 1",
                            body = "Squeeze the little plastic tab on the wire connector and pull straight up — it'll release with a soft click. Then take your 10mm socket and remove the single bolt holding the coil down. Set the bolt somewhere you won't lose it (your pocket works).",
                            diagramHint = "Connector + bolt — both on top of the coil",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Lift out the old coil",
                            body = "Grab the coil at its base and pull straight up. It's seated onto the spark plug below — give it a wiggle if it's stubborn, but pull straight, not sideways. It should come out with maybe 8 inches of a thin black rubber boot attached. That's normal.",
                            diagramHint = "Pull straight up — don't twist",
                        ),
                        WalkthroughStep(
                            number = 4,
                            title = "Drop in the new one",
                            body = "Take the new coil out of the box. Push it straight down into the same hole until you feel it seat onto the spark plug. Bolt it back down (don't crank too hard — snug is fine). Reconnect the wire until you hear the click. Close the hood. Start the engine. The misfire should clear in a minute or two — I'll let you know.",
                            diagramHint = "Snug, not tight — and listen for the click",
                        ),
                    ),
                    mechanicDraft = """Hi — my 2009 Corolla (187k miles) has been showing a P0301 misfire on cylinder 1 for the last few drives. O2 sensor is reading rich and idle is rough.

Looks like it might be an ignition coil but I wanted a pro to confirm before I buy parts. Could you give me a rough estimate?

I'm trying to keep costs down — happy to bring it in when you have time.""",
                    tripReadiness = TripReadiness(
                        headline = "Safe for short trips",
                        caveat = "avoid highway until fixed",
                    ),
                    defaultHighlights = listOf(DiagramTarget.COIL_1),
                ),
            )
        )
    }
}
