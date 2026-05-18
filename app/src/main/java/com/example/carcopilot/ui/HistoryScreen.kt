package com.example.carcopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.History
import com.example.carcopilot.model.HistoryEntry
import com.example.carcopilot.model.HistoryPill
import com.example.carcopilot.model.HistoryStats
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography
import com.example.carcopilot.ui.theme.JetBrainsMono

@Composable
fun HistoryScreen(
    gemma: GemmaService,
    currentIssue: Issue?,
    onHomeTab: () -> Unit,
    onBack: () -> Unit = onHomeTab,
) {
    var state by remember { mutableStateOf<HistoryPatternState>(HistoryPatternState.Thinking) }

    LaunchedEffect(Unit) {
        gemma.awaitReady()
        if (gemma.initError != null) {
            state = HistoryPatternState.Ready(body = History.PATTERN.body, isFallback = true)
            return@LaunchedEffect
        }
        try {
            typewriterCollect(
                source = gemma.streamHistoryPattern(History.ENTRIES, currentIssue),
                extractDisplay = { raw -> extractHistoryPatternInProgress(raw).partial },
                onStreaming = { displayed -> state = HistoryPatternState.Streaming(displayed) },
                onDone = { raw ->
                    state = if (raw.isEmpty()) {
                        HistoryPatternState.Ready(body = History.PATTERN.body, isFallback = true)
                    } else {
                        parseHistoryPatternOrFallback(raw, History.PATTERN.body)
                    }
                },
            )
        } catch (_: Throwable) {
            state = HistoryPatternState.Ready(body = History.PATTERN.body, isFallback = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarCopilotColors.PhoneBg),
    ) {
        TopBar(left = TopBarLeft.Brand(vehicleSubtitle = "History · 2009 Corolla"))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
        ) {
            AnimatedAIStrip(
                state = state.toSynthesisState(),
                label = History.PATTERN.label,
                severity = Severity.warning,
            )
            StatRow(History.STATS)
            Spacer(Modifier.height(22.dp))
            History.ENTRIES.groupBy { it.month }.entries.forEachIndexed { groupIdx, (month, entries) ->
                MonthHeading(month, isFirst = groupIdx == 0)
                Spacer(Modifier.height(10.dp))
                entries.forEachIndexed { idx, entry ->
                    HistoryRow(entry, isLast = idx == entries.lastIndex)
                }
            }
        }
        BottomTabBar(
            selected = Tab.History,
            onSelect = { tab ->
                when (tab) {
                    Tab.Home -> onHomeTab()
                    Tab.History -> Unit
                }
            },
        )
    }
}

/** Adapt the history-pattern state to the SynthesisState the AI strip renders. */
private fun HistoryPatternState.toSynthesisState(): SynthesisState = when (this) {
    HistoryPatternState.Thinking -> SynthesisState.Thinking
    is HistoryPatternState.Streaming -> SynthesisState.Streaming(partial)
    is HistoryPatternState.Ready -> SynthesisState.Ready(
        synthesis = body,
        goodNews = null,
        isFallback = isFallback,
    )
}

@Composable
private fun StatRow(stats: HistoryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(value = stats.issuesResolved.toString(), label = "ISSUES\nRESOLVED")
        StatCard(value = stats.coilFailures.toString(), label = "COIL\nFAILURES", accent = true)
        StatCard(value = "$${stats.spentThisYearUsd}", label = "SPENT\nTHIS YEAR")
    }
}

@Composable
private fun RowScope.StatCard(value: String, label: String, accent: Boolean = false) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = value,
            style = CarCopilotTypography.EvidenceRowValue.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.2.sp,
            ),
            color = if (accent) CarCopilotColors.Accent else CarCopilotColors.Text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = CarCopilotTypography.EvidenceSectionHeading.copy(letterSpacing = 0.1.em),
            color = CarCopilotColors.TextMute,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun MonthHeading(month: String, isFirst: Boolean = false) {
    Text(
        text = month.uppercase(),
        style = CarCopilotTypography.EvidenceSectionHeading.copy(letterSpacing = 0.18.em),
        color = CarCopilotColors.TextFaint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 8.dp else 18.dp, bottom = 8.dp)
            .drawBehind {
                val y = size.height + 8.dp.toPx()
                drawLine(
                    color = CarCopilotColors.Line,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun HistoryRow(entry: HistoryEntry, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!isLast) {
                    val y = size.height
                    drawLine(
                        color = CarCopilotColors.Line,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = entry.day.toString().padStart(2, '0'),
            style = CarCopilotTypography.VehicleSubtitle.copy(letterSpacing = 0.04.em),
            color = CarCopilotColors.TextFaint,
            modifier = Modifier
                .widthIn(min = 36.dp)
                .padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            TitleWithPills(entry.title, entry.pills)
            Spacer(Modifier.height(3.dp))
            Text(
                text = entry.sub,
                style = CarCopilotTypography.CardSubtitle.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = CarCopilotColors.TextMute,
            )
        }
    }
}

@Composable
private fun TitleWithPills(title: String, pills: List<HistoryPill>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = CarCopilotTypography.CardSubtitle.copy(fontSize = 14.sp),
            color = CarCopilotColors.Text,
        )
        pills.forEach { Pill(it) }
    }
}

@Composable
private fun Pill(pill: HistoryPill) {
    val (bg, fg, text) = when (pill) {
        HistoryPill.Open -> Triple(Color(0x21F5C518), CarCopilotColors.Accent, "in progress")
        HistoryPill.Resolved -> Triple(Color(0x1A4ADE80), CarCopilotColors.Healthy, "resolved")
        HistoryPill.Recurrence -> Triple(Color(0x21E44545), Color(0xFFFF9090), "recurrence")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = CarCopilotTypography.EvidenceSectionHeading.copy(
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                letterSpacing = 0.1.em,
            ),
            color = fg,
        )
    }
}
