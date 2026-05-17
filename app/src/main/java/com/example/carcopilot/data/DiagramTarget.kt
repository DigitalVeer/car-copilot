package com.example.carcopilot.data

import com.example.carcopilot.ui.PlanStep

/**
 * Per-step highlight targets for the engine-bay diagram. Demo-scoped to the
 * P0301 misfire procedure — every value here renders distinctly on the
 * existing [com.example.carcopilot.ui.components.EngineDiagram]. New DTCs
 * that need new targets are a one-line enum extension plus the geometry
 * change to draw them.
 *
 * COIL_1..COIL_4 and SPARK_PLUG share a geometric location in the bay
 * (the plug sits inside the coil well), so the diagram renders them at
 * different scales — coil = outer box + arrow, plug = inner dot inside the
 * cylinder 1 well — so the user sees movement on a Phase 2 → Phase 3
 * transition rather than the highlight standing still.
 */
enum class DiagramTarget {
    COIL_1,
    COIL_2,
    COIL_3,
    COIL_4,
    SPARK_PLUG,
    BATTERY_NEG,
}

/**
 * Map a plan step to the diagram targets it should highlight. Deterministic
 * keyword scan over `title + brief` — no Gemma decision involved. If nothing
 * matches, falls back to [defaults] (typically the DTCEntry's
 * defaultHighlights) so the diagram is never blank.
 *
 * The keyword order matters: "battery" wins over "coil" because the prep
 * phase often mentions disconnecting the battery near coil work, and the
 * battery is the more specific action.
 *
 * Note on "spark" vs "plug": we key on "spark" specifically because a step
 * about "unplugging the connector" should not be misread as a spark-plug
 * step. Coil-removal steps say "connector," not "plug."
 */
fun highlightsForPlanStep(
    planStep: PlanStep,
    defaults: List<DiagramTarget>,
): List<DiagramTarget> {
    val haystack = (planStep.title + " " + planStep.brief).lowercase()
    return when {
        "battery" in haystack -> listOf(DiagramTarget.BATTERY_NEG)
        "spark" in haystack -> listOf(DiagramTarget.SPARK_PLUG)
        "coil" in haystack -> listOf(DiagramTarget.COIL_1)
        else -> defaults
    }
}
