package com.example.carcopilot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

enum class Tab { Home, History }

@Composable
fun BottomTabBar(
    selected: Tab,
    modifier: Modifier = Modifier,
    onSelect: (Tab) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CarCopilotColors.PhoneBg)
            .drawBehind {
                drawLine(
                    color = CarCopilotColors.Line,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 10.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabButton(
            label = "Home",
            active = selected == Tab.Home,
            icon = { HomeIcon(it) },
            onClick = { onSelect(Tab.Home) },
        )
        TabButton(
            label = "History",
            active = selected == Tab.History,
            icon = { ClockIcon(it) },
            onClick = { onSelect(Tab.History) },
        )
    }
}

@Composable
private fun TabButton(
    label: String,
    active: Boolean,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    // Icon stroke uses the indigo fill; the label uses the indigo INLINE
    // variant so the small mono text renders crisply on white per the
    // two-tone discipline.
    val iconTint = if (active) CarCopilotColors.Accent else CarCopilotColors.TextFaint
    val labelTint = if (active) CarCopilotColors.AccentInline else CarCopilotColors.TextFaint
    Column(
        modifier = Modifier
            .scalePressable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(iconTint)
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = CarCopilotTypography.TabLabel,
            color = labelTint,
        )
    }
}

@Composable
private fun HomeIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.09f
        val roof = Path().apply {
            moveTo(w * 0.10f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.90f, h * 0.50f)
        }
        drawPath(
            path = roof,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val body = Path().apply {
            moveTo(w * 0.20f, h * 0.46f)
            lineTo(w * 0.20f, h * 0.86f)
            lineTo(w * 0.80f, h * 0.86f)
            lineTo(w * 0.80f, h * 0.46f)
        }
        drawPath(
            path = body,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val door = Path().apply {
            moveTo(w * 0.42f, h * 0.86f)
            lineTo(w * 0.42f, h * 0.62f)
            lineTo(w * 0.58f, h * 0.62f)
            lineTo(w * 0.58f, h * 0.86f)
        }
        drawPath(
            path = door,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun ClockIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.09f
        val r = w * 0.40f
        val cx = w / 2f
        val cy = h / 2f
        drawCircle(
            color = tint,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = stroke),
        )
        val hands = Path().apply {
            moveTo(cx, cy)
            lineTo(cx, cy - r * 0.62f)
            moveTo(cx, cy)
            lineTo(cx + r * 0.45f, cy)
        }
        drawPath(
            path = hands,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
