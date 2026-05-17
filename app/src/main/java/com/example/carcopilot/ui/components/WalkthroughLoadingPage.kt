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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

private const val THINKING_CYCLE_MS = 1200
private val DOT_SIZE = 18.dp
private val DOT_GAP = 24.dp
private val BOUNCE_HEIGHT = 8.dp
private val LABEL_GAP_TOP = 28.dp
private val LABEL_GAP_INTER = 6.dp

/**
 * Full-page loading state for the walkthrough screen. Centered ThinkingDots
 * motif scaled up to earn the ~2-minute plan + per-step generation wait,
 * with a primary "PREPARING YOUR WALKTHROUGH" label and a quieter
 * "GEMMA · ON-DEVICE" attribution underneath.
 *
 * Intentionally separate from [ThinkingDots]: that component lives inline
 * next to body content and uses 6dp dots; this one is the page itself and
 * uses 18dp. Both ride the same 1200ms bounce-and-fade keyframes for visual
 * consistency. Forking the dot composable rather than parameterizing
 * [ThinkingDots] keeps that long-standing component's external contract
 * frozen — the existing AI strip flows rely on its exact size and label.
 */
@Composable
fun WalkthroughLoadingPage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BigBouncingDots()
            Spacer(Modifier.height(LABEL_GAP_TOP))
            Text(
                text = "PREPARING YOUR WALKTHROUGH",
                style = CarCopilotTypography.TabLabel.copy(letterSpacing = 0.12.em),
                color = CarCopilotColors.TextMute,
            )
            Spacer(Modifier.height(LABEL_GAP_INTER))
            Text(
                text = "GEMMA · ON-DEVICE",
                style = CarCopilotTypography.TabLabel.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.16.em,
                ),
                color = CarCopilotColors.TextFaint,
            )
        }
    }
}

@Composable
private fun BigBouncingDots() {
    val color = CarCopilotColors.Accent
    val bouncePx = with(LocalDensity.current) { BOUNCE_HEIGHT.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        repeat(3) { idx ->
            BigBouncingDot(color = color, delayMs = idx * 150, bouncePx = bouncePx)
        }
    }
}

@Composable
private fun BigBouncingDot(color: Color, delayMs: Int, bouncePx: Float) {
    val transition = rememberInfiniteTransition(label = "loading-page-dot")
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
