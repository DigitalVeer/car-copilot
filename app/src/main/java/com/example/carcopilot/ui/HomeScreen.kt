package com.example.carcopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.carcopilot.model.FALLBACK_GOOD_NEWS_MISFIRE
import com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE
import com.example.carcopilot.model.Issue
import com.example.carcopilot.ui.components.AlsoSection
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.IssueCard
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.components.TripReadinessTile
import com.example.carcopilot.ui.theme.CarCopilotColors
import kotlinx.coroutines.delay

/**
 * Home — minimum-viable home-multi state per spec §3.
 * AI strip shows a brief thinking animation then resolves to the canned
 * fallback synthesis (static, no Gemma call here per spec).
 * Issue card is tappable and navigates to Issue page.
 */
@Composable
fun HomeScreen(
    misfire: Issue,
    onIssueClick: () -> Unit,
    onHistoryTab: () -> Unit = {},
) {
    var state by remember {
        mutableStateOf<SynthesisState>(SynthesisState.Thinking)
    }
    LaunchedEffect(Unit) {
        delay(900)
        state = SynthesisState.Ready(
            synthesis = FALLBACK_SYNTHESIS_MISFIRE,
            goodNews = FALLBACK_GOOD_NEWS_MISFIRE,
            isFallback = false,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarCopilotColors.PhoneBg),
    ) {
        TopBar(left = TopBarLeft.Brand(vehicleSubtitle = misfire.vehicle.displayName))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp),
        ) {
            AnimatedAIStrip(
                state = state,
                label = "Today's drive",
                severity = misfire.severity,
            )
            // Trip readiness sits directly below the AI strip — it's the single
            // sentence the driver most needs to see ("Safe for short trips"),
            // and burying it under the IssueCard + AlsoSection meant it lived
            // below the fold on most phones.
            misfire.tripReadiness?.let { readiness ->
                TripReadinessTile(readiness = readiness, severity = misfire.severity)
                Spacer(Modifier.height(20.dp))
            }
            IssueCard(
                issue = misfire,
                ctaLabel = "Show me what's going on →",
                onClick = onIssueClick,
            )
            Spacer(Modifier.height(24.dp))
            AlsoSection(
                items = listOf(
                    "Tighten gas cap next time you stop",
                    "Front-left tire low at 22 PSI",
                ),
            )
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
