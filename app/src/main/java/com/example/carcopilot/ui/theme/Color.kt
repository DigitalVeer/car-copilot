package com.example.carcopilot.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Light theme tokens — Apple HIG-influenced palette with indigo primary.
 *
 * The token *names* are preserved from the prior dark theme so composables
 * don't need to be retyped, but the values flip wholesale: cool-neutral
 * surfaces, near-black text (never pure black), indigo as the single primary
 * action color, and two-tone semantic colors (a fill variant for backgrounds
 * and a darker inline variant for text on white).
 *
 * Two-tone rule: never use a Fill color for body text on white. Use the
 * Inline counterpart instead. Fills are for buttons, bars, dots, status
 * pills' background. Inlines are for status words inside paragraphs of
 * body copy, where the same hue would otherwise render washed out or,
 * for red, aggressively saturated.
 *
 * Three-color semantic system: the original dark theme used amber as
 * "warning" alongside red severe and green healthy. On the light theme,
 * indigo replaced amber for primary actions, and after on-device review
 * the residual amber for inline warning emphasis was collapsed too — it
 * read as off-key brownish-yellow against the cool palette, and the
 * single primary hue carries inline emphasis equally well. The system
 * is now indigo (primary + inline emphasis), red (severe), green
 * (healthy). No amber anywhere.
 */
object CarCopilotColors {
    // ── Surface — cool neutrals ─────────────────────────────────────
    val PhoneBg = Color(0xFFF5F5F7)        // iOS system gray 6 — phone interior
    val PhoneCard = Color(0xFFFFFFFF)      // elevated card / white surface
    val PhoneCardSoft = Color(0xFFF0F0F2)  // chip outer surface, inset tile bg

    // ── Hairlines & dividers — barely-there separators ──────────────
    val Line = Color(0x14000000)           // rgba(0,0,0,0.08) — primary hairline
    val LineBright = Color(0x1F000000)     // rgba(0,0,0,0.12) — ghost-button border
    val Divider = Color(0x0F000000)        // rgba(0,0,0,0.06) — interior divider

    // ── Text — near-black, never pure black ─────────────────────────
    val Text = Color(0xFF1D1D1F)           // Apple body
    val TextMute = Color(0xFF6E6E73)       // Apple secondary
    val TextFaint = Color(0xFF86868B)      // Apple tertiary — timestamps, hints
    val TextGhost = Color(0xFFAEAEB2)      // Apple quaternary — very subtle
    val TitleBright = Color(0xFF1D1D1F)    // titles emphasize via weight, not color
    val MetaBold = Color(0xFF1D1D1F)       // bold meta — same as primary on white

    // ── Primary accent — indigo, two-tone ───────────────────────────
    val Accent = Color(0xFF6366F1)         // indigo-500 — FILL: buttons, bars, dots
    val AccentInline = Color(0xFF4F46E5)   // indigo-600 — INLINE text emphasis on white
    val AccentSoft = Color(0xFFEEF0FE)     // indigo ~10% — soft pill / chip bg
    val AccentPressed = Color(0xFF4338CA)  // indigo-700 — pressed state
    val AccentDeep = Color(0xFFFFFFFF)     // text on Accent fill — white

    // ── Severe / danger — iOS red, two-tone ─────────────────────────
    val Severe = Color(0xFFFF3B30)         // iOS system red — FILL
    val SevereInline = Color(0xFFC41E1E)   // INLINE on white — readable, not aggressive
    val SevereSoft = Color(0xFFFFE5E3)     // soft red pill bg

    // ── Healthy / success — iOS green, two-tone ─────────────────────
    val Healthy = Color(0xFF34C759)        // iOS system green — FILL
    val HealthyInline = Color(0xFF1D8038)  // INLINE on white
    val HealthySoft = Color(0xFFE3F8E8)    // soft green pill bg

    // ── Evidence DTC pill — soft indigo on white ────────────────────
    val EvidenceDtcBg = AccentSoft
    val EvidenceDtcBorder = Color(0x336366F1)  // ~20% indigo

    // ── Schematic surface — intentional dark inset ──────────────────
    // The engine-diagram surface inside the walkthrough card. Intentionally
    // foreign on the light theme — reads as an "instrument view" cue,
    // analogous to Apple Health's dark sleep-graph zone in light mode.
    val SchematicSurface = Color(0xFF050505)
}
