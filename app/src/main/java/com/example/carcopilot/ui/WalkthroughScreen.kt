package com.example.carcopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.carcopilot.data.DTCTable
import com.example.carcopilot.data.DiagramTarget
import com.example.carcopilot.data.highlightsForPlanStep
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.WalkthroughStep
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.EngineDiagram
import com.example.carcopilot.ui.components.StepPill
import com.example.carcopilot.ui.components.StepProgress
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Plan-then-prefetch walkthrough.
 *
 * Lifecycle:
 *   1. Enter screen → stream the plan envelope. While streaming, plan state
 *      counts complete step objects so the loader shows "Building plan…".
 *   2. Plan ready → kick off step 0's body generation; immediately prefetch
 *      step 1 (queues behind step 0 on GemmaService's convoMutex).
 *   3. User taps "next step" → snapshotFlow on stepIndex fires; ensure
 *      stepIndex is generating (no-op if already cached) and prefetch
 *      stepIndex+1.
 *   4. Back-nav disposes the LaunchedEffect; all child jobs cancel.
 *
 * In-session cache: [stepStates] survives recomposition while the screen
 * is on-stack. Each entry transitions monotonically Thinking → Streaming →
 * Ready and stays Ready; advancing back and forth between steps does not
 * re-stream.
 *
 * Fallback:
 *  - Plan parse fails or generation errors → fall back to the canned
 *    [Issue.walkthroughSteps] mapped into [PlanStep]s. Plan-level fallback
 *    short-circuits per-step Gemma calls; step bodies pull directly from the
 *    canned [WalkthroughStep.body] so we never mix live-generated bodies on
 *    top of a fallback plan.
 *  - Step parse fails or generation errors → fall back to the canned body
 *    at the same index, or a generic line if the canned list is shorter than
 *    the live plan.
 *
 * In line with [AnimatedAIStrip]'s long-standing "isFallback is intentionally
 * not surfaced" rule, fallback state is carried on the state objects (for
 * tests and logs) but not rendered as a visible badge.
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
        cannedSteps.map { PlanStep(number = it.number, title = it.title, brief = it.body) }
    }
    val defaultHighlights: List<DiagramTarget> = remember(issue.id) {
        val code = issue.dtcs.firstOrNull()?.code ?: return@remember emptyList()
        DTCTable.DEFAULT.lookup(code)?.defaultHighlights.orEmpty()
    }

    var planState by remember(issue.id) {
        mutableStateOf<WalkthroughPlanState>(WalkthroughPlanState.Thinking)
    }
    val stepStates = remember(issue.id) {
        mutableStateMapOf<Int, WalkthroughStepState>()
    }
    var stepIndex by remember(issue.id) { mutableIntStateOf(0) }

    LaunchedEffect(issue.id) {
        gemma.awaitReady()
        if (gemma.initError != null) {
            planState = WalkthroughPlanState.Ready(planFallback, isFallback = true)
        } else {
            val buf = StringBuilder()
            try {
                gemma.streamWalkthroughPlan(issue).collect { delta ->
                    buf.append(delta)
                    val progress = extractWalkthroughPlanInProgress(buf.toString())
                    planState = WalkthroughPlanState.Streaming(progress.stepsSeen)
                }
                planState = if (buf.isEmpty()) {
                    WalkthroughPlanState.Ready(planFallback, isFallback = true)
                } else {
                    parseWalkthroughPlanOrFallback(buf.toString(), planFallback)
                }
            } catch (_: Throwable) {
                planState = WalkthroughPlanState.Ready(planFallback, isFallback = true)
            }
        }

        val ready = planState as? WalkthroughPlanState.Ready ?: return@LaunchedEffect
        val planSteps = ready.steps
        val planIsFallback = ready.isFallback
        val totalSteps = planSteps.size
        val launched = mutableMapOf<Int, Job>()

        fun launchStep(idx: Int) {
            if (idx < 0 || idx >= totalSteps) return
            if (launched.containsKey(idx)) return
            if (stepStates[idx] is WalkthroughStepState.Ready) return
            stepStates[idx] = WalkthroughStepState.Thinking
            launched[idx] = launch {
                runStepGeneration(
                    idx = idx,
                    planSteps = planSteps,
                    totalSteps = totalSteps,
                    planIsFallback = planIsFallback,
                    cannedSteps = cannedSteps,
                    issue = issue,
                    gemma = gemma,
                    write = { state -> stepStates[idx] = state },
                )
            }
        }

        snapshotFlow { stepIndex }.collect { idx ->
            launchStep(idx)
            launchStep(idx + 1)
        }
    }

    val total = (planState as? WalkthroughPlanState.Ready)?.steps?.size ?: cannedSteps.size
    val planReady = planState as? WalkthroughPlanState.Ready
    val activePlanStep = planReady?.steps?.getOrNull(stepIndex)
    val activeStepState: WalkthroughStepState =
        stepStates[stepIndex] ?: WalkthroughStepState.Thinking
    val ctaEnabled = activeStepState is WalkthroughStepState.Ready
    val ctaLabel = if (stepIndex == total - 1) "Finish →" else "Done — next step →"
    val activeHighlights: List<DiagramTarget> = activePlanStep
        ?.let { highlightsForPlanStep(it, defaultHighlights) }
        ?: defaultHighlights

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarCopilotColors.PhoneBg),
    ) {
        TopBar(left = TopBarLeft.Back(onBack = onBack))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
        ) {
            if (planReady != null) {
                StepPill(current = stepIndex + 1, total = total)
                Spacer(Modifier.height(14.dp))
                StepProgress(current = stepIndex + 1, total = total)
                Spacer(Modifier.height(18.dp))
            }
            AnimatedAIStrip(
                state = stripStateFor(planState, activeStepState),
                label = stripLabelFor(planState, activePlanStep),
                severity = issue.severity,
            )
            DiagramCard(
                caption = diagramCaptionFor(planState, activePlanStep),
                highlights = activeHighlights,
            )
            Spacer(Modifier.height(18.dp))
            CtaButton(
                label = ctaLabel,
                enabled = ctaEnabled,
                onClick = {
                    if (stepIndex == total - 1) onFinish() else stepIndex += 1
                },
            )
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

private suspend fun runStepGeneration(
    idx: Int,
    planSteps: List<PlanStep>,
    totalSteps: Int,
    planIsFallback: Boolean,
    cannedSteps: List<WalkthroughStep>,
    issue: Issue,
    gemma: GemmaService,
    write: (WalkthroughStepState) -> Unit,
) {
    val cannedFallback = cannedSteps.getOrNull(idx)?.body
        ?: "Snug what you opened, reconnect what you unplugged, and start the engine."
    if (planIsFallback) {
        // Plan itself fell back; don't mix live bodies on top of canned plan steps.
        write(WalkthroughStepState.Ready(body = cannedFallback, isFallback = true))
        return
    }
    val planStep = planSteps[idx]
    val buf = StringBuilder()
    try {
        gemma.streamWalkthroughStep(issue, planStep, totalSteps).collect { delta ->
            buf.append(delta)
            val progress = extractWalkthroughStepInProgress(buf.toString())
            if (progress.partial.isNotEmpty()) {
                write(WalkthroughStepState.Streaming(progress.partial))
            }
        }
        write(
            if (buf.isEmpty()) {
                WalkthroughStepState.Ready(body = cannedFallback, isFallback = true)
            } else {
                parseWalkthroughStepOrFallback(buf.toString(), cannedFallback)
            }
        )
    } catch (_: Throwable) {
        write(WalkthroughStepState.Ready(body = cannedFallback, isFallback = true))
    }
}

private fun stripStateFor(
    planState: WalkthroughPlanState,
    activeStepState: WalkthroughStepState,
): SynthesisState = when (planState) {
    WalkthroughPlanState.Thinking, is WalkthroughPlanState.Streaming -> SynthesisState.Thinking
    is WalkthroughPlanState.Ready -> when (activeStepState) {
        WalkthroughStepState.Thinking -> SynthesisState.Thinking
        is WalkthroughStepState.Streaming -> SynthesisState.Streaming(activeStepState.partial)
        is WalkthroughStepState.Ready -> SynthesisState.Ready(
            synthesis = activeStepState.body,
            goodNews = null,
            isFallback = activeStepState.isFallback,
        )
    }
}

private fun stripLabelFor(planState: WalkthroughPlanState, activePlanStep: PlanStep?): String =
    when (planState) {
        WalkthroughPlanState.Thinking -> "Building your plan"
        is WalkthroughPlanState.Streaming -> {
            val n = planState.stepsSeen
            if (n == 0) "Building your plan" else "Building your plan — $n step${if (n == 1) "" else "s"} so far"
        }
        is WalkthroughPlanState.Ready -> activePlanStep?.title ?: "Working through it"
    }

private fun diagramCaptionFor(planState: WalkthroughPlanState, activePlanStep: PlanStep?): String =
    when (planState) {
        is WalkthroughPlanState.Ready -> activePlanStep?.brief.orEmpty()
        else -> ""
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
                .background(Color(0xFF050505))
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

@Composable
private fun CtaButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(CarCopilotColors.Accent)
        .alpha(if (enabled) 1f else 0.4f)
    Box(
        modifier = (if (enabled) base.clickable(onClick = onClick) else base)
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
