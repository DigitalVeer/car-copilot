package com.example.carcopilot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

sealed interface TopBarLeft {
    data class Brand(val vehicleSubtitle: String) : TopBarLeft
    data class Back(val label: String = "Back", val onBack: () -> Unit) : TopBarLeft
}

@Composable
fun TopBar(left: TopBarLeft, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 16.dp)
            .defaultMinSize(minHeight = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when (left) {
            is TopBarLeft.Brand -> BrandMark(vehicleSubtitle = left.vehicleSubtitle)
            is TopBarLeft.Back -> BackButton(label = left.label, onBack = left.onBack)
        }
    }
}

@Composable
private fun BrandMark(vehicleSubtitle: String) {
    Column(verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "CAR", style = CarCopilotTypography.Brand, color = CarCopilotColors.Text)
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(CarCopilotColors.Accent),
            )
            Spacer(Modifier.width(2.dp))
            Text(text = "COPILOT", style = CarCopilotTypography.Brand, color = CarCopilotColors.Text)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = vehicleSubtitle,
            style = CarCopilotTypography.VehicleSubtitle,
            color = CarCopilotColors.TextMute,
        )
    }
}

@Composable
private fun BackButton(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onBack)
            .padding(top = 6.dp, bottom = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackChevron()
        Text(
            text = label,
            style = CarCopilotTypography.VehicleSubtitle.copy(letterSpacing = 0.08.em),
            color = CarCopilotColors.TextMute,
        )
    }
}

@Composable
private fun BackChevron() {
    val tint = CarCopilotColors.TextMute
    Canvas(modifier = Modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.18f)
            lineTo(w * 0.30f, h * 0.50f)
            lineTo(w * 0.62f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = size.minDimension * 0.14f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
