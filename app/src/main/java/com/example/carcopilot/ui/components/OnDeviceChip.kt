package com.example.carcopilot.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.theme.CarCopilotTypography
import com.example.carcopilot.ui.theme.JetBrainsMono

private const val BREATHE_CYCLE_MS = 1600
private const val CHIP_TEXT = "GEMMA · ON-DEVICE"

/**
 * Severity-tinted mono pill that labels the AI strip as on-device inference.
 * Sits at the trailing edge of the AI strip's label row on every surface.
 * Breathes alpha 0.55→1.0 during Thinking; static at full alpha otherwise —
 * the breathing is the only signal that the model is currently warming up.
 */
@Composable
fun OnDeviceChip(
    severity: Severity,
    breathing: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = severity.accentColor()
    val alpha = if (breathing) rememberBreatheAlpha() else 1f
    Text(
        text = CHIP_TEXT,
        style = CarCopilotTypography.AiLabel.copy(
            fontFamily = JetBrainsMono,
            fontSize = 8.5.sp,
            letterSpacing = 0.14.em,
        ),
        color = tint.copy(alpha = 0.85f),
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.06f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun rememberBreatheAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "on-device-chip-breathe")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BREATHE_CYCLE_MS
                0.55f at 0 using LinearEasing
                1f at BREATHE_CYCLE_MS / 2 using LinearEasing
                0.55f at BREATHE_CYCLE_MS using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "alpha",
    )
    return alpha
}
