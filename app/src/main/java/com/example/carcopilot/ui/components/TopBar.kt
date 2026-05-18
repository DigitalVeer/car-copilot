package com.example.carcopilot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * The header is a single composition across all five screens. The top row
 * is the constant CAR · COPILOT brand mark + accent underline — same logo,
 * same color, same height on Home, Issue, Walkthrough, Mechanic Draft, and
 * History. The bottom row varies:
 *
 *   - Brand variant → vehicle subtitle text (Home, History)
 *   - Back variant  → polished "BACK" chip (Issue, Walkthrough, Mechanic Draft)
 *
 * Prior to this refactor, Brand was a tall three-row block and Back was a
 * tiny single-line chevron + text — the header collapsed in height and
 * shed brand identity the moment the user navigated into any deeper page.
 * Locking the brand row in place keeps visual identity constant and gives
 * navigation a consistent home regardless of which screen is on top.
 */
sealed interface TopBarLeft {
    data class Brand(val vehicleSubtitle: String) : TopBarLeft
    data class Back(val onBack: () -> Unit) : TopBarLeft
}

@Composable
fun TopBar(left: TopBarLeft, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandLogo()
            when (left) {
                is TopBarLeft.Brand -> VehicleSubtitle(text = left.vehicleSubtitle)
                is TopBarLeft.Back -> BackChip(onClick = left.onBack)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CarCopilotColors.Line),
        )
    }
}

/** Constant brand logo row + accent underline, shared by every screen. */
@Composable
private fun BrandLogo() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "CAR",
                style = CarCopilotTypography.Brand,
                color = CarCopilotColors.Text,
            )
            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .clip(CircleShape)
                    .background(CarCopilotColors.AccentInline),
            )
            Text(
                text = "COPILOT",
                style = CarCopilotTypography.Brand,
                color = CarCopilotColors.Text,
            )
        }
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.5.dp)
                .background(CarCopilotColors.AccentInline.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun VehicleSubtitle(text: String) {
    Text(
        text = text,
        style = CarCopilotTypography.VehicleSubtitle,
        color = CarCopilotColors.TextMute,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * Back affordance promoted to a pill chip with a soft indigo fill, hairline
 * border, and stronger chevron. The prior implementation was a 13dp chevron
 * next to a small mono "Back" label — easy to miss and read as a hyperlink
 * rather than a button. The chip gives the touch target visible bounds
 * (44dp+ hit slop via wrapping padding) and reads as an action.
 */
@Composable
private fun BackChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .scalePressable(onClick = onClick)
            .clip(RoundedCornerShape(999.dp))
            .background(CarCopilotColors.AccentSoft)
            .border(
                width = 1.dp,
                color = CarCopilotColors.AccentInline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(start = 9.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ChipChevron(color = CarCopilotColors.AccentInline)
        Text(
            text = "BACK",
            style = CarCopilotTypography.TabLabel.copy(letterSpacing = 0.14.em),
            color = CarCopilotColors.AccentInline,
        )
    }
}

@Composable
private fun ChipChevron(color: Color) {
    Canvas(modifier = Modifier.size(11.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.18f)
            lineTo(w * 0.30f, h * 0.50f)
            lineTo(w * 0.62f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = size.minDimension * 0.20f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
