package com.example.carcopilot.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.carcopilot.data.DTCTable
import com.example.carcopilot.data.DiagramTarget
import com.example.carcopilot.data.WalkthroughSpec
import com.example.carcopilot.data.highlightsForPlanStep
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.WalkthroughStep
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.EngineDiagram
import com.example.carcopilot.ui.components.SpecsChipRow
import com.example.carcopilot.ui.components.StepPill
import com.example.carcopilot.ui.components.StepProgress
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.components.WalkthroughLoadingPage
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

private const val GENERIC_STEP_FALLBACK =
    "Snug what you opened, reconnect what you unplugged, and start the engine."

private const val LOADING_CROSSFADE_MS = 300

/**
 * TEMP — UI iteration bypass.
 *
 * When `true`, the walkthrough screen skips the Gemma plan + per-step
 * generation entirely and uses the canned [Issue.walkthroughSteps] right
 * away. This lets us iterate on bullet rendering, [Y]-emphasis colors,
 * and step-card layout without waiting ~2 minutes for the model to
 * produce a five-step procedure each launch. Flip back to `false`
 * before committing — production must always go through Gemma so the
 * surface streams real, vehicle-specific content.
 */
private const val DEV_INSTANT_WALKTHROUGH = false

/**
 * Single-loading-event walkthrough.
 *
 * Lifecycle:
 *   1. Enter screen → the page is just the centered [WalkthroughLoadingPage]
 *      with scaled-up bouncing dots, "PREPARING YOUR WALKTHROUGH", and a
 *      quieter "GEMMA · ON-DEVICE" attribution. StepPill, StepProgress,
 *      diagram card, AI strip, and CTA are NOT rendered during this phase.
 *      The pipeline state machine still distinguishes BuildingPlan vs
 *      BuildingStep internally (and the labels are wired up so future builds
 *      can re-expose progress), but the unified page intentionally hides
 *      it — no "Building step 3 of 5" theatre.
 *   2. Plan + every step body finish streaming sequentially → pipeline
 *      transitions to Ready. A 300ms Crossfade swaps the loading page for
 *      the full walkthrough content (StepPill, StepProgress, AI strip with
 *      step 1 body, diagram with first highlight, CTA).
 *   3. Advancing the step index reads from the pre-built [RenderedStep]
 *      cache; no further inference. The CTA goes through every step ending
 *      at "Finish →".
 *
 * Fallback:
 *  - Plan parse fails / generation errors → fall back to the canned
 *    [Issue.walkthroughSteps] mapped into [PlanStep]s, short-circuit to
 *    Ready with every body filled from the canned baseline. Loading page
 *    barely appears before the transition.
 *  - Live plan + any individual step body failure → that one body falls
 *    back to its canned counterpart; rest of the pipeline continues.
 *
 * In line with [AnimatedAIStrip]'s long-standing "isFallback is intentionally
 * not surfaced" rule, fallback state lives on the data objects (for logs
 * and tests) but is not rendered as a visible badge.
 */
@Composable
fun WalkthroughScreen(
    issue: Issue,
    gemma: GemmaService,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onHomeTab: () -> Unit,
    onHistoryTab: () -> Unit,
) {
    val cannedSteps: List<WalkthroughStep> = issue.walkthroughSteps
    val planFallback: List<PlanStep> = remember(issue.id) {
        // The plan's `brief` doubles as the diagram caption beneath the
        // engine schematic, so it must not echo the per-step body (the AI
        // strip already shows that). The canned WalkthroughStep already
        // carries a `diagramHint` of exactly the right shape — terse,
        // single-line, written for the schematic — so use it. Fall back
        // to the step title only if a hint is missing.
        cannedSteps.map { PlanStep(
            number = it.number,
            title = it.title,
            brief = it.diagramHint ?: it.title,
        ) }
    }
    val defaultHighlights: List<DiagramTarget> = remember(issue.id) {
        val code = issue.dtcs.firstOrNull()?.code ?: return@remember emptyList()
        DTCTable.DEFAULT.lookup(code)?.defaultHighlights.orEmpty()
    }
    val procedureSpecs: List<WalkthroughSpec> = remember(issue.id) {
        val code = issue.dtcs.firstOrNull()?.code ?: return@remember emptyList()
        DTCTable.DEFAULT.lookup(code)?.procedureSpecs.orEmpty()
    }

    var pipelineState by remember(issue.id) {
        mutableStateOf<WalkthroughPipelineState>(WalkthroughPipelineState.BuildingPlan())
    }
    var stepIndex by remember(issue.id) { mutableIntStateOf(0) }

    LaunchedEffect(issue.id) {
        // TEMP — see DEV_INSTANT_WALKTHROUGH at the top of this file.
        if (DEV_INSTANT_WALKTHROUGH) {
            pipelineState = buildReadyFromFallback(planFallback, cannedSteps)
            return@LaunchedEffect
        }
        gemma.awaitReady()
        if (gemma.initError != null) {
            pipelineState = buildReadyFromFallback(planFallback, cannedSteps)
            return@LaunchedEffect
        }

        // Phase 1 — stream the plan envelope.
        val planBuf = StringBuilder()
        val parsedPlan: WalkthroughPlanState.Ready = try {
            gemma.streamWalkthroughPlan(issue).collect { delta ->
                planBuf.append(delta)
                val progress = extractWalkthroughPlanInProgress(planBuf.toString())
                pipelineState = WalkthroughPipelineState.BuildingPlan(progress.stepsSeen)
            }
            if (planBuf.isEmpty()) {
                WalkthroughPlanState.Ready(planFallback, isFallback = true)
            } else {
                parseWalkthroughPlanOrFallback(planBuf.toString(), planFallback)
            }
        } catch (_: Throwable) {
            WalkthroughPlanState.Ready(planFallback, isFallback = true)
        }

        if (parsedPlan.isFallback) {
            pipelineState = buildReadyFromFallback(parsedPlan.steps, cannedSteps)
            return@LaunchedEffect
        }

        // Phase 2 — stream each step body sequentially. The pipeline state
        // advances BuildingStep(idx, total) for each so labels stay consistent
        // (the loading page hides them; logs and any future variant can show
        // them again without re-plumbing state).
        val planSteps = parsedPlan.steps
        val totalSteps = planSteps.size
        val rendered = mutableListOf<RenderedStep>()
        for ((idx, planStep) in planSteps.withIndex()) {
            pipelineState = WalkthroughPipelineState.BuildingStep(plan = planSteps, currentIdx = idx)
            val cannedFallback = cannedSteps.getOrNull(idx)?.body ?: GENERIC_STEP_FALLBACK
            val stepBuf = StringBuilder()
            val parsed: WalkthroughStepState.Ready = try {
                gemma.streamWalkthroughStep(issue, planStep, totalSteps).collect { delta ->
                    stepBuf.append(delta)
                }
                if (stepBuf.isEmpty()) {
                    WalkthroughStepState.Ready(body = cannedFallback, isFallback = true)
                } else {
                    parseWalkthroughStepOrFallback(stepBuf.toString(), cannedFallback)
                }
            } catch (_: Throwable) {
                WalkthroughStepState.Ready(body = cannedFallback, isFallback = true)
            }
            rendered.add(
                RenderedStep(
                    planStep = planStep,
                    body = parsed.body,
                    isFallback = parsed.isFallback,
                )
            )
        }

        pipelineState = WalkthroughPipelineState.Ready(
            steps = rendered,
            anyFallback = rendered.any { it.isFallback },
        )
    }

    val ready = pipelineState as? WalkthroughPipelineState.Ready
    val isReady = ready != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarCopilotColors.PhoneBg),
    ) {
        TopBar(left = TopBarLeft.Back(onBack = onBack))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Crossfade(
                targetState = isReady,
                animationSpec = tween(durationMillis = LOADING_CROSSFADE_MS),
                label = "walkthrough-loading-to-content",
            ) { contentReady ->
                if (!contentReady || ready == null) {
                    WalkthroughLoadingPage(state = pipelineState)
                } else {
                    WalkthroughContent(
                        rendered = ready,
                        stepIndex = stepIndex,
                        defaultHighlights = defaultHighlights,
                        procedureSpecs = procedureSpecs,
                        severity = issue.severity,
                        onAdvance = { stepIndex += 1 },
                        onRetreat = { if (stepIndex > 0) stepIndex -= 1 },
                        onFinish = onFinish,
                    )
                }
            }
        }
        BottomTabBar(
            selected = Tab.Home,
            onSelect = { tab ->
                when (tab) {
                    Tab.Home -> onHomeTab()
                    Tab.History -> onHistoryTab()
                }
            },
        )
    }
}

@Composable
private fun WalkthroughContent(
    rendered: WalkthroughPipelineState.Ready,
    stepIndex: Int,
    defaultHighlights: List<DiagramTarget>,
    procedureSpecs: List<WalkthroughSpec>,
    severity: Severity,
    onAdvance: () -> Unit,
    onRetreat: () -> Unit,
    onFinish: () -> Unit,
) {
    val steps = rendered.steps
    val total = steps.size
    val activeRendered = steps.getOrNull(stepIndex) ?: return
    val activeHighlights = highlightsForPlanStep(activeRendered.planStep, defaultHighlights)
    val isFirst = stepIndex == 0
    val isLast = stepIndex == total - 1
    val nextLabel = if (isLast) "Finish →" else "Done — next step →"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
    ) {
        StepPill(current = stepIndex + 1, total = total)
        Spacer(Modifier.height(14.dp))
        StepProgress(current = stepIndex + 1, total = total)
        Spacer(Modifier.height(18.dp))
        AnimatedAIStrip(
            state = SynthesisState.Ready(
                synthesis = activeRendered.body,
                goodNews = null,
                isFallback = activeRendered.isFallback,
            ),
            label = activeRendered.planStep.title,
            severity = severity,
        )
        if (procedureSpecs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SpecsChipRow(specs = procedureSpecs)
        }
        Spacer(Modifier.height(14.dp))
        DiagramCard(
            caption = activeRendered.planStep.brief,
            highlights = activeHighlights,
        )
        Spacer(Modifier.height(18.dp))
        StepControls(
            backEnabled = !isFirst,
            nextLabel = nextLabel,
            onBack = onRetreat,
            onNext = { if (isLast) onFinish() else onAdvance() },
        )
    }
}

private fun buildReadyFromFallback(
    planSteps: List<PlanStep>,
    cannedSteps: List<WalkthroughStep>,
): WalkthroughPipelineState.Ready {
    val rendered = planSteps.mapIndexed { idx, planStep ->
        val body = cannedSteps.getOrNull(idx)?.body ?: GENERIC_STEP_FALLBACK
        RenderedStep(planStep = planStep, body = body, isFallback = true)
    }
    return WalkthroughPipelineState.Ready(steps = rendered, anyFallback = true)
}

@Composable
private fun DiagramCard(caption: String, highlights: List<DiagramTarget>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CarCopilotColors.SchematicSurface)
                .padding(18.dp),
        ) {
            EngineDiagram(highlights = highlights)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = caption,
            style = CarCopilotTypography.TabLabel,
            color = CarCopilotColors.TextMute,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Bottom-of-page step controls. Two side-by-side buttons:
 *
 *   ┌────────────┬────────────────────────┐
 *   │  ← Back    │  Done — next step →    │
 *   └────────────┴────────────────────────┘
 *
 * Back is a ghost button that retreats one step; disabled (40% alpha, no
 * pointer) when the user is on step 1 so the row keeps a stable width across
 * all steps — feedback asked for a Back button "next to" Next, not a
 * conditional render that would shuffle the Next button's position when the
 * user reaches step 2.
 *
 * Next keeps the original accent fill — it's still the primary action — but
 * loses the full-width treatment to share the row. Weighted 1.7 : 1 against
 * Back so the primary stays visually dominant.
 *
 * Canonical Compose patterns (carried over from the prior single-CtaButton
 * implementation): opacity baked into the background color rather than
 * applied via Modifier.alpha (which would allocate a graphics layer prone
 * to stale-redraw under the loading→content Crossfade above), and
 * `clickable(enabled = …)` rather than a conditional Modifier branch (which
 * would change the modifier chain identity on every enabled flip and force
 * a layout-node reattach). See the W2 commit-1 investigation for the bug
 * that motivated those choices.
 */
@Composable
private fun StepControls(
    backEnabled: Boolean,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackButton(
            enabled = backEnabled,
            onClick = onBack,
            modifier = Modifier.weight(1f),
        )
        NextButton(
            label = nextLabel,
            onClick = onNext,
            modifier = Modifier.weight(1.7f),
        )
    }
}

@Composable
private fun BackButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (enabled) CarCopilotColors.LineBright else CarCopilotColors.Line
    val labelColor =
        if (enabled) CarCopilotColors.MetaBold else CarCopilotColors.TextFaint
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "← Back",
            style = CarCopilotTypography.CtaButton,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarCopilotColors.Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CarCopilotTypography.CtaButton,
            color = CarCopilotColors.AccentDeep,
            textAlign = TextAlign.Center,
        )
    }
}
