package com.example.carcopilot.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.carcopilot.inference.GemmaService
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.FALLBACK_GOOD_NEWS_MISFIRE
import com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft

@Composable
fun IssueScreen(issue: Issue, gemma: GemmaService, onBack: () -> Unit) {
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
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(left = TopBarLeft.Back(onBack = onBack))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedAIStrip(
                state = state,
                label = "Here's what I'm seeing",
                severity = issue.severity,
            )
            IssueMetaCard(issue)
            EvidenceToggle(open = evidenceOpen, onClick = { evidenceOpen = !evidenceOpen })
            if (evidenceOpen) {
                EvidenceContent(issue.dtcs, issue.liveReadings)
            }
            Spacer(Modifier.height(16.dp))
            BottomTabBar(selected = "Home")
        }
    }
}

@Composable
private fun IssueMetaCard(issue: Issue) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161B25))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(issue.title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE6E8EE))
        Text(issue.subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E94A6))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "About $${issue.meta.costUsdMin}–$${issue.meta.costUsdMax} · ${issue.meta.timeMinutes} min · ${issue.meta.difficulty} · ${issue.meta.drivability}",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8FB9FF),
        )
    }
}

@Composable
private fun EvidenceToggle(open: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF161B25))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = if (open) "Hide evidence" else "Show the codes & readings",
            color = Color(0xFF8FB9FF),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EvidenceContent(dtcs: List<DTC>, readings: List<LiveReading>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("OBD codes", color = Color(0xFFE6E8EE), style = MaterialTheme.typography.labelMedium)
        dtcs.forEach { dtc ->
            Text(
                text = "${dtc.code} · ${dtc.description}",
                color = Color(0xFFE6E8EE),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Live readings", color = Color(0xFFE6E8EE), style = MaterialTheme.typography.labelMedium)
        readings.forEach { r ->
            val color = when (r.status) {
                LiveStatus.severe -> Color(0xFFFF7A7A)
                LiveStatus.warning -> Color(0xFFE6C26A)
                LiveStatus.normal -> Color(0xFF8E94A6)
            }
            val noteSuffix = r.note?.let { " — $it" } ?: ""
            val unitSuffix = r.unit?.let { " $it" } ?: ""
            Text(
                text = "${r.key}: ${r.value}$unitSuffix$noteSuffix",
                color = color,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
