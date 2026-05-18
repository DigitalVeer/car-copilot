package com.example.carcopilot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.Route
import com.example.carcopilot.model.Severity
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/** Home variant — whole card tappable, one primary CTA (visual only). */
@Composable
fun IssueCard(
    issue: Issue,
    ctaLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardFrame(severity = issue.severity, modifier = modifier.clickable(onClick = onClick)) {
        CardCopy(issue)
        Spacer(Modifier.height(20.dp))
        PrimaryCta(label = ctaLabel, severity = issue.severity)
    }
}

/** Issue-page variant — non-clickable card, primary + ghost CTAs. */
@Composable
fun IssueCardWithCTAs(
    issue: Issue,
    primaryLabel: String,
    ghostLabel: String,
    onPrimary: () -> Unit = {},
    onGhost: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CardFrame(severity = issue.severity, modifier = modifier) {
        CardCopy(issue)
        Spacer(Modifier.height(20.dp))
        PrimaryCta(label = primaryLabel, severity = issue.severity, onClick = onPrimary)
        Spacer(Modifier.height(10.dp))
        GhostCta(label = ghostLabel, onClick = onGhost)
    }
}

@Composable
private fun CardFrame(
    severity: Severity,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(14.dp)),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(severity.accentColor()),
            )
            Column(modifier = Modifier.padding(22.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CardCopy(issue: Issue) {
    Text(
        text = issue.title,
        style = CarCopilotTypography.CardTitle,
        color = CarCopilotColors.TitleBright,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = issue.subtitle,
        style = CarCopilotTypography.CardSubtitle,
        color = CarCopilotColors.TextMute,
    )
    Spacer(Modifier.height(20.dp))
    MetaLine(issue = issue)
}

/**
 * The route is the single "what do I do with this?" signal. The full-width
 * "DIY-FRIENDLY" pill above the title was too loud — feedback was that it
 * fought the title for first glance. It now lives inline at the head of
 * the meta line as a small colored chip ahead of "$30–$60 · 20 min", so
 * the user gets route + cost + time in one horizontal sweep and the chip
 * carries enough oomph (color + glyph) to read at a glance without
 * dominating the card. info routes render no chip — they're informational
 * and don't suggest an action.
 *
 * Leading marker is a small Canvas glyph per route so the chip is
 * recognizable at peripheral-vision distance: ✓ for DIY (approval),
 * ↗ for expert (handoff), ! for safety (alert).
 */
@Composable
private fun RouteChip(route: Route) {
    val spec = routeChipSpec(route) ?: return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(spec.bg)
            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        spec.mark(spec.fg)
        Text(
            text = spec.label,
            style = CarCopilotTypography.TabLabel.copy(
                fontSize = 9.5.sp,
                letterSpacing = 0.12.em,
                fontWeight = FontWeight.Medium,
            ),
            color = spec.fg,
        )
    }
}

private data class RouteChipSpec(
    val bg: Color,
    val fg: Color,
    val label: String,
    val mark: @Composable (Color) -> Unit,
)

private fun routeChipSpec(route: Route): RouteChipSpec? = when (route) {
    Route.diy -> RouteChipSpec(
        bg = CarCopilotColors.HealthySoft,
        fg = CarCopilotColors.HealthyInline,
        label = "DIY-FRIENDLY",
        mark = { CheckMark(it) },
    )
    Route.expert -> RouteChipSpec(
        bg = CarCopilotColors.AccentSoft,
        fg = CarCopilotColors.AccentInline,
        label = "SHOP VISIT",
        mark = { ArrowMark(it) },
    )
    Route.safety -> RouteChipSpec(
        bg = CarCopilotColors.SevereSoft,
        fg = CarCopilotColors.SevereInline,
        label = "STOP & CALL",
        mark = { BangMark(it) },
    )
    Route.info -> null
}

@Composable
private fun CheckMark(color: Color) {
    Canvas(modifier = Modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.82f, h * 0.28f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = size.minDimension * 0.22f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun ArrowMark(color: Color) {
    Canvas(modifier = Modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        val line = Path().apply {
            moveTo(w * 0.22f, h * 0.78f)
            lineTo(w * 0.78f, h * 0.22f)
        }
        val head = Path().apply {
            moveTo(w * 0.42f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.58f)
        }
        val stroke = Stroke(
            width = size.minDimension * 0.20f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawPath(path = line, color = color, style = stroke)
        drawPath(path = head, color = color, style = stroke)
    }
}

@Composable
private fun BangMark(color: Color) {
    Canvas(modifier = Modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        val bar = Path().apply {
            moveTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.60f)
        }
        drawPath(
            path = bar,
            color = color,
            style = Stroke(
                width = size.minDimension * 0.24f,
                cap = StrokeCap.Round,
            ),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.11f,
            center = Offset(w * 0.50f, h * 0.82f),
        )
    }
}

@Composable
private fun PrimaryCta(label: String, severity: Severity, onClick: (() -> Unit)? = null) {
    val bg = severity.accentColor()
    val fg = if (severity == Severity.severe) Color.White else CarCopilotColors.AccentDeep
    val mod = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(bg)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = 13.dp, horizontal = 16.dp)
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = CarCopilotTypography.CtaButton,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GhostCta(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CarCopilotColors.LineBright, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CarCopilotTypography.CtaButton,
            color = CarCopilotColors.MetaBold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Meta line: route chip (small pill) + " · " + cost + " · " + time. The
 * route chip leads because it's the most actionable signal — but it sits
 * inline with cost and time at meta-line size, not as a separate banner
 * above the title. Earlier revisions tried both extremes (buried in the
 * text run; full-width pill above the title); this is the middle ground —
 * chip-shaped, colored, but visually proportionate to the supporting
 * cost/time fragments.
 */
@Composable
private fun MetaLine(issue: Issue) {
    val text = buildMetaText(issue)
    val hasChip = routeChipSpec(issue.route) != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasChip) {
            RouteChip(route = issue.route)
            if (text.isNotEmpty()) {
                Text(
                    text = " · ",
                    style = CarCopilotTypography.CardMeta,
                    color = CarCopilotColors.TextMute,
                )
            }
        }
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = CarCopilotTypography.CardMeta,
                color = CarCopilotColors.TextMute,
            )
        }
    }
}

private fun buildMetaText(issue: Issue): AnnotatedString {
    val m = issue.meta
    val cost = when {
        m.costUsdMin != null && m.costUsdMax != null && m.costUsdMin != m.costUsdMax ->
            "\$${m.costUsdMin}–\$${m.costUsdMax}"
        m.costUsdMin != null -> "\$${m.costUsdMin}"
        m.costUsdMax != null -> "\$${m.costUsdMax}"
        else -> null
    }
    val bold = SpanStyle(color = CarCopilotColors.MetaBold, fontWeight = FontWeight.Medium)
    return buildAnnotatedString {
        var first = true
        fun sep() { if (!first) append(" · "); first = false }
        cost?.let { sep(); withStyle(bold) { append(it) } }
        m.timeMinutes?.let { sep(); withStyle(bold) { append("$it min") } }
    }
}
