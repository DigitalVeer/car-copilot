package com.example.carcopilot.model

/**
 * Output of [com.example.carcopilot.data.RulesEngine.classify].
 *
 * Captures the engine's verdict on the primary DTC, the confidence it has
 * in that verdict (shaped by corroborating live readings), and the list of
 * human-readable signals that led there. Surfaced in prompts so Gemma can
 * reference real evidence rather than generating plausible-sounding guesses.
 */
data class Classification(
    val primaryDtcCode: String,
    val severity: Severity,
    val route: Route,
    val confidence: Confidence,
    val likelyCause: String,
    val supportingSignals: List<String>,
)

enum class Confidence { LOW, MEDIUM, HIGH }
