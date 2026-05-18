package com.example.carcopilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.R

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

object CarCopilotTypography {
    val Brand = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.18.em,
    )

    val VehicleSubtitle = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.04.em,
    )

    val AiLabel = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.16.em,
    )

    /**
     * Heading style for the AI strip's label slot. Light-theme upgrade:
     * what used to be a tiny uppercase mono accent label is now a proper
     * Geist sentence-case heading so the strip carries real prominence
     * on white. Replaces [AiLabel] inside [AnimatedAIStrip]; AiLabel is
     * kept for surfaces that still want the small-mono accent style.
     */
    val AiHeading = TextStyle(
        fontFamily = Geist,
        fontSize = 19.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    )

    val AiBody = TextStyle(
        fontFamily = Geist,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    )

    val CardTitle = TextStyle(
        fontFamily = Geist,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 23.4.sp,
    )

    val CardSubtitle = TextStyle(
        fontFamily = Geist,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.7.sp,
    )

    val CardMeta = TextStyle(
        fontFamily = Geist,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 19.5.sp,
    )

    val CtaButton = TextStyle(
        fontFamily = Geist,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )

    val SectionLabel = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.14.em,
    )

    val EvidenceRowKey = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.04.em,
    )

    val EvidenceRowValue = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )

    val DtcCode = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.04.em,
    )

    val DtcDescription = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )

    val TabLabel = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.06.em,
    )

    val EvidenceSectionHeading = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.16.em,
    )

    val AlsoRow = TextStyle(
        fontFamily = Geist,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.sp,
    )
}

val Typography = Typography(
    bodyLarge = CarCopilotTypography.AiBody,
    bodyMedium = CarCopilotTypography.CardSubtitle,
    bodySmall = CarCopilotTypography.CardMeta,
    titleLarge = CarCopilotTypography.CardTitle,
    labelLarge = CarCopilotTypography.CtaButton,
    labelMedium = CarCopilotTypography.SectionLabel,
    labelSmall = CarCopilotTypography.TabLabel,
)
