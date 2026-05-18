package com.example.carcopilot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.example.carcopilot.data.WalkthroughSpec
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * Collapsible chip grid of canonical [WalkthroughSpec]s pulled from the
 * curated procedure for the issue's DTC. The numbers (torques, gaps,
 * pressures) are an authoritative reference for the rare user who'll actually
 * turn a wrench — but for the much larger audience that just wants to follow
 * the AI body, they're noise that bloats the screen. Collapsed by default so
 * the walkthrough reads cleanly; expanded on tap when a mechanic-minded user
 * wants the specs in view.
 *
 * Phase W2.1 — film-around for persisting numeric drift on less-rehearsed
 * DTC paths. Per-step content still uses the tighter sampler; the chip row
 * is the visible cross-check sitting beside the AI body when invoked.
 */
@Composable
fun SpecsChipRow(
    specs: List<WalkthroughSpec>,
    modifier: Modifier = Modifier,
) {
    if (specs.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarCopilotColors.PhoneCardSoft)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "SPECS FROM PROCEDURE",
                style = CarCopilotTypography.EvidenceSectionHeading,
                color = CarCopilotColors.TextMute,
            )
            ChevronSpec(rotated = expanded)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 240)) +
                fadeIn(animationSpec = tween(durationMillis = 240)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 200)) +
                fadeOut(animationSpec = tween(durationMillis = 160)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    specs.forEach { spec -> SpecChip(spec) }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SpecChip(spec: WalkthroughSpec) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = spec.label.uppercase(),
            style = CarCopilotTypography.TabLabel,
            color = CarCopilotColors.TextMute,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = spec.value,
            style = CarCopilotTypography.EvidenceRowValue,
            color = CarCopilotColors.TitleBright,
        )
    }
}

@Composable
private fun ChevronSpec(rotated: Boolean) {
    val tint = CarCopilotColors.MetaBold
    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "specs-chevron",
    )
    Canvas(
        modifier = Modifier
            .size(10.dp)
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
