package com.example.carcopilot.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.components.ThinkingDots
import com.example.carcopilot.ui.components.accentColor
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * The hero component. A 2dp severity-colored bar runs the full height of the
 * label + body column. Body shape depends on SynthesisState:
 *   - Thinking  → ThinkingDots
 *   - Streaming → partial text
 *   - Ready     → synthesis, optionally followed by good_news
 * The label color matches the severity. `isFallback` is intentionally not
 * surfaced — the user just sees the canned text; the diagnostic signal lives
 * in logs.
 */
@Composable
fun AnimatedAIStrip(
    state: SynthesisState,
    label: String,
    severity: Severity,
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = PaddingValues(bottom = 32.dp),
) {
    val accent = severity.accentColor()
    Box(modifier = modifier.fillMaxWidth().padding(outerPadding)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .padding(start = 14.dp, top = 4.dp, bottom = 4.dp)
                    .defaultMinSize(minHeight = 60.dp),
            ) {
                Text(
                    text = label,
                    style = CarCopilotTypography.AiLabel,
                    color = accent,
                )
                Spacer(Modifier.height(10.dp))
                AiBody(state = state, severity = severity)
            }
        }
    }
}

@Composable
private fun AiBody(state: SynthesisState, severity: Severity) {
    // Crossfade only on the Thinking → resolved threshold so per-token
    // Streaming updates flow as plain recompositions and don't re-trigger
    // the 200ms fade on every append.
    Crossfade(
        targetState = state is SynthesisState.Thinking,
        animationSpec = tween(durationMillis = 200),
        label = "ai-strip-body",
    ) { thinking ->
        if (thinking) {
            ThinkingDots(severity)
        } else {
            ResolvedBody(state)
        }
    }
}

@Composable
private fun ResolvedBody(state: SynthesisState) {
    when (state) {
        is SynthesisState.Thinking -> Unit // unreachable inside the false Crossfade branch
        is SynthesisState.Streaming -> Text(
            text = state.partial,
            style = CarCopilotTypography.AiBody,
            color = CarCopilotColors.Text,
        )
        is SynthesisState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = state.synthesis,
                style = CarCopilotTypography.AiBody,
                color = CarCopilotColors.Text,
            )
            state.goodNews?.let { news ->
                Text(
                    text = news,
                    style = CarCopilotTypography.AiBody,
                    color = CarCopilotColors.Text,
                )
            }
        }
    }
}
