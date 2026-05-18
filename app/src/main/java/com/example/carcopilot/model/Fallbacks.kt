package com.example.carcopilot.model

/**
 * Canned synthesis text used when LiteRT-LM errors or returns unparseable
 * output. Must stay in the voice defined in reference/prompts/system.md.
 *
 * Color markers: [Y] for primary inline emphasis (indigo on the light theme;
 * originally amber, retained as a marker name for prompt compatibility),
 * [R] for severe (red), [G] for healthy (green). See ColoredText.kt for
 * the runtime mapping.
 *
 * [fallbackSynthesisFor] and [fallbackGoodNewsFor] select the right entry
 * by primary DTC code, falling back to the misfire text for unknown codes.
 */
const val FALLBACK_SYNTHESIS_MISFIRE: String =
    "Cylinder 1 keeps [Y]misfiring[/Y] — you'll feel it as a stumble at idle. On the EA211 TSI engine in this Jetta, a tired [Y]ignition coil[/Y] is almost always the cause."

const val FALLBACK_GOOD_NEWS_MISFIRE: String =
    "It's a [Y]30-minute fix[/Y] and you can do it yourself. I'll walk you through it."

private val FALLBACK_SYNTHESIS: Map<String, String> = mapOf(
    "P0300" to "Several cylinders are [Y]misfiring at random[/Y] — that points to a fuel-delivery problem rather than individual coils. The engine isn't getting enough fuel into the mix.",
    "P0301" to FALLBACK_SYNTHESIS_MISFIRE,
    "P0302" to "Cylinder 2 keeps [Y]misfiring[/Y] — you'll feel it as a stumble at idle. On older engines this is almost always a worn [Y]ignition coil[/Y] or fouled spark plug.",
    "P0303" to "Cylinder 3 keeps [Y]misfiring[/Y] — you'll feel it as a stumble at idle. On older engines this is almost always a worn [Y]ignition coil[/Y] or fouled spark plug.",
    "P0304" to "Cylinder 4 keeps [Y]misfiring[/Y] — you'll feel it as a stumble at idle. On older engines this is almost always a worn [Y]ignition coil[/Y] or fouled spark plug.",
    "P0101" to "Your [Y]air flow sensor[/Y] is reading inconsistently — the engine can't measure incoming air, so the fuel mix is off. A dirty sensor is the most likely cause on a high-mileage car.",
    "P0171" to "Your engine is [Y]running lean[/Y] — not enough fuel for the air it's pulling in. High fuel-trim numbers mean the ECU has been compensating for a while. A dirty [Y]MAF sensor[/Y] is the usual reason on high-mileage cars.",
    "P0087" to "Your diesel isn't building enough [Y]fuel pressure[/Y] — that's why power feels flat under load. A [Y]clogged fuel filter[/Y] is the right first thing to rule out — cheap and common.",
    "P0118" to "Your coolant temp sensor is reading [R]dangerously hot[/R]. It's either real overheating or a failed sensor — [R]don't drive on it[/R] until you've checked the coolant level.",
    "P0401" to "Your [Y]EGR valve[/Y] isn't flowing enough exhaust back through the engine. On high-mileage cars, carbon buildup in the valve or its passage is the usual culprit.",
    "P0420" to "Your [Y]catalytic converter[/Y] isn't cleaning the exhaust as well as it used to. That's normal wear — the catalyst material degrades over time.",
    "P0507" to "Your engine is [Y]idling too fast[/Y] — usually an air leak somewhere in the intake. Extra air gets in past the MAF and the ECU can't account for it.",
    "P0670" to "The [Y]glow-plug control module[/Y] has a fault. Without pre-heating, the cylinders can't ignite fuel reliably on cold starts.",
    "P0671" to "Cylinder 1's [Y]glow plug[/Y] isn't working. Without it, cold starts will be slow and rough.",
    "P0672" to "Cylinder 2's [Y]glow plug[/Y] isn't working. Cold starts will be slow and rough.",
    "P0673" to "Cylinder 3's [Y]glow plug[/Y] isn't working. Cold starts will be slow and rough.",
    "P0674" to "Cylinder 4's [Y]glow plug[/Y] isn't working. Cold starts will be slow and rough.",
)

private val FALLBACK_GOOD_NEWS: Map<String, String> = mapOf(
    "P0300" to "Once we find the root cause it's fixable. I'll help you work through it.",
    "P0301" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0302" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0303" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0304" to FALLBACK_GOOD_NEWS_MISFIRE,
    "P0101" to "MAF cleaner takes [Y]ten minutes[/Y]. I'll walk you through it.",
    "P0171" to "Cleaning the MAF sensor takes [Y]ten minutes[/Y] and usually solves it. I'll walk you through it.",
    "P0087" to "[Y]Fuel filters are cheap[/Y] and easy to swap. I'll walk you through it.",
    "P0118" to "Once it's cool, a quick coolant check catches most of these. I'll walk you through it.",
    "P0401" to "Cleaning the EGR is a [Y]weekend job[/Y] with basic tools. I'll walk you through it.",
    "P0420" to "It's safe to drive for now, but it'll need attention soon.",
    "P0507" to "Finding the leak takes a few minutes with the right method. I'll walk you through it.",
    "P0670" to "A mechanic can test the module quickly — worth getting sorted before the next cold morning.",
    "P0671" to "[Y]Glow plugs are cheap[/Y] and straightforward to swap. I'll walk you through it.",
    "P0672" to "[Y]Glow plugs are cheap[/Y] and straightforward to swap. I'll walk you through it.",
    "P0673" to "[Y]Glow plugs are cheap[/Y] and straightforward to swap. I'll walk you through it.",
    "P0674" to "[Y]Glow plugs are cheap[/Y] and straightforward to swap. I'll walk you through it.",
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
