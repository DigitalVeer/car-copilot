package com.example.carcopilot.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.components.OnDeviceChip
import com.example.carcopilot.ui.components.ThinkingDots
import com.example.carcopilot.ui.components.accentColor
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

private const val BAR_PULSE_CYCLE_MS = 1400
private const val BAR_PULSE_MIN_ALPHA = 0.5f
private const val STREAM_GLOW_ALPHA = 0.35f
private const val GLOW_TRANSITION_MS = 250

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
            SeverityBar(state = state, accent = accent)
            Column(
                modifier = Modifier
                    .padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
                    .defaultMinSize(minHeight = 60.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = CarCopilotTypography.AiLabel,
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    OnDeviceChip(
                        severity = severity,
                        breathing = state is SynthesisState.Thinking,
                    )
                }
                Spacer(Modifier.height(10.dp))
                AiBody(state = state, severity = severity)
            }
        }
    }
}

/**
 * The 2dp severity bar with three states:
 *  - Thinking  → pulses alpha 0.5→1.0 on a ~1.4s sine-ish loop
 *  - Streaming → static at full alpha, plus an 8dp horizontal-gradient glow
 *                fading from accent.0.35 to transparent into the body padding
 *  - Ready     → static, full alpha, no glow
 *
 * The 8dp glow slot is always present in layout (just invisible at rest) so the
 * body column never shifts when the state changes. The Column's start padding
 * is reduced from 14dp to 6dp to compensate, preserving Phase-9 spacing.
 */
@Composable
private fun SeverityBar(state: SynthesisState, accent: Color) {
    val isThinking = state is SynthesisState.Thinking
    val isStreaming = state is SynthesisState.Streaming

    val pulseAlpha = if (isThinking) rememberPulseAlpha() else 1f
    val glowAlpha by animateFloatAsState(
        targetValue = if (isStreaming) STREAM_GLOW_ALPHA else 0f,
        animationSpec = tween(durationMillis = GLOW_TRANSITION_MS),
        label = "stream-glow-alpha",
    )

    Row(modifier = Modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = pulseAlpha)),
        )
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxHeight()
                .drawBehind {
                    if (glowAlpha > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accent.copy(alpha = glowAlpha),
                                    accent.copy(alpha = 0f),
                                ),
                            ),
                        )
                    }
                },
        )
    }
}

@Composable
private fun rememberPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "severity-bar-pulse")
    val alpha by transition.animateFloat(
        initialValue = BAR_PULSE_MIN_ALPHA,
        targetValue = BAR_PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BAR_PULSE_CYCLE_MS
                BAR_PULSE_MIN_ALPHA at 0 using LinearEasing
                1f at BAR_PULSE_CYCLE_MS / 2 using LinearEasing
                BAR_PULSE_MIN_ALPHA at BAR_PULSE_CYCLE_MS using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "alpha",
    )
    return alpha
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
