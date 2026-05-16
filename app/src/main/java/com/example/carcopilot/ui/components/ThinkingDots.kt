package com.example.carcopilot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

private const val THINKING_CYCLE_MS = 1200
private val DOT_SIZE = 6.dp
private val DOT_GAP = 4.dp
private val LABEL_GAP = 10.dp
private val BOUNCE_HEIGHT = 3.dp

@Composable
fun ThinkingDots(severity: Severity, modifier: Modifier = Modifier) {
    val color = severity.accentColor()
    val bouncePx = with(LocalDensity.current) { BOUNCE_HEIGHT.toPx() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
        ) {
            repeat(3) { idx -> BouncingDot(color = color, delayMs = idx * 150, bouncePx = bouncePx) }
        }
        Spacer(Modifier.width(LABEL_GAP))
        Text(
            text = "thinking on-device…",
            style = CarCopilotTypography.TabLabel,
            color = CarCopilotColors.TextMute,
        )
    }
}

@Composable
private fun BouncingDot(color: Color, delayMs: Int, bouncePx: Float) {
    val transition = rememberInfiniteTransition(label = "thinking-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = THINKING_CYCLE_MS
                0.3f at 0
                1f at (THINKING_CYCLE_MS * 0.4f).toInt()
                0.3f at (THINKING_CYCLE_MS * 0.8f).toInt()
                0.3f at THINKING_CYCLE_MS
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "alpha",
    )
    val translateY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = THINKING_CYCLE_MS
                0f at 0
                -bouncePx at (THINKING_CYCLE_MS * 0.4f).toInt()
                0f at (THINKING_CYCLE_MS * 0.8f).toInt()
                0f at THINKING_CYCLE_MS
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "translateY",
    )
    Box(
        modifier = Modifier
            .size(DOT_SIZE)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translateY
            }
            .clip(CircleShape)
            .background(color),
    )
}

internal fun Severity.accentColor(): Color = when (this) {
    Severity.severe -> CarCopilotColors.Severe
    Severity.healthy -> CarCopilotColors.Healthy
    Severity.warning, Severity.info -> CarCopilotColors.Accent
}
