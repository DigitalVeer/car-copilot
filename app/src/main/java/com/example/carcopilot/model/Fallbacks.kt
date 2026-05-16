package com.example.carcopilot.model

/**
 * Canned synthesis text used when LiteRT-LM errors or returns unparseable
 * output. Mirrors fallback.py for the misfire entry. Must stay in the voice
 * defined in reference/prompts/system.md.
 */
const val FALLBACK_SYNTHESIS_MISFIRE: String =
    "Cylinder 1 keeps misfiring. On older Corollas, this is almost always a worn ignition coil."

const val FALLBACK_GOOD_NEWS_MISFIRE: String =
    "I can walk you through it."
