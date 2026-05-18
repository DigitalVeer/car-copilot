package com.example.carcopilot.model

/**
 * Canned synthesis text used when LiteRT-LM errors or returns unparseable
 * output. Must stay in the voice defined in reference/prompts/system.md.
 *
 * [fallbackSynthesisFor] and [fallbackGoodNewsFor] select the right entry
 * by primary DTC code, falling back to the misfire text for unknown codes.
 */
const val FALLBACK_SYNTHESIS_MISFIRE: String =
    "Cylinder 1 keeps misfiring. On older Corollas, this is almost always a worn ignition coil."

const val FALLBACK_GOOD_NEWS_MISFIRE: String =
    "I can walk you through it."

private val FALLBACK_SYNTHESIS: Map<String, String> = mapOf(
    "P0300" to "Several cylinders are misfiring at random. When multiple cylinders misfire together like this, a fuel delivery problem is more likely than individual coil failures — the engine isn't getting enough fuel.",
    "P0301" to FALLBACK_SYNTHESIS_MISFIRE,
    "P0302" to "Cylinder 2 keeps misfiring. On older engines, this is almost always a worn ignition coil or fouled spark plug.",
    "P0303" to "Cylinder 3 keeps misfiring. On older engines, this is almost always a worn ignition coil or fouled spark plug.",
    "P0304" to "Cylinder 4 keeps misfiring. On older engines, this is almost always a worn ignition coil or fouled spark plug.",
    "P0101" to "Your air flow sensor is giving inconsistent readings. The engine can't measure incoming air accurately, so fuel delivery is off. A dirty sensor is the most likely cause on a high-mileage car.",
    "P0171" to "Your engine isn't getting enough fuel in the mix. High fuel trim readings mean the ECU has been compensating for a while. On high-mileage cars, a dirty MAF sensor is the most common reason.",
    "P0087" to "Your diesel isn't building enough fuel pressure. The rail is reading well below what the engine needs at idle. A clogged fuel filter is the right first thing to rule out — it's cheap and it's common.",
    "P0401" to "Your EGR valve isn't flowing enough exhaust gas back through the engine. On a high-mileage car, carbon buildup in the valve or passage is the usual cause.",
    "P0420" to "Your catalytic converter is no longer cleaning exhaust gases efficiently. This is normal wear on a high-mileage car — the catalyst material inside has degraded over time.",
    "P0507" to "Your engine is idling faster than it should. The most common cause is an air leak somewhere in the intake — extra air gets in past the MAF sensor, and the ECU can't account for it.",
)

private val FALLBACK_GOOD_NEWS: Map<String, String> = mapOf(
    "P0300" to "Once we find the root cause, it's fixable. I can help you work through it.",
    "P0301" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0302" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0303" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0304" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0101" to "MAF cleaner spray takes ten minutes. I can walk you through it.",
    "P0171" to "Spray it clean and we'll have you sorted in ten minutes. I can walk you through it.",
    "P0087" to "Filters are cheap and easy to swap. I can walk you through it.",
    "P0401" to "Cleaning the EGR valve is a weekend job with basic tools. I can walk you through it.",
    "P0420" to "It's safe to drive for now, but it'll need attention soon.",
    "P0507" to "Finding the leak takes a few minutes with the right method. I can walk you through it.",
)

fun fallbackSynthesisFor(code: String?): String =
    FALLBACK_SYNTHESIS[code] ?: FALLBACK_SYNTHESIS_MISFIRE

fun fallbackGoodNewsFor(code: String?): String =
    FALLBACK_GOOD_NEWS[code] ?: FALLBACK_GOOD_NEWS_MISFIRE

/**
 * Builds a data-driven synthesis from [Classification] when Gemma is
 * unavailable. Uses the actual sensor readings captured in
 * [Classification.supportingSignals] so the text is specific to this
 * vehicle's snapshot rather than a generic canned string.
 *
 * Falls back to [fallbackSynthesisFor] when confidence is LOW or there
 * are no supporting signals to reference.
 */
fun synthesizeFromClassification(
    classification: Classification?,
    issue: Issue,
): Pair<String, String?> {
    val code = issue.dtcs.firstOrNull()?.code
    val goodNews = fallbackGoodNewsFor(code)

    if (classification == null ||
        classification.confidence == Confidence.LOW ||
        classification.supportingSignals.isEmpty()
    ) {
        return fallbackSynthesisFor(code) to goodNews
    }

    val synthesis = buildString {
        classification.supportingSignals.take(2).forEach { signal ->
            append(signal)
            append(". ")
        }
        append("That points to ")
        append(classification.likelyCause)
        append(".")
    }

    return synthesis to goodNews
}
