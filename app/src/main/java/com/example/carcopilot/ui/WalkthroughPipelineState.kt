package com.example.carcopilot.ui

/**
 * Composition state for the walkthrough screen's plan-then-bodies pipeline.
 * Replaces the W1 design where plan and per-step states were tracked
 * independently (WalkthroughPlanState + Map<Int, WalkthroughStepState>) and
 * the user could advance into a step whose body was still streaming.
 *
 * The pipeline now has a single loading phase that covers plan generation
 * plus every step body. The user enters the interactive walkthrough only
 * after the state transitions to [Ready], at which point every body is
 * already in the cache and step navigation is instant.
 *
 * Underlying envelope extractors and parsers (WalkthroughPlanState.kt,
 * WalkthroughStepState.kt) are unchanged — those still own the JSON
 * scanning. This sealed interface is purely about what the AI strip and
 * CTAs render on the Walkthrough screen.
 */
sealed interface WalkthroughPipelineState {
    /**
     * Plan envelope is in flight. [stepsSeen] is the count of complete step
     * objects the extractor has already closed inside `"steps":[…]`. The
     * loader label uses this to show "Building your plan — 3 steps so far"
     * once any have landed.
     */
    data class BuildingPlan(val stepsSeen: Int = 0) : WalkthroughPipelineState

    /**
     * Plan is parsed; currently streaming the body for plan step at
     * [currentIdx] (zero-indexed) inside [plan]. The skeleton loader uses
     * the full plan to render each row with its real title — the active row
     * pulses, completed rows show ✓, queued rows wait — so the user can see
     * the procedure shaping up before the bodies finish streaming. [total]
     * is derived from `plan.size` for callers that just need a count.
     */
    data class BuildingStep(
        val plan: List<PlanStep>,
        val currentIdx: Int,
    ) : WalkthroughPipelineState {
        val total: Int get() = plan.size
    }

    /**
     * All step bodies are cached and the walkthrough is interactive. Advancing
     * the step index reads from [steps] with no further inference. [anyFallback]
     * is true if any step's body came from the canned baseline rather than
     * live generation; the screen surfaces this only via logs today (matches
     * AnimatedAIStrip's long-standing convention).
     */
    data class Ready(
        val steps: List<RenderedStep>,
        val anyFallback: Boolean,
    ) : WalkthroughPipelineState
}

/**
 * One step's view-ready content. [body] is already JSON-extracted (passes
 * through parseWalkthroughStepOrFallback in the orchestrator) so the screen
 * can render it directly. [planStep] is preserved because the diagram-target
 * keyword scan and the AI-strip label both key off the plan step's title +
 * brief, not the generated body.
 */
data class RenderedStep(
    val planStep: PlanStep,
    val body: String,
    val isFallback: Boolean,
)
