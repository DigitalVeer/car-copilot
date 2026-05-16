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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.carcopilot.model.FALLBACK_GOOD_NEWS_MISFIRE
import com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE
import com.example.carcopilot.model.Issue
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import kotlinx.coroutines.delay

/**
 * Home — minimum-viable home-multi state per spec §3.
 * AI strip shows a brief thinking animation then resolves to the canned
 * fallback synthesis (static, no Gemma call here per spec).
 * Issue card is tappable and navigates to Issue page.
 */
@Composable
fun HomeScreen(misfire: Issue, onIssueClick: () -> Unit) {
    var state by remember {
        mutableStateOf<SynthesisState>(SynthesisState.Thinking)
    }
    LaunchedEffect(Unit) {
        delay(900)
        state = SynthesisState.Ready(
            synthesis = FALLBACK_SYNTHESIS_MISFIRE,
            goodNews = FALLBACK_GOOD_NEWS_MISFIRE,
            isFallback = false, // canned-but-intentional, not an error fallback
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)),
    ) {
        TopBar(left = TopBarLeft.Brand(vehicleSubtitle = misfire.vehicle.displayName))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedAIStrip(state = state, label = "Today's drive", severity = misfire.severity)
            IssueCard(misfire, onClick = onIssueClick)
            AlsoRow(text = "Also: 12V battery is reading a little low.")
            AlsoRow(text = "Also: due for oil in about 1,500 miles.")
            Spacer(Modifier.fillMaxWidth().height(0.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                BottomTabBar(selected = "Home")
            }
        }
    }
}

@Composable
private fun IssueCard(issue: Issue, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161B25))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = issue.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFE6E8EE),
        )
        Text(
            text = issue.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E94A6),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "About $${issue.meta.costUsdMin}–$${issue.meta.costUsdMax} · ${issue.meta.timeMinutes} min · ${issue.meta.difficulty}",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8FB9FF),
        )
    }
}

@Composable
private fun AlsoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF8E94A6),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
fun BottomTabBar(selected: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161B25))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("Home", "History").forEach { tab ->
            Text(
                text = tab,
                color = if (tab == selected) Color(0xFFE6E8EE) else Color(0xFF6A7187),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
