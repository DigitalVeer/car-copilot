package com.example.carcopilot.ui.components.schematic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Declarative description of a walkthrough schematic.
 *
 * The renderer ([SchematicRenderer]) walks [shapes] in order to draw the base
 * art, then overlays a [HighlightRegion] for every id in `highlightedIds` that
 * exists in [regions]. Coordinates are in viewport space; the renderer scales
 * to the actual Canvas size with `aspectRatio(viewport.width / viewport.height)`.
 *
 * Per-DTC layouts live in their own files (see [EngineBaySchematic],
 * [FuelSystemSchematic]) as pure data. Adding a new DTC family means writing
 * a new spec — no Canvas math in the consumer.
 */
data class SchematicSpec(
    val viewport: Size,
    val shapes: List<SchematicShape>,
    val regions: Map<String, HighlightRegion>,
    val edgeLabels: List<EdgeLabel> = emptyList(),
)

/**
 * A single drawn primitive in viewport space. The renderer dispatches on the
 * sealed type. Stroke and fill are optional and independent — a shape can be
 * fill-only (no stroke), stroke-only (no fill), or both.
 */
sealed interface SchematicShape {
    val stroke: SchematicStroke?
    val fill: Color?

    data class RoundedRect(
        val rect: Rect,
        val cornerRadius: Float,
        override val stroke: SchematicStroke? = null,
        override val fill: Color? = null,
    ) : SchematicShape

    data class Line(
        val from: Offset,
        val to: Offset,
        override val stroke: SchematicStroke,
    ) : SchematicShape {
        override val fill: Color? get() = null
    }

    data class Path(
        val commands: List<PathCommand>,
        override val stroke: SchematicStroke? = null,
        override val fill: Color? = null,
    ) : SchematicShape

    data class Circle(
        val center: Offset,
        val radius: Float,
        override val stroke: SchematicStroke? = null,
        override val fill: Color? = null,
    ) : SchematicShape
}

data class SchematicStroke(
    val color: Color,
    val widthPx: Float = 1f,
    val rounded: Boolean = false,
)

sealed interface PathCommand {
    data class MoveTo(val to: Offset) : PathCommand
    data class LineTo(val to: Offset) : PathCommand
    data class QuadTo(val control: Offset, val to: Offset) : PathCommand
    object Close : PathCommand
}

/**
 * Overlay drawn when a target id is highlighted. [outline] is the visible
 * accent shape (a coil rectangle filled, a plug dot, a filter outline).
 * Optional [arrow] adds a top callout that points at the target, like the
 * existing "↑ COIL 1" indicator. [label] is rendered at [labelAnchor] using
 * the schematic's monospace label paint.
 *
 * [style] is informational metadata for renderers that want to alter the
 * visual treatment per region (e.g., a ghost ring around a dot). The current
 * renderer interprets it as: AccentFill = solid fill, AccentRing = stroke
 * only, AccentDot = solid fill plus a wider stroke ghost ring.
 */
data class HighlightRegion(
    val outline: SchematicShape,
    val label: String,
    val labelAnchor: Offset,
    val arrow: ArrowSpec? = null,
    val style: HighlightStyle = HighlightStyle.AccentFill,
)

enum class HighlightStyle { AccentFill, AccentRing, AccentDot }

data class ArrowSpec(
    val from: Offset,
    val to: Offset,
    val headSize: Float = 4f,
)

data class EdgeLabel(val text: String, val anchor: Offset)
