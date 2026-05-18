package com.example.carcopilot.data

import com.example.carcopilot.model.IssueMeta
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.TripReadiness
import com.example.carcopilot.model.WalkthroughStep
import com.example.carcopilot.ui.components.schematic.EngineBaySchematic
import com.example.carcopilot.ui.components.schematic.FuelSystemSchematic
import com.example.carcopilot.ui.components.schematic.SchematicSpec

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
    val meta: IssueMeta = IssueMeta(),
    val walkthroughSteps: List<WalkthroughStep> = emptyList(),
    val mechanicDraft: String? = null,
    val tripReadiness: TripReadiness? = null,
    /**
     * Per-DTC schematic shown beneath the AI strip on the walkthrough screen.
     * Null means no diagram card — preferable to showing an unrelated one.
     * Adding a new DTC family with its own topology is a new
     * [SchematicSpec] data file, not a new composable.
     */
    val schematic: SchematicSpec? = null,
    /**
     * Schematic target IDs the walkthrough screen falls back to when a plan
     * step has no specific keyword match. IDs are resolved by
     * [SchematicSpec.regions] in the [schematic]; see [DiagramTargets] for
     * the constants and [highlightsForPlanStep] for the per-step override logic.
     */
    val defaultHighlights: List<String> = emptyList(),
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

    /** Returns a new table that includes [thin] entries, with this table's entries winning on conflict. */
    fun withThin(thin: Map<String, DTCEntry>): DTCTable = DTCTable(thin + entries)

    companion object {
        /** Deep entries only. Use [withThin] at runtime to add universal coverage. */
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
                            body = "- Pop the hood and find the [Y]four slim black blocks[/Y] sitting in a row on top of the engine.\n- Each one has a single wire connector — those are the ignition coils.\n- [Y]Cylinder 1[/Y] sits on the [Y]timing-chain side[/Y] — the passenger side of the car on this EA211.",
                            diagramHint = "Engine bay — cylinder 1 highlighted",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Disconnect coil 1",
                            body = "- Squeeze the plastic tab on the wire connector and pull [Y]straight up[/Y] — it releases with a soft click.\n- The EA211 coil is held by a [Y]spring clip[/Y], not a bolt — slide the clip sideways with a small flat screwdriver and it pops loose.\n- Drop the clip into a parts tray so it doesn't end up under the engine.",
                            diagramHint = "Connector + spring clip — both on top of the coil",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Lift out the old coil",
                            body = "- Grab the coil at its base and pull [Y]straight up, not sideways[/Y].\n- A wiggle is fine if it sticks — the coil seats onto the spark plug below.\n- It comes out with about 8 inches of [Y]thin black rubber boot[/Y] attached. That's normal.",
                            diagramHint = "Pull straight up — don't twist",
                        ),
                        WalkthroughStep(
                            number = 4,
                            title = "Drop in the new one",
                            body = "- Push the new coil [Y]straight down[/Y] until you feel it seat onto the spark plug.\n- Slide the [Y]spring clip[/Y] back into place — it snaps home when it's fully seated.\n- Reconnect the wire until you hear the click.\n- Close the hood and start the engine — the misfire should clear in a minute or two.",
                            diagramHint = "Slide the clip until it snaps — and listen for the wire click",
                        ),
                    ),
                    mechanicDraft = """Hi — my 2021 VW Jetta (42k miles, 1.4 TSI) has been showing a P0301 misfire on cylinder 1 for the last few drives. O2 sensor is reading rich and idle is rough.

Looks like it might be an ignition coil — these TSI coils are known to fail early — but I wanted a pro to confirm before I buy parts. Could you give me a rough estimate?

I'm trying to keep costs down — happy to bring it in when you have time.""",
                    tripReadiness = TripReadiness(
                        headline = "Safe for short trips",
                        caveat = "avoid highway until fixed",
                    ),
                    schematic = EngineBaySchematic,
                    defaultHighlights = listOf(DiagramTargets.COIL_1),
                    procedureSpecs = listOf(
                        WalkthroughSpec("Plug torque", "25 Nm (18 ft-lb)"),
                        WalkthroughSpec("Plug gap", "0.031\" (0.8mm)"),
                        WalkthroughSpec("Coil hold-down", "spring clip"),
                        WalkthroughSpec("Plug socket", "14mm thin-wall"),
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
                            body = "- Open the bonnet and look for a [Y]small metal canister[/Y] mounted near the injection pump.\n- Two fuel lines run into it — that's your fuel filter.\n- Some models also have a [Y]secondary water separator[/Y] further down the line. Check the owner's manual if you're unsure.",
                            diagramHint = "Fuel filter — near injection pump",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Let it cool and prep the area",
                            body = "- Let the engine cool for [Y]10 minutes[/Y] before touching anything — diesel is pressurised.\n- Wrap a [Y]rag[/Y] around each fitting before you loosen it.\n- Put a small container underneath to catch drips.",
                            diagramHint = "Rag over fuel line fittings before loosening",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Swap the filter",
                            body = "- Loosen the clips or banjo bolts on each fuel line.\n- Note which line is [Y]inlet[/Y] and which is [Y]outlet[/Y] — the new filter has a flow arrow.\n- Slide the new filter in, reconnect the lines, and [Y]snug the fittings[/Y].\n- Wipe up any spilled diesel.",
                            diagramHint = "Arrow on filter shows fuel flow direction",
                        ),
                        WalkthroughStep(
                            number = 4,
                            title = "Prime and start",
                            body = "- Pump the primer bulb (if your system has one) until it feels [Y]firm[/Y] — this clears air from the line.\n- Crank the engine. It may take a [Y]few extra seconds[/Y] to start while the system primes.\n- Let it idle and check the filter connections for drips.",
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
                    schematic = FuelSystemSchematic,
                    defaultHighlights = listOf(DiagramTargets.FUEL_FILTER),
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
                            body = "- Find the MAF sensor on the [Y]intake pipe[/Y] between the air filter box and the engine.\n- Disconnect the wire connector and spray [Y]MAF cleaner[/Y] inside — never touch the thin sensing wire.\n- Let it dry for [Y]10 minutes[/Y], reconnect, and start the engine.\n- If fuel trim drops back toward zero, you found it.",
                            diagramHint = "Intake pipe — MAF sensor between air filter box and throttle body",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Check the intake hose for cracks",
                            body = "- Run your hand along the [Y]large rubber hose[/Y] from the air filter box to the engine.\n- Feel for cracks, holes, or loose clamps — a crack lets unmetered air past the MAF and the mix goes lean.\n- Tighten any loose clamps with a [Y]flat screwdriver[/Y].",
                            diagramHint = "Rubber intake hose and clamps",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Listen for vacuum leaks",
                            body = "- With the engine running, listen for a [Y]hiss[/Y] near the intake manifold.\n- Small vacuum hoses connect the intake to sensors around the engine — a cracked or disconnected one leaks air past the MAF.\n- Use an unlit propane torch or a straw to trace the hiss. [R]Don't use water or flammable spray near a hot engine.[/R]",
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
                "P0118" to DTCEntry(
                    code = "P0118",
                    description = "Engine coolant temperature circuit high",
                    category = "cooling",
                    severity = Severity.severe,
                    route = Route.diy,
                    title = "Coolant reading dangerously hot",
                    subtitle = "Your sensor is reporting an extreme temperature. It could be a real overheat or a failing sensor — either way, stop the engine before driving further.",
                    meta = IssueMeta(
                        costUsdMin = 0,
                        costUsdMax = 35,
                        timeMinutes = 25,
                        difficulty = "easy",
                        drivability = "stop driving — engine may overheat",
                    ),
                    walkthroughSteps = listOf(
                        WalkthroughStep(
                            number = 1,
                            title = "Pull over and shut off",
                            body = "- Find a safe spot and [R]stop the engine[/R] right away — driving a hot engine warps the head.\n- Pop the hood to let heat escape, but [Y]don't touch anything yet[/Y] — surfaces near the radiator hit 200°F.\n- Set a [Y]20-minute timer[/Y] before you go further.",
                            diagramHint = "Stop, hood up, hands off",
                        ),
                        WalkthroughStep(
                            number = 2,
                            title = "Check the coolant reservoir",
                            body = "- Once it's cool, find the [Y]plastic coolant reservoir[/Y] — usually a translucent tank near the radiator with MIN / MAX marks.\n- Look at the level against the marks. Low or empty points to a leak.\n- Don't open the radiator cap yet — even cool engines can hold residual pressure.",
                            diagramHint = "Reservoir tank — check MIN / MAX",
                        ),
                        WalkthroughStep(
                            number = 3,
                            title = "Top up and restart",
                            body = "- Top up the reservoir with [Y]50/50 coolant mix[/Y] (or distilled water in a pinch).\n- Start the engine and watch the temperature gauge for [Y]two minutes[/Y].\n- If it climbs back into the red, [R]shut it off and call a tow[/R] — you're looking at a head-gasket or water-pump issue, not something to drive on.",
                            diagramHint = "Watch the gauge — shut off if it climbs",
                        ),
                    ),
                    mechanicDraft = """Hi — my 2009 Corolla (187k miles) threw a P0118 (coolant temp sensor circuit high). The sensor is reading well above the normal range at idle.

I let it cool, checked the reservoir, and topped up the coolant. The code is still active. Could it be the sensor itself or am I looking at a real cooling-system issue (water pump, thermostat, head gasket)?

Happy to bring it in when you have time.""",
                    tripReadiness = TripReadiness(
                        headline = "Don't drive until you've checked the coolant",
                        caveat = "engine may overheat — pull over if the gauge climbs",
                    ),
                ),
            )
        )
    }
}
