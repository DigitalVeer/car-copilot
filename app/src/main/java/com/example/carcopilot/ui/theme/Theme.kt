package com.example.carcopilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CarCopilotColorScheme = darkColorScheme(
    background = CarCopilotColors.PhoneBg,
    surface = CarCopilotColors.PhoneCard,
    surfaceVariant = CarCopilotColors.PhoneCardSoft,
    onBackground = CarCopilotColors.Text,
    onSurface = CarCopilotColors.Text,
    onSurfaceVariant = CarCopilotColors.TextMute,
    primary = CarCopilotColors.Accent,
    onPrimary = CarCopilotColors.AccentDeep,
    secondary = CarCopilotColors.Accent,
    onSecondary = CarCopilotColors.AccentDeep,
    error = CarCopilotColors.Severe,
    onError = CarCopilotColors.AccentDeep,
    outline = CarCopilotColors.LineBright,
    outlineVariant = CarCopilotColors.Line,
)

@Composable
fun CarCopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CarCopilotColorScheme,
        typography = Typography,
        content = content,
    )
}
