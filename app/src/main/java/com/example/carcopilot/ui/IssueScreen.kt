package com.example.carcopilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.FALLBACK_GOOD_NEWS_MISFIRE
import com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE
import com.example.carcopilot.model.Issue
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.EvidenceSection
import com.example.carcopilot.ui.components.EvidenceToggle
import com.example.carcopilot.ui.components.IssueCardWithCTAs
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.theme.CarCopilotColors

@Composable
fun IssueScreen(
    issue: Issue,
    gemma: GemmaService,
    onBack: () -> Unit,
    onWalkthrough: () -> Unit = {},
    onMechanicDraft: () -> Unit = {},
) {
    var state by remember { mutableStateOf<SynthesisState>(SynthesisState.Thinking) }
    var evidenceOpen by remember { mutableStateOf(false) }

    LaunchedEffect(issue.id) {
        // Wait for engine init to settle (may already be done if user lingered on Home).
        gemma.awaitReady()
        if (gemma.initError != null) {
            state = SynthesisState.Ready(
                synthesis = FALLBACK_SYNTHESIS_MISFIRE,
                goodNews = FALLBACK_GOOD_NEWS_MISFIRE,
                isFallback = true,
            )
            return@LaunchedEffect
        }
        val buf = StringBuilder()
        try {
            gemma.streamSynthesis(issue).collect { delta ->
                buf.append(delta)
                val progress = extractSynthesisInProgress(buf.toString())
                if (progress.partial.isNotEmpty()) {
                    state = SynthesisState.Streaming(progress.partial)
                }
                // Else keep Thinking — the model is still emitting the JSON
                // envelope (`{"synthesis": "`) and there's nothing to show yet.
            }
            state = if (buf.isEmpty()) {
                SynthesisState.Ready(FALLBACK_SYNTHESIS_MISFIRE, FALLBACK_GOOD_NEWS_MISFIRE, isFallback = true)
            } else {
                parseOrFallback(buf.toString())
            }
        } catch (_: Throwable) {
            state = SynthesisState.Ready(
                synthesis = FALLBACK_SYNTHESIS_MISFIRE,
                goodNews = FALLBACK_GOOD_NEWS_MISFIRE,
                isFallback = true,
            )
        }
    }

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
            AnimatedAIStrip(
                state = state,
                label = "Here's what I'm seeing",
                severity = issue.severity,
            )
            IssueCardWithCTAs(
                issue = issue,
                primaryLabel = "Walk me through the fix →",
                ghostLabel = "Send this to a mechanic instead",
                onPrimary = onWalkthrough,
                onGhost = onMechanicDraft,
            )
            EvidenceToggle(open = evidenceOpen, onClick = { evidenceOpen = !evidenceOpen })
            AnimatedVisibility(
                visible = evidenceOpen,
                enter = expandVertically(animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) +
                    fadeOut(animationSpec = tween(300)),
            ) {
                EvidenceSection(dtcs = issue.dtcs, readings = issue.liveReadings)
            }
        }
        BottomTabBar(selected = Tab.Home)
    }
}
