package com.example.carcopilot.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun ExpandableAlsoSection(
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scalePressable { isExpanded = !isExpanded }
                .clip(RoundedCornerShape(12.dp))
                .background(CarCopilotColors.PhoneCard)
                .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Also detected",
                        style = CarCopilotTypography.SectionLabel,
                        color = CarCopilotColors.AccentInline,
                    )
                    Text(
                        text = "${items.size} other ${if (items.size == 1) "finding" else "findings"}",
                        style = CarCopilotTypography.AlsoRow,
                        color = CarCopilotColors.TextMute,
                    )
                }
                AlsoChevron(rotated = isExpanded)
            }
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { item ->
                    AlsoItemRow(text = item)
                }
            }
        }
    }
}

@Composable
private fun AlsoItemRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CarCopilotColors.PhoneCardSoft)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Solid indigo dot — Canvas circle reads more consistently than the
        // U+2022 bullet glyph, which varies in size and baseline across Geist
        // font versions and looks rough at 14sp.
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(CarCopilotColors.AccentInline),
        )
        Text(
            text = text,
            style = CarCopilotTypography.AlsoRow,
            color = CarCopilotColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Down-pointing chevron, animates a 180° rotation when [rotated] flips.
 * Canvas-drawn rather than rendered as a Unicode "▼" glyph — bullet/triangle
 * glyphs in Geist render with inconsistent baselines and weights, while the
 * Canvas chevron matches [EvidenceToggle]'s polished look pixel-for-pixel.
 */
@Composable
private fun AlsoChevron(rotated: Boolean) {
    val tint = CarCopilotColors.TextFaint
    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "also-chevron",
    )
    Canvas(
        modifier = Modifier
            .size(12.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.70f)
            lineTo(w * 0.82f, h * 0.38f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = size.minDimension * 0.16f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
