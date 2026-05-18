package com.example.carcopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.example.carcopilot.ui.theme.CarCopilotColors

/**
 * Build a colored AnnotatedString from prompt-emitted marker syntax:
 *
 *   [Y]…[/Y]   indigo emphasis  (primary inline emphasis — formerly amber)
 *   [R]…[/R]   severe emphasis  (dark red, never the saturated fill)
 *   [G]…[/G]   healthy emphasis (dark green)
 *
 * Marker spans are bolded (Medium weight) inside the emphasis color. If a
 * span never closes the bracket characters are appended as plain text, so
 * mid-stream partial buffers degrade gracefully.
 */
fun buildColoredString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("[Y]", i) -> {
                    val endIdx = text.indexOf("[/Y]", i)
                    if (endIdx != -1) {
                        val content = text.substring(i + 3, endIdx)
                        pushStyle(SpanStyle(
                            color = CarCopilotColors.AccentInline,
                            fontWeight = FontWeight.Medium,
                        ))
                        append(content)
                        pop()
                        i = endIdx + 4
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("[R]", i) -> {
                    val endIdx = text.indexOf("[/R]", i)
                    if (endIdx != -1) {
                        val content = text.substring(i + 3, endIdx)
                        pushStyle(SpanStyle(
                            color = CarCopilotColors.SevereInline,
                            fontWeight = FontWeight.Medium,
                        ))
                        append(content)
                        pop()
                        i = endIdx + 4
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("[G]", i) -> {
                    val endIdx = text.indexOf("[/G]", i)
                    if (endIdx != -1) {
                        val content = text.substring(i + 3, endIdx)
                        pushStyle(SpanStyle(
                            color = CarCopilotColors.HealthyInline,
                            fontWeight = FontWeight.Medium,
                        ))
                        append(content)
                        pop()
                        i = endIdx + 4
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

/**
 * Parsed structural block within an AI body string. A line that starts
 * with `- ` (or `• `) becomes a Bullet; everything else becomes a
 * Paragraph. Blank lines are dropped at parse time.
 */
private sealed interface BodyBlock {
    val text: String
    data class Paragraph(override val text: String) : BodyBlock
    data class Bullet(override val text: String) : BodyBlock
}

private fun parseBodyBlocks(text: String): List<BodyBlock> {
    val out = mutableListOf<BodyBlock>()
    val paragraphBuf = StringBuilder()
    fun flushParagraph() {
        val flushed = paragraphBuf.toString().trim()
        if (flushed.isNotEmpty()) out += BodyBlock.Paragraph(flushed)
        paragraphBuf.clear()
    }
    for (raw in text.split("\n")) {
        val trimmed = raw.trimStart()
        when {
            trimmed.startsWith("- ") -> {
                flushParagraph()
                out += BodyBlock.Bullet(trimmed.removePrefix("- ").trim())
            }
            trimmed.startsWith("• ") -> {
                flushParagraph()
                out += BodyBlock.Bullet(trimmed.removePrefix("• ").trim())
            }
            trimmed.isBlank() -> {
                flushParagraph()
            }
            else -> {
                if (paragraphBuf.isNotEmpty()) paragraphBuf.append(' ')
                paragraphBuf.append(trimmed)
            }
        }
    }
    flushParagraph()
    return out
}

/**
 * Renders body text with two layers of structure:
 *
 *   - [Y]/[R]/[G] inline emphasis (handled by [buildColoredString]).
 *   - Bullet syntax: any line starting with `- ` or `• ` becomes a row
 *     with a small indigo dot beside the content. Mixed bullets + prose
 *     within the same body string render as a column of blocks. A body
 *     with no bullet markers stays a single paragraph Text, so callers
 *     that don't use the bullet syntax get the same output they did
 *     before structured rendering was added.
 *
 * Both layers tolerate partial input (mid-stream buffers, unclosed
 * marker spans) — they never throw, they just leave the half-formed
 * characters as plain text until the rest arrives.
 */
@Composable
fun ColoredText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = parseBodyBlocks(text)
    val hasBullets = blocks.any { it is BodyBlock.Bullet }
    if (!hasBullets) {
        // Pure prose — preserve the previous single-Text rendering.
        Text(
            text = buildColoredString(text),
            style = style,
            color = color,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (block in blocks) when (block) {
            is BodyBlock.Paragraph -> Text(
                text = buildColoredString(block.text),
                style = style,
                color = color,
            )
            is BodyBlock.Bullet -> BulletRow(text = block.text, style = style, color = color)
        }
    }
}

/**
 * A single bulleted body line: a 6dp indigo dot followed by the colored
 * line text. Dot top-padding lines it up with the first row of body type
 * (approximated for the AiBody 15sp / 24sp leading; close enough that
 * the dot reads as anchored to the line, not floating).
 */
@Composable
private fun BulletRow(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 9.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(CarCopilotColors.AccentInline),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = buildColoredString(text),
            style = style,
            color = color,
        )
    }
}
