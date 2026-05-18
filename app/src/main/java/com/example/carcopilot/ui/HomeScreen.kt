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
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.fallbackGoodNewsFor
import com.example.carcopilot.model.fallbackSynthesisFor
import com.example.carcopilot.ui.components.ExpandableAlsoSection
import com.example.carcopilot.ui.components.BottomTabBar
import com.example.carcopilot.ui.components.IssueCard
import com.example.carcopilot.ui.components.Tab
import com.example.carcopilot.ui.components.TopBar
import com.example.carcopilot.ui.components.TopBarLeft
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography
import kotlinx.coroutines.delay

private const val HEALTHY_SYNTHESIS =
    "[G]No active issues[/G] right now. Everything we can read on the bus is in range."
private const val HEALTHY_GOOD_NEWS =
    "Last scan was today. You're [G]good to go[/G]."

/**
 * Home — minimum-viable home-multi state per spec §3.
 *
 * AI strip shows a brief thinking animation then resolves to a canned
 * synthesis derived from the active Issue (static here — Gemma streams
 * on the deeper Issue page, not on Home, per spec). The synthesis text,
 * good-news caveat, and CTA label all branch on the issue's severity so
 * a healthy, warning, or severe diagnosis each reads with the right
 * tone:
 *
 *   - Severity.healthy → green AI strip, "All clear" verdict, no
 *     "ACTIVE ISSUE" heading, "Run a fresh scan" CTA (visual only for
 *     now). The "also detected" expandable is hidden because nothing
 *     was detected.
 *   - Severity.severe → red AI strip, urgent verdict, "Stop & check
 *     now" CTA, ACTIVE ISSUE heading still shown.
 *   - Severity.warning (default) → indigo AI strip, the existing
 *     "Show me what's going on" CTA, ACTIVE ISSUE heading.
 */
@Composable
fun HomeScreen(
    misfire: Issue,
    onIssueClick: () -> Unit,
    onHistoryTab: () -> Unit = {},
) {
    var state by remember(misfire.id) {
        mutableStateOf<SynthesisState>(SynthesisState.Thinking)
    }
    LaunchedEffect(misfire.id) {
        delay(900)
        val (synthesis, goodNews) = synthesisForHome(misfire)
        state = SynthesisState.Ready(
            synthesis = synthesis,
            goodNews = goodNews,
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
            // Trip readiness rides inside the AI strip as its verdict footer
            // ("Safe for short trips", "Don't drive", "All clear") so it
            // shares the accent rail with the AI body — one chip, not two
            // floating tiles. Previously a separate soft card beneath.
            AnimatedAIStrip(
                state = state,
                label = "Today's drive",
                severity = misfire.severity,
                verdict = misfire.tripReadiness?.let { readiness ->
                    {
                        AiStripVerdict(
                            label = "TRIP READY",
                            headline = readiness.headline,
                            severity = misfire.severity,
                            caveat = readiness.caveat,
                        )
                    }
                },
            )
            // The healthy variant has no diagnosed issue, so the ACTIVE
            // ISSUE heading + tappable IssueCard don't apply. Warning and
            // severe variants both surface the focal card.
            if (misfire.severity != Severity.healthy) {
                Text(
                    text = "ACTIVE ISSUE",
                    style = CarCopilotTypography.SectionLabel.copy(letterSpacing = 0.18.em),
                    color = CarCopilotColors.TextFaint,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                IssueCard(
                    issue = misfire,
                    ctaLabel = ctaLabelForHome(misfire.severity),
                    onClick = onIssueClick,
                )
                Spacer(Modifier.height(24.dp))
                ExpandableAlsoSection(
                    items = listOf(
                        "Tighten gas cap next time you stop",
                        "Front-left tire low at 22 PSI",
                    ),
                )
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

private fun synthesisForHome(issue: Issue): Pair<String, String?> {
    if (issue.severity == Severity.healthy) {
        return HEALTHY_SYNTHESIS to HEALTHY_GOOD_NEWS
    }
    val code = issue.dtcs.firstOrNull()?.code
    return fallbackSynthesisFor(code) to fallbackGoodNewsFor(code)
}

private fun ctaLabelForHome(severity: Severity): String = when (severity) {
    Severity.severe -> "What do I do now? →"
    Severity.healthy -> "Run a fresh scan →"
    Severity.warning, Severity.info -> "Show me what's going on →"
}
