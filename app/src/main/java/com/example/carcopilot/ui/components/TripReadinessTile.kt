package com.example.carcopilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.TripReadiness
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * Driver-facing verdict anchored at the bottom of the Home page. The CTA
 * inside [IssueCard] tells you *what* to do; this tile tells you whether
 * you can drive today at all. Filled the lower dead zone where Home
 * previously had ~30% empty black.
 *
 * Visual vocabulary matches [IssueCard]'s frame (PhoneCard bg, 1dp Line
 * border, 14dp rounded corner, 3dp severity left bar, 22dp inner padding)
 * so the two cards read as a pair.
 */
@Composable
fun TripReadinessTile(
    readiness: TripReadiness,
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(14.dp)),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(severity.accentColor()),
            )
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "TRIP READY",
                    style = CarCopilotTypography.SectionLabel.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.18.em,
                    ),
                    color = CarCopilotColors.TextFaint,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = readiness.headline,
                    style = CarCopilotTypography.CardTitle.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 28.sp,
                    ),
                    color = CarCopilotColors.TitleBright,
                )
                readiness.caveat?.let { caveat ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = caveat,
                        style = CarCopilotTypography.CardSubtitle,
                        color = CarCopilotColors.TextMute,
                    )
                }
            }
        }
    }
}
