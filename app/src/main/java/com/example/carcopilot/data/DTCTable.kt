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
    /**
     * Canonical specs lifted from the curated procedure file. Rendered as
     * an always-visible chip row on the walkthrough screen so the user has
     * authoritative torque, gap, time, pressure, and tool-size values
     * regardless of where Gemma drifts in the generated step body. See
     * [WalkthroughSpec] for the W2.1 film-around motivation.
     */
    val procedureSpecs: List<WalkthroughSpec> = emptyList(),
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
                    procedureSpecs = listOf(
                        WalkthroughSpec("Plug torque", "18 Nm (13 ft-lb)"),
                        WalkthroughSpec("Plug gap", "0.043\""),
                        WalkthroughSpec("Coil bolt", "10mm"),
                        WalkthroughSpec("Plug socket", "5/8\" (16mm)"),
                        WalkthroughSpec("Test drive", "5–10 min mixed"),
                        WalkthroughSpec("Idle relearn", "30 sec"),
                    ),
                ),
                "P0087" to DTCEntry(
                    code = "P0087",
                    description = "Fuel rail pressure too low",
                    category = "fuel_system",
                    severity = Severity.warning,
                    route = Route.diy,
                    title = "Replace fuel filter — diesel pressure too low",
                    subtitle = "Your diesel isn't building enough pressure. A clogged fuel filter is the right first thing to check — it's cheap and quick.",
                    meta = IssueMeta(
                        costUsdMin = 5,
                        costUsdMax = 20,
                        timeMinutes = 45,
                        difficulty = "easy",
                        drivability = "reduced power — avoid highway or heavy loads",
                    ),
                    walkthroughSteps = listOf(
                        WalkthroughStep(
                            number = 1,
                            title = "Find the fuel filter",
                            body = "Open the bonnet. The fuel filter on most diesel Hilux and similar trucks is a small metal canister mounted near the injection pump, with two fuel lines running into it. Check your owner's manual if you're not sure. Some models have a secondary water separator further along the line.",
                            diagramHint = "Fuel filter — near injection pump",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Let it cool and prep the area",
                            body = "Let the engine cool for 10 minutes before touching anything. Diesel is pressurised — opening a line on a hot engine can spray fuel. Wrap a rag around each fitting before you loosen it, and put a small container underneath to catch any drips.",
                            diagramHint = "Rag over fuel line fittings before loosening",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Swap the filter",
                            body = "Loosen the clips or banjo bolts holding the fuel lines to the old filter. Note which line is inlet and which is outlet — the new filter is usually marked with a flow arrow. Slide the new filter in, reconnect the lines, and snug the fittings. Wipe up any spilled diesel.",
                            diagramHint = "Arrow on filter shows fuel flow direction",
                        ),
                        WalkthroughStep(
                            number = 4,
                            title = "Prime and start",
                            body = "If your system has a manual primer bulb, pump it until it feels firm — this pushes fuel through the new filter and clears any air from the line. Then crank the engine. It may take a few extra seconds to start while the system primes. Let it idle and check the filter connections for drips.",
                            diagramHint = "Primer bulb — pump until firm before cranking",
                        ),
                    ),
                    mechanicDraft = """Hi — my diesel is throwing a P0087 (fuel rail pressure too low). Rail pressure is reading around 28,000 kPa at idle when it should be closer to 34,500 kPa minimum.

I've already replaced the fuel filter as a first step. The pressure is still low. Could you check the fuel system — specifically whether the SCV on the high-pressure pump might be at fault?

Happy to bring it in at your convenience.""",
                    tripReadiness = TripReadiness(
                        headline = "Reduced power — drive carefully",
                        caveat = "avoid highway or heavy loads until fixed",
                    ),
                    procedureSpecs = listOf(
                        WalkthroughSpec("Banjo torque", "30 Nm (22 ft-lb)"),
                        WalkthroughSpec("Banjo wrench", "14mm / 17mm flare-nut"),
                        WalkthroughSpec("Primer pumps", "20–30 until firm"),
                        WalkthroughSpec("Key-on prime", "10–15 sec"),
                        WalkthroughSpec("Cool wait", "10 min before opening"),
                        WalkthroughSpec("Healthy idle pressure", "34,500 kPa"),
                        WalkthroughSpec("Low threshold", "≤ 28,000 kPa"),
                        WalkthroughSpec("Test drive", "5–10 min moderate load"),
                    ),
                ),
                "P0171" to DTCEntry(
                    code = "P0171",
                    description = "System too lean (Bank 1)",
                    category = "fuel_system",
                    severity = Severity.warning,
                    route = Route.diy,
                    title = "Engine running lean — MAF sensor or air leak",
                    subtitle = "Your engine isn't getting enough fuel in the mix. Usually a dirty air flow sensor or a crack in the intake hose.",
                    meta = IssueMeta(
                        costUsdMin = 15,
                        costUsdMax = 180,
                        timeMinutes = 30,
                        difficulty = "easy",
                        drivability = "safe for short trips, avoid extended highway driving",
                    ),
                    walkthroughSteps = listOf(
                        WalkthroughStep(
                            number = 1,
                            title = "Clean the MAF sensor",
                            body = "The MAF sensor sits on the air intake pipe between the air filter box and the engine. Disconnect the wire connector, then spray MAF cleaner inside — don't touch the thin sensing wire. Let it dry for 10 minutes, reconnect, and start the engine. If the trim readings drop back toward zero, you found it.",
                            diagramHint = "Intake pipe — MAF sensor between air filter box and throttle body",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Check the intake hose for cracks",
                            body = "Run your hand along the large rubber hose from the air filter box to the engine. Feel for cracks, holes, or loose clamps. A crack lets in air that bypasses the MAF sensor — the ECU doesn't account for it, so the mix goes lean. Tighten any loose clamps with a flat screwdriver.",
                            diagramHint = "Rubber intake hose and clamps",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Listen for vacuum leaks",
                            body = "With the engine running, listen for a hissing sound near the intake manifold. Small rubber vacuum hoses connect the intake to sensors around the engine — any cracked or disconnected one leaks air past the MAF. You can use a unlit propane torch or a straw to trace the hiss. Don't use water or flammable spray near a hot engine.",
                            diagramHint = "Intake manifold vacuum lines",
                        ),
                    ),
                    mechanicDraft = """Hi — my car has a P0171 fault code (System Too Lean, Bank 1). The long-term fuel trim is at +18%, which means the ECU has been adding extra fuel for a while to compensate.

I've sprayed the MAF sensor and checked the intake hose but the code keeps coming back. Could you check fuel pressure and look for any vacuum leaks I might have missed?

Happy to bring it in at your convenience.""",
                    tripReadiness = TripReadiness(
                        headline = "Safe for short trips",
                        caveat = "avoid extended highway driving until fixed",
                    ),
                ),
            )
        )
    }
}
