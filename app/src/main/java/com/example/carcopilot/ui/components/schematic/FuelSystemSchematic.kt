package com.example.carcopilot.ui.components.schematic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Diesel common-rail fuel-system schematic — used by P0087 (fuel rail
 * pressure too low). Viewport 280×180; origin top-left.
 *
 * Flow, left to right:
 *   TANK → FILTER → PRIMER BULB → HP PUMP → RAIL → injectors → head
 *
 * Highlight targets:
 *   - FUEL_FILTER — the canister; primary focus of Phases 1 & 3
 *   - PRIMER_BULB — bulb on the line between filter and pump; Phase 4
 *   - FUEL_RAIL — the horizontal rail bar across the top; verification step
 *   - BATTERY_NEG — battery in the bottom-left, shared with Phase 1
 *     "disconnect the negative battery terminal" instruction
 */

private val FrameStroke = SchematicStroke(Color(0xFF3A3A37), widthPx = 1.5f)
private val TankFill = Color(0xFF1A1A18)
private val TankStroke = SchematicStroke(Color(0xFF4A4A47), widthPx = 1f)
private val LineStroke = SchematicStroke(Color(0xFF4A4A47), widthPx = 1.2f, rounded = true)
private val PartStroke = SchematicStroke(Color(0xFF4A4A47), widthPx = 1.2f)
private val MutedTerminal = Color(0xFF6E6E73)

private val FILTER_RECT = Rect(left = 70f, top = 90f, right = 92f, bottom = 122f)
private const val PRIMER_CX = 112f
private const val PRIMER_CY = 95f
private const val PRIMER_R = 5.5f
private val PUMP_RECT = Rect(left = 130f, top = 85f, right = 160f, bottom = 115f)
private val RAIL_RECT = Rect(left = 90f, top = 40f, right = 240f, bottom = 50f)
private val HEAD_RECT = Rect(left = 90f, top = 60f, right = 240f, bottom = 78f)

val FuelSystemSchematic: SchematicSpec = SchematicSpec(
    viewport = Size(280f, 180f),
    shapes = buildList {
        // Tank — left side, a tall pillow-shape
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 20f, top = 95f, right = 60f, bottom = 135f),
                cornerRadius = 4f,
                fill = TankFill,
                stroke = TankStroke,
            )
        )
        // Tank-to-filter feed line
        add(
            SchematicShape.Line(
                from = Offset(60f, 106f),
                to = Offset(70f, 106f),
                stroke = LineStroke,
            )
        )
        // Filter canister
        add(
            SchematicShape.RoundedRect(
                rect = FILTER_RECT,
                cornerRadius = 3f,
                fill = TankFill,
                stroke = PartStroke,
            )
        )
        // Filter inlet/outlet stubs (top and bottom) — sells "two fuel lines" cue
        add(
            SchematicShape.Line(
                from = Offset(81f, 86f),
                to = Offset(81f, 90f),
                stroke = LineStroke,
            )
        )
        add(
            SchematicShape.Line(
                from = Offset(81f, 122f),
                to = Offset(81f, 126f),
                stroke = LineStroke,
            )
        )
        // Filter → primer → pump feed line (goes up over the primer bulb)
        add(
            SchematicShape.Path(
                commands = listOf(
                    PathCommand.MoveTo(Offset(92f, 100f)),
                    PathCommand.LineTo(Offset(102f, 100f)),
                    PathCommand.QuadTo(Offset(112f, 100f), Offset(112f, 95f)),
                ),
                stroke = LineStroke,
            )
        )
        add(
            SchematicShape.Path(
                commands = listOf(
                    PathCommand.MoveTo(Offset(112f, 95f)),
                    PathCommand.QuadTo(Offset(112f, 100f), Offset(122f, 100f)),
                    PathCommand.LineTo(Offset(130f, 100f)),
                ),
                stroke = LineStroke,
            )
        )
        // Primer bulb (base art — empty circle; the highlight overrides)
        add(
            SchematicShape.Circle(
                center = Offset(PRIMER_CX, PRIMER_CY),
                radius = PRIMER_R,
                fill = TankFill,
                stroke = PartStroke,
            )
        )
        // HP pump (front-of-engine box)
        add(
            SchematicShape.RoundedRect(
                rect = PUMP_RECT,
                cornerRadius = 3f,
                fill = TankFill,
                stroke = PartStroke,
            )
        )
        // Pump → rail stub (goes up)
        add(
            SchematicShape.Line(
                from = Offset(145f, 85f),
                to = Offset(145f, 50f),
                stroke = LineStroke,
            )
        )
        // Fuel rail across the top (the part that holds pressure)
        add(
            SchematicShape.RoundedRect(
                rect = RAIL_RECT,
                cornerRadius = 3f,
                fill = TankFill,
                stroke = PartStroke,
            )
        )
        // Cylinder head outline below the rail
        add(
            SchematicShape.RoundedRect(
                rect = HEAD_RECT,
                cornerRadius = 4f,
                stroke = FrameStroke,
            )
        )
        // Four injector stubs from rail → head
        listOf(110f, 145f, 180f, 215f).forEach { x ->
            add(
                SchematicShape.Line(
                    from = Offset(x, 50f),
                    to = Offset(x, 60f),
                    stroke = LineStroke,
                )
            )
            add(
                SchematicShape.Circle(
                    center = Offset(x, 55f),
                    radius = 1.8f,
                    fill = MutedTerminal,
                )
            )
        }
        // Battery body + terminals (mirrors EngineBaySchematic for visual continuity)
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 30f, top = 150f, right = 66f, bottom = 172f),
                cornerRadius = 2f,
                stroke = SchematicStroke(FrameStroke.color, widthPx = 1f),
            )
        )
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 53.76f, top = 146f, right = 59.76f, bottom = 150f),
                cornerRadius = 1f,
                fill = MutedTerminal,
            )
        )
        add(
            SchematicShape.RoundedRect(
                rect = Rect(left = 36.48f, top = 146f, right = 42.48f, bottom = 150f),
                cornerRadius = 1f,
                fill = MutedTerminal,
            )
        )
    },
    regions = mapOf(
        "FUEL_FILTER" to HighlightRegion(
            outline = SchematicShape.RoundedRect(
                rect = FILTER_RECT,
                cornerRadius = 3f,
                stroke = SchematicStroke(Color.Transparent, widthPx = 1.5f),
            ),
            label = "FUEL FILTER",
            labelAnchor = Offset(81f, 138f),
            arrow = ArrowSpec(
                from = Offset(81f, 138f - 14f),
                to = Offset(81f, FILTER_RECT.bottom + 2f),
            ),
        ),
        "PRIMER_BULB" to HighlightRegion(
            outline = SchematicShape.Circle(
                center = Offset(PRIMER_CX, PRIMER_CY),
                radius = PRIMER_R,
            ),
            label = "PRIME",
            labelAnchor = Offset(PRIMER_CX, PRIMER_CY - 11f),
            style = HighlightStyle.AccentDot,
        ),
        "FUEL_RAIL" to HighlightRegion(
            outline = SchematicShape.RoundedRect(
                rect = RAIL_RECT,
                cornerRadius = 3f,
                stroke = SchematicStroke(Color.Transparent, widthPx = 1.5f),
            ),
            label = "34.5 MPa RAIL",
            labelAnchor = Offset(165f, 32f),
        ),
        "BATTERY_NEG" to HighlightRegion(
            outline = SchematicShape.RoundedRect(
                rect = Rect(left = 36.48f, top = 146f, right = 42.48f, bottom = 150f),
                cornerRadius = 1f,
            ),
            label = "(−)",
            labelAnchor = Offset(50f, 145f),
        ),
    ),
    edgeLabels = listOf(
        EdgeLabel("TANK", Offset(40f, 90f)),
        EdgeLabel("HP PUMP", Offset(145f, 128f)),
    ),
)
