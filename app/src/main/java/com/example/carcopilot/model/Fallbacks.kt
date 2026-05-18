package com.example.carcopilot.model

/**
 * Canned synthesis text used when LiteRT-LM errors or returns unparseable
 * output. Mirrors fallback.py for the misfire entry. Must stay in the voice
 * defined in reference/prompts/system.md.
 *
 * Color markers: [Y] for accent (yellow), [R] for severe (red), [G] for healthy (green)
 */
const val FALLBACK_SYNTHESIS_MISFIRE: String =
    "Cylinder 1 keeps [Y]misfiring[/Y]. On older Corollas, this is almost always a worn [Y]ignition coil[/Y]."

const val FALLBACK_GOOD_NEWS_MISFIRE: String =
    "It's a 30-minute fix — I can [Y]walk you through it[/Y]."
