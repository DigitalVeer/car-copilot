package com.example.carcopilot.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

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
                            color = CarCopilotColors.Accent,
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
                            color = CarCopilotColors.Severe,
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
                            color = CarCopilotColors.Healthy,
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

@Composable
fun ColoredText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildColoredString(text),
        style = style,
        color = color,
        modifier = modifier,
    )
}
