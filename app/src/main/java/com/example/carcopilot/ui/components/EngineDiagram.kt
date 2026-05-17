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
import com.example.carcopilot.data.DiagramTarget
import com.example.carcopilot.ui.theme.CarCopilotColors

private const val VIEW_W = 280f
private const val VIEW_H = 180f

/**
 * Port of mockup §06 walkthrough engine-bay SVG.
 *
 * ViewBox is 280×180. Aspect ratio is fixed; the surrounding Box determines
 * absolute width. Shapes go on a Canvas; the labels use nativeCanvas.drawText
 * with the bundled JetBrains Mono typeface so we don't have to position
 * Compose Text overlays at sub-dp precision.
 *
 * Highlight rendering is layered so a COIL_1 → SPARK_PLUG transition looks
 * like the focus drilling into the cylinder, not standing still on the
 * same shape. Labels are terse identifiers only — the AI strip body
 * explains what the user is doing, the diagram identifies what the body
 * is talking about:
 *   - COIL_n  → fill the 32×38 coil rectangle in accent, draw top arrow
 *               + "COIL n" label.
 *   - SPARK_PLUG → small accent dot inside the cylinder 1 coil well (it
 *               always means cylinder 1 in the P0301 demo), with a "PLUG"
 *               label below the well so the eye moves further down than
 *               for the coil highlight.
 *   - BATTERY_NEG → accent the negative terminal of the battery silhouette
 *               at the bottom-left, with a "(−)" label.
 *
 * Empty highlights render the diagram with no accents — a calm "this is the
 * engine bay you're working in" overview state.
 */
@Composable
fun EngineDiagram(
    highlights: List<DiagramTarget> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val typeface: Typeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    }
    val frame = CarCopilotColors.LineBright
    val accent = CarCopilotColors.Accent
    val muted = CarCopilotColors.TextFaint
    val valveCoverFill = androidx.compose.ui.graphics.Color(0xFF1A1A18)
    val valveCoverStroke = androidx.compose.ui.graphics.Color(0xFF4A4A47)
    val coilStroke = androidx.compose.ui.graphics.Color(0xFF4A4A47)
    val engineStroke = androidx.compose.ui.graphics.Color(0xFF3A3A37)

    val highlightedCoils: Set<Int> = highlights.mapNotNull { target ->
        when (target) {
            DiagramTarget.COIL_1 -> 1
            DiagramTarget.COIL_2 -> 2
            DiagramTarget.COIL_3 -> 3
            DiagramTarget.COIL_4 -> 4
            else -> null
        }
    }.toSet()
    val sparkPlugHighlighted = DiagramTarget.SPARK_PLUG in highlights
    val batteryNegHighlighted = DiagramTarget.BATTERY_NEG in highlights
    val primaryHighlight = highlights.firstOrNull()

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
            val cylinder = idx + 1
            val active = cylinder in highlightedCoils
            val color = if (active) accent else coilStroke
            val fill = if (active) accent else null
            drawRoundedRect(x, 40f, 32f, 38f, 3f, sx, sy, color = color, strokeWidth = 1.5f * sx, fillColor = fill)
            // Wire connector stub
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
        // Battery silhouette (always drawn; terminals tint on highlight).
        // Sits in the bottom-left zone clear of the left hose.
        drawBattery(
            x = 60f, y = 120f, w = 36f, h = 22f, sx = sx, sy = sy,
            bodyStroke = engineStroke,
            negColor = if (batteryNegHighlighted) accent else muted,
            posColor = muted,
        )

        // Top arrow + COIL label — only when the primary highlight is a coil.
        val coilPrimary = (primaryHighlight as? DiagramTarget)?.let {
            when (it) {
                DiagramTarget.COIL_1 -> 1
                DiagramTarget.COIL_2 -> 2
                DiagramTarget.COIL_3 -> 3
                DiagramTarget.COIL_4 -> 4
                else -> null
            }
        }
        if (coilPrimary != null) {
            val arrowX = coilXs[coilPrimary - 1] + 16f
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
        }

        // Spark plug — inner dot inside cylinder 1's coil well, layered on top
        // of the coil rectangle. Always cylinder 1 in the P0301 demo. Smaller
        // than the coil fill so a COIL_1 → SPARK_PLUG transition reads as
        // the focus moving deeper.
        if (sparkPlugHighlighted) {
            val plugCenterX = coilXs[0] + 16f
            val plugCenterY = 59f  // mid-coil vertically (coil spans y=40 to y=78)
            // Outer ghost ring suggesting depth
            drawCircle(
                color = accent,
                radius = 9f * sx,
                center = Offset(plugCenterX * sx, plugCenterY * sy),
                style = Stroke(width = 1f * sx),
            )
            // Inner solid dot — the plug itself
            drawCircle(
                color = accent,
                radius = 4.5f * sx,
                center = Offset(plugCenterX * sx, plugCenterY * sy),
            )
        }

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
            when {
                coilPrimary != null -> {
                    val arrowX = coilXs[coilPrimary - 1] + 16f
                    canvas.nativeCanvas.drawText(
                        "COIL $coilPrimary",
                        arrowX * sx,
                        11f * sy,
                        titlePaint,
                    )
                }
                sparkPlugHighlighted -> {
                    val plugLabelX = coilXs[0] + 16f
                    // Label below the coil well so the eye drops downward,
                    // distinguishing this from the coil label position.
                    canvas.nativeCanvas.drawText(
                        "PLUG",
                        plugLabelX * sx,
                        92f * sy,
                        titlePaint,
                    )
                }
                batteryNegHighlighted -> {
                    canvas.nativeCanvas.drawText(
                        "(−)",
                        78f * sx,
                        115f * sy,
                        titlePaint,
                    )
                }
            }
            val edgePaint = Paint().apply {
                this.color = muted.toArgb()
                this.textSize = 8f * sy
                this.textAlign = Paint.Align.CENTER
                this.typeface = typeface
                this.isAntiAlias = true
                this.letterSpacing = 0.12f
            }
            canvas.nativeCanvas.drawText("← FRONT", 130f * sx, 170f * sy, edgePaint)
            canvas.nativeCanvas.drawText("CABIN →", 220f * sx, 170f * sy, edgePaint)
            // suppress unused-var warning while keeping `frame` available for
            // future highlight shapes that need the brighter line color.
            @Suppress("UNUSED_VARIABLE")
            val unused = frame
        }
    }
}

private fun DrawScope.drawBattery(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    sx: Float,
    sy: Float,
    bodyStroke: androidx.compose.ui.graphics.Color,
    negColor: androidx.compose.ui.graphics.Color,
    posColor: androidx.compose.ui.graphics.Color,
) {
    drawRoundedRect(x, y, w, h, 2f, sx, sy, color = bodyStroke, strokeWidth = 1f * sx)
    val terminalW = 6f
    val terminalH = 4f
    // Negative on the left, positive on the right — convention matches typical
    // top-post automotive batteries.
    val negX = x + w * 0.18f
    val posX = x + w * 0.66f
    val termTopY = y - terminalH
    // Negative terminal
    drawRoundedRect(
        negX, termTopY, terminalW, terminalH, 1f, sx, sy,
        color = negColor, fill = true, fillColor = negColor,
    )
    // Positive terminal
    drawRoundedRect(
        posX, termTopY, terminalW, terminalH, 1f, sx, sy,
        color = posColor, fill = true, fillColor = posColor,
    )
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
