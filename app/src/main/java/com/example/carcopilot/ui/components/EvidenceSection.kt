package com.example.carcopilot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun EvidenceToggle(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "SHOW RAW DATA FOR A MECHANIC",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = CarCopilotColors.Line,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = CarCopilotTypography.SectionLabel,
            color = CarCopilotColors.MetaBold,
        )
        Chevron(rotated = open)
    }
}

@Composable
fun EvidenceSection(
    dtcs: List<DTC>,
    readings: List<LiveReading>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (dtcs.isNotEmpty()) {
            SectionHeading(text = "TROUBLE CODES")
            dtcs.forEach { dtc -> DtcRow(dtc) }
        }
        if (readings.isNotEmpty()) {
            SectionHeading(text = "LIVE READINGS")
            Column(modifier = Modifier.fillMaxWidth()) {
                readings.forEachIndexed { idx, reading ->
                    LiveReadingRow(reading, isLast = idx == readings.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = CarCopilotTypography.EvidenceSectionHeading,
        color = CarCopilotColors.TextMute,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun DtcRow(dtc: DTC) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CarCopilotColors.EvidenceDtcBg)
            .border(1.dp, CarCopilotColors.EvidenceDtcBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = dtc.code,
            style = CarCopilotTypography.DtcCode,
            color = CarCopilotColors.Accent,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = dtc.description,
            style = CarCopilotTypography.DtcDescription,
            color = CarCopilotColors.MetaBold,
        )
    }
}

@Composable
private fun LiveReadingRow(reading: LiveReading, isLast: Boolean) {
    val valueText = buildString {
        append(reading.value)
        reading.unit?.let { append(' ').append(it) }
        reading.note?.let { append(" — ").append(it) }
    }
    val valueColor = when (reading.status) {
        LiveStatus.severe -> CarCopilotColors.Severe
        LiveStatus.warning -> CarCopilotColors.Accent
        LiveStatus.normal -> CarCopilotColors.Text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!isLast) {
                    val y = size.height
                    drawLine(
                        color = CarCopilotColors.Line,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = reading.key,
            style = CarCopilotTypography.EvidenceRowKey,
            color = CarCopilotColors.MetaBold,
        )
        Text(
            text = valueText,
            style = CarCopilotTypography.EvidenceRowValue,
            color = valueColor,
        )
    }
}

/** Down-pointing chevron, animates a 180° rotation when `rotated` flips. */
@Composable
private fun Chevron(rotated: Boolean) {
    val tint = CarCopilotColors.MetaBold
    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "evidence-chevron",
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
