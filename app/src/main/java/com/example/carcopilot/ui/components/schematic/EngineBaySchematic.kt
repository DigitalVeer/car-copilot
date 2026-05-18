package com.example.carcopilot.ui.components.schematic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Engine-bay schematic — used by P0301 (cylinder misfire) and other ignition
 * codes. Coordinates lifted verbatim from the original hand-coded
 * `EngineDiagram.kt` so P0301 renders the same shape after the refactor.
 *
 * Viewport is 280×180. Origin is top-left.
 *
 * Layout:
 *   - Engine block outline + valve cover behind four coil rectangles
 *   - Coils 1..4 across the top at x = 56, 100, 144, 188 (each 32 wide)
 *   - Wire connector stubs above each coil with a connector dot
 *   - Left/right cooling hoses arcing off the block
 *   - Battery silhouette in the bottom-left with two terminals
 *   - Edge labels "← FRONT" and "CABIN →" at the bottom
 *
 * Highlight targets:
 *   - COIL_1..COIL_4 — accent-fill the coil rectangle + top arrow + label
 *   - SPARK_PLUG — dot inside cylinder 1's coil well (always cylinder 1
 *     in the P0301 demo); deliberately smaller so a COIL_1 → SPARK_PLUG
 *     transition reads as the focus moving deeper, not standing still
 *   - BATTERY_NEG — accent the negative terminal of the battery
 */

private val COIL_XS = floatArrayOf(56f, 100f, 144f, 188f)
private const val COIL_W = 32f
private const val COIL_H = 38f
private const val COIL_Y = 40f

private val EngineFrameStroke = SchematicStroke(Color(0xFF3A3A37), widthPx = 1.5f)
private val ValveCoverStroke = SchematicStroke(Color(0xFF4A4A47), widthPx = 1f)
private val CoilStroke = SchematicStroke(Color(0xFF4A4A47), widthPx = 1.5f)
private val ValveCoverFill = Color(0xFF1A1A18)

private val MutedTerminal = Color(0xFF6E6E73) // matches CarCopilotColors.TextFaint

val EngineBaySchematic: SchematicSpec = SchematicSpec(
    viewport = Size(280f, 180f),
    shapes = buildList {
        // Engine block outline (was EngineDiagram.kt:94)
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 30f, top = 55f, right = 250f, bottom = 150f),
                cornerRadius = 6f,
                stroke = EngineFrameStroke,
            )
        )
        // Valve cover — fill then stroke (was 96-97)
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 42f, top = 70f, right = 238f, bottom = 110f),
                cornerRadius = 3f,
                fill = ValveCoverFill,
                stroke = ValveCoverStroke,
            )
        )
        // Four coils + wire stubs + connector dots (was 99-124)
        COIL_XS.forEach { x ->
            add(
                SchematicShape.RoundedRect(
                    rect = Rect(left = x, top = COIL_Y, right = x + COIL_W, bottom = COIL_Y + COIL_H),
                    cornerRadius = 3f,
                    stroke = CoilStroke,
                )
            )
            add(
                SchematicShape.Line(
                    from = Offset(x + 16f, 33f),
                    to = Offset(x + 16f, 40f),
                    stroke = CoilStroke,
                )
            )
            add(
                SchematicShape.Circle(
                    center = Offset(x + 16f, 32f),
                    radius = 3f,
                    stroke = SchematicStroke(CoilStroke.color, widthPx = 1f),
                )
            )
        }
        // Left hose (was 126-135)
        add(
            SchematicShape.Path(
                commands = listOf(
                    PathCommand.MoveTo(Offset(30f, 90f)),
                    PathCommand.QuadTo(Offset(18f, 90f), Offset(18f, 110f)),
                    PathCommand.LineTo(Offset(18f, 145f)),
                ),
                stroke = SchematicStroke(EngineFrameStroke.color, widthPx = 1.5f, rounded = true),
            )
        )
        // Right hose (was 136-145)
        add(
            SchematicShape.Path(
                commands = listOf(
                    PathCommand.MoveTo(Offset(250f, 100f)),
                    PathCommand.QuadTo(Offset(262f, 100f), Offset(262f, 120f)),
                    PathCommand.LineTo(Offset(262f, 145f)),
                ),
                stroke = SchematicStroke(EngineFrameStroke.color, widthPx = 1.5f, rounded = true),
            )
        )
        // Battery body + terminals (was drawBattery 262-291 at x=60, y=120, w=36, h=22)
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 60f, top = 120f, right = 96f, bottom = 142f),
                cornerRadius = 2f,
                stroke = SchematicStroke(EngineFrameStroke.color, widthPx = 1f),
            )
        )
        // Positive terminal — sits at x = 60 + 36 * 0.66 ≈ 83.76, w=6, h=4, top y = 116
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 83.76f, top = 116f, right = 89.76f, bottom = 120f),
                cornerRadius = 1f,
                fill = MutedTerminal,
            )
        )
        // Negative terminal — sits at x = 60 + 36 * 0.18 ≈ 66.48 (drawn under base art so the
        // highlighted version draws on top via the BATTERY_NEG region)
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 66.48f, top = 116f, right = 72.48f, bottom = 120f),
                cornerRadius = 1f,
                fill = MutedTerminal,
            )
        )
    },
    regions = buildMap {
        COIL_XS.forEachIndexed { idx, x ->
            val cylinder = idx + 1
            put(
                "COIL_$cylinder",
                HighlightRegion(
                    outline = SchematicShape.RoundedRect(
                        rect = Rect(
                            left = x, top = COIL_Y,
                            right = x + COIL_W, bottom = COIL_Y + COIL_H,
                        ),
                        cornerRadius = 3f,
                        // The renderer's withAccentFill swaps fill+stroke to accent;
                        // stroke seed kept here so the shape's outline keeps width 1.5
                        stroke = SchematicStroke(Color.Transparent, widthPx = 1.5f),
                    ),
                    label = "COIL $cylinder",
                    labelAnchor = Offset(x + 16f, 11f),
                    arrow = ArrowSpec(
                        from = Offset(x + 16f, 15f),
                        to = Offset(x + 16f, 32f),
                    ),
                ),
            )
        }
        put(
            "SPARK_PLUG",
            HighlightRegion(
                // Mid-coil vertically (coil spans y=40..78 → center y=59)
                outline = SchematicShape.Circle(
                    center = Offset(COIL_XS[0] + 16f, 59f),
                    radius = 4.5f,
                ),
                label = "PLUG",
                labelAnchor = Offset(COIL_XS[0] + 16f, 92f),
                style = HighlightStyle.AccentDot,
            ),
        )
        put(
            "BATTERY_NEG",
            HighlightRegion(
                outline = SchematicShape.RoundedRect(
                    rect = Rect(left = 66.48f, top = 116f, right = 72.48f, bottom = 120f),
                    cornerRadius = 1f,
                ),
                label = "(−)",
                labelAnchor = Offset(78f, 115f),
            ),
        )
    },
    edgeLabels = listOf(
        EdgeLabel("← FRONT", Offset(130f, 170f)),
        EdgeLabel("CABIN →", Offset(220f, 170f)),
    ),
)
