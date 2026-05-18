package com.example.carcopilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.theme.CarCopilotTypography
import com.example.carcopilot.ui.theme.JetBrainsMono

private const val CHIP_TEXT = "GEMMA · ON-DEVICE"

/**
 * Severity-tinted mono pill that labels the AI strip as on-device inference.
 * Sits at the trailing edge of the AI strip's label row on every surface.
 *
 * Static label — the chip used to breathe alpha 0.55→1.0 during Thinking,
 * but with the rail glow already pulsing and the ThinkingDots already
 * bouncing in the body, three concurrent loops felt busy. The chip is now
 * a quiet attribution mark; the rail and dots carry all the "AI is working"
 * motion.
 */
@Composable
fun OnDeviceChip(
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    val fill = severity.accentColor()
    val inline = severity.accentInlineColor()
    Text(
        text = CHIP_TEXT,
        style = CarCopilotTypography.AiLabel.copy(
            fontFamily = JetBrainsMono,
            fontSize = 8.5.sp,
            letterSpacing = 0.14.em,
        ),
        color = inline,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill.copy(alpha = 0.08f))
            .border(1.dp, fill.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
