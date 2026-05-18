package com.example.carcopilot.ui.components.schematic

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.example.carcopilot.R
import com.example.carcopilot.ui.theme.CarCopilotColors

/**
 * Renders a [SchematicSpec] inside the dark inset on the walkthrough screen.
 *
 * Layering, bottom-up:
 *   1. Faint background grid — sells the "instrument view" framing.
 *   2. Inner bezel — a 1px stroke just inside the surface, like an LCD frame.
 *   3. Base shapes from [SchematicSpec.shapes], in declaration order.
 *   4. Highlight overlays — one per id in [highlightedIds] that resolves to a
 *      [HighlightRegion]. Highlight alpha pulses 0.65 → 1.0 over 1.5s with
 *      RepeatMode.Reverse — same cadence as the loading-page dots
 *      ([com.example.carcopilot.ui.components.WalkthroughLoadingPage]) so
 *      the page reads coherently.
 *   5. Edge labels from [SchematicSpec.edgeLabels] — drawn with the muted
 *      paint at the bottom of the viewport.
 *   6. Per-region labels and arrows — drawn last so they sit above geometry.
 *
 * Each region in [SchematicSpec.regions] carries its own animated alpha
 * keyed on whether its id is currently in [highlightedIds]. When the step
 * advances and the highlight moves (e.g. COIL_1 → PLUG_1), COIL_1 fades to
 * zero over 240ms while PLUG_1 fades up — the swap reads as attention
 * shifting between components, not as a hard pop. The animated alpha is
 * multiplied by the breathing [pulse] so both motions compound cleanly.
 *
 * The native-canvas text path needs a Typeface; we resolve JetBrains Mono once
 * via `remember(context)` and reuse the same instance across the two paints.
 */
@Composable
fun SchematicRenderer(
    spec: SchematicSpec,
    highlightedIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val typeface: Typeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
    }
    val accent = CarCopilotColors.Accent
    val muted = CarCopilotColors.TextFaint
    val grid = Color.White.copy(alpha = 0.05f)
    val bezel = Color.White.copy(alpha = 0.10f)

    val pulse by rememberInfiniteTransition(label = "schematic-pulse")
        .animateFloat(
            initialValue = 0.65f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "highlight-alpha",
        )

    // Per-region target alpha — 1f when active in highlightedIds, 0f when
    // not. animateFloatAsState handles the cross-fade between step changes.
    val regionAlphas: Map<String, Float> = spec.regions.keys.associateWith { id ->
        animateFloatAsState(
            targetValue = if (id in highlightedIds) 1f else 0f,
            animationSpec = tween(
                durationMillis = 240,
                easing = LinearOutSlowInEasing,
            ),
            label = "highlight-$id",
        ).value
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(spec.viewport.width / spec.viewport.height),
    ) {
        val sx = size.width / spec.viewport.width
        val sy = size.height / spec.viewport.height

        drawGrid(spec.viewport, sx, sy, grid)
        drawBezel(spec.viewport, sx, sy, bezel)

        spec.shapes.forEach { shape -> drawShape(shape, sx, sy) }

        // Regions with non-trivial alpha are drawn; everything below epsilon is
        // a no-op so a fully-faded region pays nothing.
        val visibleRegions = spec.regions.entries.mapNotNull { (id, region) ->
            val alpha = regionAlphas[id] ?: 0f
            if (alpha > 0.01f) Triple(id, region, alpha) else null
        }
        visibleRegions.forEach { (_, region, regionAlpha) ->
            drawHighlight(region, accent.copy(alpha = pulse * regionAlpha), sx, sy)
        }

        drawIntoCanvas { canvas ->
            val labelPaint = Paint().apply {
                color = accent.toArgb()
                textSize = 10f * sy
                textAlign = Paint.Align.CENTER
                this.typeface = typeface
                isAntiAlias = true
                letterSpacing = 0.06f
                setShadowLayer(2f * sy, 0f, 0f, Color.Black.toArgb())
            }
            val edgePaint = Paint().apply {
                color = muted.toArgb()
                textSize = 8f * sy
                textAlign = Paint.Align.CENTER
                this.typeface = typeface
                isAntiAlias = true
                letterSpacing = 0.12f
            }
            spec.edgeLabels.forEach { label ->
                canvas.nativeCanvas.drawText(
                    label.text,
                    label.anchor.x * sx,
                    label.anchor.y * sy,
                    edgePaint,
                )
            }
            // Per-region label alpha tracks the same fade as the highlight
            // overlay — text and shape rise and fall together. Paint.alpha is
            // a final multiplier over both the text fill and its shadow, so
            // setting it here is enough; no need to rebuild the paint.
            visibleRegions.forEach { (_, region, regionAlpha) ->
                labelPaint.alpha =
                    ((pulse * regionAlpha) * 255f).toInt().coerceIn(0, 255)
                canvas.nativeCanvas.drawText(
                    region.label,
                    region.labelAnchor.x * sx,
                    region.labelAnchor.y * sy,
                    labelPaint,
                )
            }
        }
    }
}

private fun DrawScope.drawGrid(viewport: Size, sx: Float, sy: Float, color: Color) {
    if (viewport.width < 100f || viewport.height < 100f) return
    val step = 12f
    val strokeWidth = 0.5f * sx
    var x = step
    while (x < viewport.width) {
        drawLine(
            color = color,
            start = Offset(x * sx, 0f),
            end = Offset(x * sx, viewport.height * sy),
            strokeWidth = strokeWidth,
        )
        x += step
    }
    var y = step
    while (y < viewport.height) {
        drawLine(
            color = color,
            start = Offset(0f, y * sy),
            end = Offset(viewport.width * sx, y * sy),
            strokeWidth = strokeWidth,
        )
        y += step
    }
}

private fun DrawScope.drawBezel(viewport: Size, sx: Float, sy: Float, color: Color) {
    val inset = 1.5f
    drawRect(
        color = color,
        topLeft = Offset(inset * sx, inset * sy),
        size = Size(
            (viewport.width - 2 * inset) * sx,
            (viewport.height - 2 * inset) * sy,
        ),
        style = Stroke(width = 1f * sx),
    )
}

private fun DrawScope.drawShape(shape: SchematicShape, sx: Float, sy: Float) {
    when (shape) {
        is SchematicShape.RoundedRect -> {
            val topLeft = Offset(shape.rect.left * sx, shape.rect.top * sy)
            val sz = Size(shape.rect.width * sx, shape.rect.height * sy)
            val corner = CornerRadius(shape.cornerRadius * sx, shape.cornerRadius * sy)
            shape.fill?.let { fill ->
                drawRoundRect(color = fill, topLeft = topLeft, size = sz, cornerRadius = corner)
            }
            shape.stroke?.let { stroke ->
                drawRoundRect(
                    color = stroke.color,
                    topLeft = topLeft,
                    size = sz,
                    cornerRadius = corner,
                    style = Stroke(width = stroke.widthPx * sx),
                )
            }
        }
        is SchematicShape.Line -> {
            drawLine(
                color = shape.stroke.color,
                start = Offset(shape.from.x * sx, shape.from.y * sy),
                end = Offset(shape.to.x * sx, shape.to.y * sy),
                strokeWidth = shape.stroke.widthPx * sx,
                cap = if (shape.stroke.rounded) StrokeCap.Round else StrokeCap.Butt,
            )
        }
        is SchematicShape.Path -> {
            val path = composePathFrom(shape.commands, sx, sy)
            shape.fill?.let { drawPath(path = path, color = it) }
            shape.stroke?.let { stroke ->
                drawPath(
                    path = path,
                    color = stroke.color,
                    style = Stroke(
                        width = stroke.widthPx * sx,
                        cap = if (stroke.rounded) StrokeCap.Round else StrokeCap.Butt,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
        is SchematicShape.Circle -> {
            val center = Offset(shape.center.x * sx, shape.center.y * sy)
            val radius = shape.radius * sx
            shape.fill?.let { drawCircle(color = it, radius = radius, center = center) }
            shape.stroke?.let { stroke ->
                drawCircle(
                    color = stroke.color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = stroke.widthPx * sx),
                )
            }
        }
    }
}

private fun composePathFrom(commands: List<PathCommand>, sx: Float, sy: Float): ComposePath {
    val path = ComposePath()
    commands.forEach { cmd ->
        when (cmd) {
            is PathCommand.MoveTo -> path.moveTo(cmd.to.x * sx, cmd.to.y * sy)
            is PathCommand.LineTo -> path.lineTo(cmd.to.x * sx, cmd.to.y * sy)
            is PathCommand.QuadTo -> path.quadraticTo(
                cmd.control.x * sx, cmd.control.y * sy,
                cmd.to.x * sx, cmd.to.y * sy,
            )
            PathCommand.Close -> path.close()
        }
    }
    return path
}

private fun DrawScope.drawHighlight(
    region: HighlightRegion,
    pulsingAccent: Color,
    sx: Float,
    sy: Float,
) {
    when (region.style) {
        HighlightStyle.AccentFill -> {
            drawShape(region.outline.withAccentFill(pulsingAccent), sx, sy)
        }
        HighlightStyle.AccentRing -> {
            drawShape(region.outline.withAccentStroke(pulsingAccent, widthPx = 1.5f), sx, sy)
        }
        HighlightStyle.AccentDot -> {
            // Solid dot inside a ghost ring — the same layered look the original
            // EngineDiagram used for the spark plug. The ring telegraphs depth,
            // the dot is the actual target.
            (region.outline as? SchematicShape.Circle)?.let { c ->
                drawCircle(
                    color = pulsingAccent,
                    radius = (c.radius + 4.5f) * sx,
                    center = Offset(c.center.x * sx, c.center.y * sy),
                    style = Stroke(width = 1f * sx),
                )
                drawCircle(
                    color = pulsingAccent,
                    radius = c.radius * sx,
                    center = Offset(c.center.x * sx, c.center.y * sy),
                )
            } ?: drawShape(region.outline.withAccentFill(pulsingAccent), sx, sy)
        }
    }
    region.arrow?.let { arrow ->
        drawLine(
            color = pulsingAccent,
            start = Offset(arrow.from.x * sx, arrow.from.y * sy),
            end = Offset(arrow.to.x * sx, arrow.to.y * sy),
            strokeWidth = 1.5f * sx,
        )
        val head = ComposePath().apply {
            moveTo((arrow.to.x - arrow.headSize) * sx, (arrow.to.y - arrow.headSize) * sy)
            lineTo(arrow.to.x * sx, (arrow.to.y + arrow.headSize) * sy)
            lineTo((arrow.to.x + arrow.headSize) * sx, (arrow.to.y - arrow.headSize) * sy)
            close()
        }
        drawPath(path = head, color = pulsingAccent)
    }
}

private fun SchematicShape.withAccentFill(accent: Color): SchematicShape = when (this) {
    is SchematicShape.RoundedRect -> copy(fill = accent, stroke = stroke?.copy(color = accent))
    is SchematicShape.Circle -> copy(fill = accent, stroke = stroke?.copy(color = accent))
    is SchematicShape.Path -> copy(fill = accent, stroke = stroke?.copy(color = accent))
    is SchematicShape.Line -> copy(stroke = stroke.copy(color = accent))
}

private fun SchematicShape.withAccentStroke(accent: Color, widthPx: Float): SchematicShape = when (this) {
    is SchematicShape.RoundedRect -> copy(stroke = SchematicStroke(accent, widthPx), fill = null)
    is SchematicShape.Circle -> copy(stroke = SchematicStroke(accent, widthPx), fill = null)
    is SchematicShape.Path -> copy(stroke = SchematicStroke(accent, widthPx), fill = null)
    is SchematicShape.Line -> copy(stroke = SchematicStroke(accent, widthPx))
}
