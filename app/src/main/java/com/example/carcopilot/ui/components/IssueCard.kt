package com.example.carcopilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.carcopilot.model.Issue
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
    Text(
        text = buildMetaLine(issue),
        style = CarCopilotTypography.CardMeta,
        color = CarCopilotColors.TextMute,
    )
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
 * Composes "{drivability} · ${cost} · {time} min · {difficulty}" with the
 * cost/time/difficulty fragments rendered weight-500 in MetaBold per spec
 * §5.4 / §5.5.
 */
private fun buildMetaLine(issue: Issue): AnnotatedString {
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
        m.drivability?.let { sep(); append(it.replaceFirstChar { c -> c.uppercaseChar() }) }
        cost?.let { sep(); withStyle(bold) { append(it) } }
        m.timeMinutes?.let { sep(); withStyle(bold) { append("$it min") } }
        m.difficulty?.let { sep(); withStyle(bold) { append(it) } }
    }
}
