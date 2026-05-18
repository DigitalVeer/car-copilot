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
import com.example.carcopilot.ui.components.accentInlineColor
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

private const val THINKING_GLOW_CYCLE_MS = 1100
private const val THINKING_GLOW_PEAK_ALPHA = 0.55f
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
    val accentFill = severity.accentColor()
    val accentInline = severity.accentInlineColor()
    Box(modifier = modifier.fillMaxWidth().padding(outerPadding)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            SeverityBar(state = state, accent = accentFill)
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
                        style = CarCopilotTypography.AiHeading,
                        color = accentInline,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    OnDeviceChip(
                        severity = severity,
                        breathing = state is SynthesisState.Thinking,
                    )
                }
                Spacer(Modifier.height(12.dp))
                AiBody(state = state, severity = severity)
            }
        }
    }
}

/**
 * The 2dp severity bar with a glow halo carrying all the motion:
 *  - Thinking  → glow alpha pulses 0 → 0.55 → 0 on a 1.1s loop. The bar itself
 *                stays solid. The motion happens across 4× more pixels than the
 *                bar's own width, which is what makes it visible at all.
 *  - Streaming → glow holds steady at 0.35 (fades in over 250ms). Calmer than
 *                the Thinking pulse on purpose — "warming up" reads more
 *                energetic than "streaming."
 *  - Ready     → no glow.
 *
 * The 8dp glow slot is always present in layout (just invisible at rest) so the
 * body column never shifts when the state changes. The Column's start padding
 * is reduced from 14dp to 6dp to compensate, preserving Phase-9 spacing.
 */
@Composable
private fun SeverityBar(state: SynthesisState, accent: Color) {
    val isThinking = state is SynthesisState.Thinking
    val isStreaming = state is SynthesisState.Streaming

    val thinkingGlow = if (isThinking) rememberThinkingGlowAlpha() else 0f
    val streamingGlow by animateFloatAsState(
        targetValue = if (isStreaming) STREAM_GLOW_ALPHA else 0f,
        animationSpec = tween(durationMillis = GLOW_TRANSITION_MS),
        label = "stream-glow-alpha",
    )
    val glowAlpha = maxOf(thinkingGlow, streamingGlow)

    Row(modifier = Modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(accent),
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
private fun rememberThinkingGlowAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "thinking-glow-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = THINKING_GLOW_CYCLE_MS
                0f at 0 using LinearEasing
                THINKING_GLOW_PEAK_ALPHA at THINKING_GLOW_CYCLE_MS / 2 using LinearEasing
                0f at THINKING_GLOW_CYCLE_MS using LinearEasing
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
            ColoredText(
                text = state.synthesis,
                style = CarCopilotTypography.AiBody,
                color = CarCopilotColors.Text,
            )
            state.goodNews?.let { news ->
                ColoredText(
                    text = news,
                    style = CarCopilotTypography.AiBody,
                    color = CarCopilotColors.Text,
                )
            }
        }
    }
}
