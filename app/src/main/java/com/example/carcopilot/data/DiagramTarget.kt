package com.example.carcopilot.data

import com.example.carcopilot.ui.PlanStep

/**
 * Per-step highlight target IDs for walkthrough schematics.
 *
 * String IDs (rather than an enum) so new DTC families can introduce their
 * own targets without churning a shared enum. The schematic spec
 * (`SchematicSpec.regions`) decides which IDs are renderable in its own
 * topology; an unknown ID simply renders nothing.
 *
 * Naming convention: SCREAMING_SNAKE for the constants here, matching
 * the legacy enum values so existing data (`P0301: defaultHighlights =
 * listOf(DiagramTargets.COIL_1)`) reads the same.
 */
object DiagramTargets {
    // Engine-bay schematic (P0301 family)
    const val COIL_1 = "COIL_1"
    const val COIL_2 = "COIL_2"
    const val COIL_3 = "COIL_3"
    const val COIL_4 = "COIL_4"
    const val SPARK_PLUG = "SPARK_PLUG"
    const val BATTERY_NEG = "BATTERY_NEG"

    // Fuel-system schematic (P0087 family)
    const val FUEL_FILTER = "FUEL_FILTER"
    const val PRIMER_BULB = "PRIMER_BULB"
    const val FUEL_RAIL = "FUEL_RAIL"
}

/**
 * Map a plan step to the diagram target IDs it should highlight. Deterministic
 * keyword scan over `title + brief` — no Gemma decision involved. If nothing
 * matches, falls back to [defaults] (typically the DTCEntry's
 * defaultHighlights) so the diagram is never blank.
 *
 * Keyword priority matters. "battery" wins over "coil" because the prep phase
 * often mentions disconnecting the battery near coil work, and the battery is
 * the more specific action. "filter" wins over "rail" because Phase 3 of the
 * P0087 procedure says "swap the filter" with a passing mention of the rail.
 *
 * Note on "spark" vs "plug": we key on "spark" specifically because a step
 * about "unplugging the connector" should not be misread as a spark-plug
 * step. Coil-removal steps say "connector," not "plug."
 */
fun highlightsForPlanStep(
    planStep: PlanStep,
    defaults: List<String>,
): List<String> {
    val haystack = (planStep.title + " " + planStep.brief).lowercase()
    return when {
        "battery" in haystack -> listOf(DiagramTargets.BATTERY_NEG)
        "spark" in haystack -> listOf(DiagramTargets.SPARK_PLUG)
        "coil" in haystack -> listOf(DiagramTargets.COIL_1)
        "filter" in haystack -> listOf(DiagramTargets.FUEL_FILTER)
        "prime" in haystack -> listOf(DiagramTargets.PRIMER_BULB)
        "rail" in haystack || "pressure" in haystack -> listOf(DiagramTargets.FUEL_RAIL)
        else -> defaults
    }
}
