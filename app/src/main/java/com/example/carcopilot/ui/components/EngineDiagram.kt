package com.example.carcopilot.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.carcopilot.R
import com.example.carcopilot.ui.theme.CarCopilotColors

private const val VIEW_W = 280f
private const val VIEW_H = 180f

/**
 * Port of mockup §06 walkthrough engine-bay SVG.
 *
 * ViewBox is 280×180. Aspect ratio is fixed at the same; the surrounding
 * Box determines absolute width. Shapes go on a Canvas; the three labels
 * use nativeCanvas.drawText with the bundled JetBrains Mono typeface so
 * we don't have to position Compose Text overlays at sub-dp precision.
 */
@Composable
fun EngineDiagram(highlightedCoil: Int = 1, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val typeface: Typeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    }
    val outline = CarCopilotColors.TextFaint
    val frame = CarCopilotColors.LineBright
    val accent = CarCopilotColors.Accent
    val muted = CarCopilotColors.TextFaint
    val valveCoverFill = androidx.compose.ui.graphics.Color(0xFF1A1A18)
    val valveCoverStroke = androidx.compose.ui.graphics.Color(0xFF4A4A47)
    val coilStroke = androidx.compose.ui.graphics.Color(0xFF4A4A47)
    val engineStroke = androidx.compose.ui.graphics.Color(0xFF3A3A37)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VIEW_W / VIEW_H),
    ) {
        val sx = size.width / VIEW_W
        val sy = size.height / VIEW_H
        // Engine block outline
        drawRoundedRect(30f, 55f, 220f, 95f, 6f, sx, sy, color = engineStroke, strokeWidth = 1.5f * sx)
        // Valve cover
        drawRoundedRect(42f, 70f, 196f, 40f, 3f, sx, sy, color = valveCoverFill, fill = true)
        drawRoundedRect(42f, 70f, 196f, 40f, 3f, sx, sy, color = valveCoverStroke, strokeWidth = 1f * sx)
        // Four coils
        val coilXs = listOf(56f, 100f, 144f, 188f)
        coilXs.forEachIndexed { idx, x ->
            val active = (idx + 1) == highlightedCoil
            val color = if (active) accent else coilStroke
            val fill = if (active) accent else null
            drawRoundedRect(x, 40f, 32f, 38f, 3f, sx, sy, color = color, strokeWidth = 1.5f * sx, fillColor = fill)
            // Wire connector
            drawLine(
                color = color,
                start = Offset((x + 16f) * sx, 33f * sy),
                end = Offset((x + 16f) * sx, 40f * sy),
                strokeWidth = 1.5f * sx,
            )
            // Connector dot
            if (active) {
                drawCircle(color = color, radius = 3f * sx, center = Offset((x + 16f) * sx, 32f * sy))
            } else {
                drawCircle(
                    color = color,
                    radius = 3f * sx,
                    center = Offset((x + 16f) * sx, 32f * sy),
                    style = Stroke(width = 1f * sx),
                )
            }
        }
        // Hoses
        val leftHose = Path().apply {
            moveTo(30f * sx, 90f * sy)
            quadraticTo(18f * sx, 90f * sy, 18f * sx, 110f * sy)
            lineTo(18f * sx, 145f * sy)
        }
        drawPath(
            path = leftHose,
            color = engineStroke,
            style = Stroke(width = 1.5f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val rightHose = Path().apply {
            moveTo(250f * sx, 100f * sy)
            quadraticTo(262f * sx, 100f * sy, 262f * sx, 120f * sy)
            lineTo(262f * sx, 145f * sy)
        }
        drawPath(
            path = rightHose,
            color = engineStroke,
            style = Stroke(width = 1.5f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Arrow + label for the highlighted coil
        val arrowX = coilXs[highlightedCoil - 1] + 16f
        drawLine(
            color = accent,
            start = Offset(arrowX * sx, 15f * sy),
            end = Offset(arrowX * sx, 32f * sy),
            strokeWidth = 1.5f * sx,
        )
        val arrowHead = Path().apply {
            moveTo((arrowX - 4f) * sx, 28f * sy)
            lineTo(arrowX * sx, 36f * sy)
            lineTo((arrowX + 4f) * sx, 28f * sy)
            close()
        }
        drawPath(path = arrowHead, color = accent)
        // Native text labels
        drawIntoCanvas { canvas ->
            val titlePaint = Paint().apply {
                this.color = accent.toArgb()
                this.textSize = 9f * sy
                this.textAlign = Paint.Align.CENTER
                this.typeface = typeface
                this.isAntiAlias = true
                this.letterSpacing = 0.06f
            }
            canvas.nativeCanvas.drawText(
                "COIL ${highlightedCoil} — REPLACE THIS",
                arrowX * sx,
                11f * sy,
                titlePaint,
            )
            val edgePaint = Paint().apply {
                this.color = muted.toArgb()
                this.textSize = 8f * sy
                this.textAlign = Paint.Align.CENTER
                this.typeface = typeface
                this.isAntiAlias = true
                this.letterSpacing = 0.12f
            }
            canvas.nativeCanvas.drawText("← FRONT", 72f * sx, 170f * sy, edgePaint)
            canvas.nativeCanvas.drawText("CABIN →", 208f * sx, 170f * sy, edgePaint)
        }
    }
}

private fun DrawScope.drawRoundedRect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    r: Float,
    sx: Float,
    sy: Float,
    color: androidx.compose.ui.graphics.Color,
    strokeWidth: Float = 0f,
    fill: Boolean = false,
    fillColor: androidx.compose.ui.graphics.Color? = null,
) {
    val topLeft = Offset(x * sx, y * sy)
    val sz = Size(w * sx, h * sy)
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * sx, r * sy)
    if (fill || fillColor != null) {
        drawRoundRect(
            color = fillColor ?: color,
            topLeft = topLeft,
            size = sz,
            cornerRadius = cornerRadius,
        )
    }
    if (strokeWidth > 0f) {
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = sz,
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth),
        )
    }
}
