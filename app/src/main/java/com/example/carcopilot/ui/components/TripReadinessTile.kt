package com.example.carcopilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.model.Severity
import com.example.carcopilot.model.TripReadiness
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * Driver-facing verdict anchored on the Home page beneath the AI strip.
 * Originally rendered as a peer card to [IssueCard] (white surface, 3dp
 * severity bar, 14dp corners) but on the light theme the two cards read
 * as siblings — the user couldn't tell which one was the actual issue.
 *
 * Light-theme rebuild: this is now a quiet verdict strip, not a card.
 * Soft-tinted background, no border, no left bar, smaller padding, a
 * 7dp severity-colored dot beside the "TRIP READY" mono label. The
 * [IssueCard] below stays a full card with the 3dp bar, so the
 * page reads: AI heading → quiet verdict strip → ACTIVE ISSUE label →
 * issue card. Clear hierarchy, no ambiguity.
 */
@Composable
fun TripReadinessTile(
    readiness: TripReadiness,
    severity: Severity,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarCopilotColors.PhoneCardSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(severity.accentColor()),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TRIP READY",
                style = CarCopilotTypography.SectionLabel.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.18.em,
                ),
                color = CarCopilotColors.TextMute,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = readiness.headline,
                style = CarCopilotTypography.CardSubtitle.copy(
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                ),
                color = CarCopilotColors.Text,
            )
            readiness.caveat?.let { caveat ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = caveat,
                    style = CarCopilotTypography.CardSubtitle.copy(fontSize = 13.sp),
                    color = CarCopilotColors.TextMute,
                )
            }
        }
    }
}
