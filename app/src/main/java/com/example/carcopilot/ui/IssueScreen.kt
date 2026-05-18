package com.example.carcopilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.Classification
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.synthesizeFromClassification
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.EvidenceSection
import com.example.carcopilot.ui.components.EvidenceToggle
import com.example.carcopilot.ui.components.IssueCardWithCTAs
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.components.accentColor
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun IssueScreen(
    issue: Issue,
    gemma: GemmaService,
    classification: Classification? = null,
    onBack: () -> Unit,
    onWalkthrough: () -> Unit = {},
    onMechanicDraft: () -> Unit = {},
    onHistoryTab: () -> Unit = {},
) {
    var state by remember { mutableStateOf<SynthesisState>(SynthesisState.Thinking) }
    var evidenceOpen by remember { mutableStateOf(false) }

    LaunchedEffect(issue.id) {
        // Wait for engine init to settle (may already be done if user lingered on Home).
        gemma.awaitReady()
        val (fallbackSynthesis, fallbackGoodNews) = synthesizeFromClassification(classification, issue)
        if (gemma.initError != null) {
            state = SynthesisState.Ready(
                synthesis = fallbackSynthesis,
                goodNews = fallbackGoodNews,
                isFallback = true,
            )
            return@LaunchedEffect
        }
        try {
            typewriterCollect(
                source = gemma.streamSynthesis(issue, classification),
                extractDisplay = { raw -> extractSynthesisInProgress(raw).partial },
                onStreaming = { displayed -> state = SynthesisState.Streaming(displayed) },
                onDone = { raw ->
                    state = if (raw.isEmpty()) {
                        SynthesisState.Ready(
                            fallbackSynthesis,
                            fallbackGoodNews,
                            isFallback = true,
                        )
                    } else {
                        parseOrFallback(raw)
                    }
                },
            )
        } catch (_: Throwable) {
            state = SynthesisState.Ready(
                synthesis = fallbackSynthesis,
                goodNews = fallbackGoodNews,
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
            issue.meta.drivability?.let { drivability ->
                DrivabilityStrip(drivability = drivability, severity = issue.severity)
                Spacer(Modifier.height(14.dp))
            }
            IssueCardWithCTAs(
                issue = issue,
                primaryLabel = "Walk me through the fix →",
                ghostLabel = "Send this to a mechanic instead",
                onPrimary = onWalkthrough,
                onGhost = onMechanicDraft,
            )
            Spacer(Modifier.height(24.dp))
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
        BottomTabBar(
            selected = Tab.Home,
            onSelect = { tab ->
                when (tab) {
                    Tab.Home -> Unit
                    Tab.History -> onHistoryTab()
                }
            },
        )
    }
}

/**
 * Drivability verdict promoted out of the issue-card meta line into a strip
 * directly under the AI synthesis. "Safe for short trips" is the single
 * answer the driver most needs once they've read the explanation, so it
 * earns its own row instead of sharing weight with cost and time fragments.
 * Severity-tinted dot keeps the color language consistent with the AI
 * strip and the card's accent bar.
 */
@Composable
private fun DrivabilityStrip(drivability: String, severity: Severity) {
    val accent = severity.accentColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarCopilotColors.PhoneCardSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "DRIVABILITY",
            style = CarCopilotTypography.SectionLabel,
            color = CarCopilotColors.TextMute,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(CarCopilotColors.TextFaint),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = drivability.replaceFirstChar { it.uppercaseChar() },
            style = CarCopilotTypography.CardSubtitle,
            color = CarCopilotColors.TitleBright,
        )
    }
}
