package com.example.carcopilot.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

private const val DOT_PULSE_CYCLE_MS = 1400
private const val DOT_PULSE_PEAK_ALPHA = 1.0f
private const val DOT_PULSE_VALLEY_ALPHA = 0.35f

/**
 * First-paint screen shown while the LiteRT-LM engine warms up. The engine
 * init + prewarm take ~6s on a Pixel 9 even before any user-facing inference
 * runs, so we hide that latency behind a branded boot moment instead of
 * showing a Home screen that can't yet talk back. The "on-device" phrase
 * also reinforces the central product claim during the unavoidable wait.
 *
 * MainActivity gates the rest of the NavHost on (engine ready) AND
 * (minimum display time elapsed) so the splash never flashes for less than
 * a beat even on warm relaunches when the engine is already initialized.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarCopilotColors.PhoneBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SplashBrandMark()
            Spacer(Modifier.height(28.dp))
            BootingRow()
        }
    }
}

@Composable
private fun SplashBrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "CAR",
            style = CarCopilotTypography.Brand.copy(
                fontSize = 22.sp,
                letterSpacing = 0.22.em,
                fontWeight = FontWeight.Medium,
            ),
            color = CarCopilotColors.TitleBright,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(CarCopilotColors.Accent),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "COPILOT",
            style = CarCopilotTypography.Brand.copy(
                fontSize = 22.sp,
                letterSpacing = 0.22.em,
                fontWeight = FontWeight.Medium,
            ),
            color = CarCopilotColors.TitleBright,
        )
    }
}

@Composable
private fun BootingRow() {
    val transition = rememberInfiniteTransition(label = "boot-pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = DOT_PULSE_VALLEY_ALPHA,
        targetValue = DOT_PULSE_VALLEY_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DOT_PULSE_CYCLE_MS
                DOT_PULSE_VALLEY_ALPHA at 0 using LinearEasing
                DOT_PULSE_PEAK_ALPHA at DOT_PULSE_CYCLE_MS / 2 using LinearEasing
                DOT_PULSE_VALLEY_ALPHA at DOT_PULSE_CYCLE_MS using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "boot-pulse-alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(CarCopilotColors.Accent.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "BOOTING ON-DEVICE AI",
            style = CarCopilotTypography.SectionLabel.copy(
                fontSize = 11.sp,
                letterSpacing = 0.22.em,
            ),
            color = CarCopilotColors.TextMute,
        )
    }
}
