package com.example.carcopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.carcopilot.model.Issue
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.EngineDiagram
import com.example.carcopilot.ui.components.StepPill
import com.example.carcopilot.ui.components.StepProgress
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun WalkthroughScreen(
    issue: Issue,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onHomeTab: () -> Unit,
    onHistoryTab: () -> Unit,
) {
    val steps = issue.walkthroughSteps
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps[stepIndex]
    val total = steps.size

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
            StepPill(current = step.number, total = total)
            Spacer(Modifier.height(14.dp))
            StepProgress(current = step.number, total = total)
            Spacer(Modifier.height(18.dp))
            AnimatedAIStrip(
                state = SynthesisState.Ready(
                    synthesis = step.body,
                    goodNews = null,
                    isFallback = false,
                ),
                label = step.title,
                severity = issue.severity,
            )
            DiagramCard(caption = step.diagramHint ?: "")
            Spacer(Modifier.height(18.dp))
            CtaButton(
                label = if (stepIndex == total - 1) "Finish →" else "Done — next step →",
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

@Composable
private fun DiagramCard(caption: String) {
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
            EngineDiagram()
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
private fun CtaButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
