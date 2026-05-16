# CAR·COPILOT — Phase 6 spec · Visual polish

**Status:** active · **Scope:** restyle the existing Android app to match the HTML mockup · **Prerequisite:** Phase 5 Checkpoint B is committed and working · **Deadline:** before recording

This document supplements `carcopilot_android_spec.md`. The app currently runs end-to-end with on-device Gemma streaming but uses default Material3 surfaces and system fonts. This phase brings the visual fidelity up to the mockup target without touching the functional contract.

The mockup at `reference/carcopilot_mockup_v09.html` is the design source of truth. Read its `<style>` block — that's where the design tokens live as CSS custom properties. Every value in this spec was extracted from there.

---

## 1. What success looks like

When recording starts, the on-device app reads as:
- **Same fonts** as the mockup (Geist for body, JetBrains Mono for labels/codes)
- **Same color palette** — dark background `#0a0a0a`, accent yellow `#f5c518` for the AI strip + CTAs, semantic colors for severity tints
- **Same typography hierarchy** — uppercase tracked mono labels, weighted sans for titles, muted secondary text
- **Same component fidelity** — issue card with left-accent stripe, AI strip with vertical accent border, evidence section that expands cleanly, two-icon bottom tab bar
- **Same animation language** — bouncing typing dots that resolve into streaming text, smooth evidence expand/collapse

It is not pixel-perfect (the mockup is HTML and Compose has its own rendering quirks), but it is unmistakably the same product.

## 2. Source of truth

`reference/carcopilot_mockup_v09.html` — specifically the `<style>` block. Open it, find the `:root` selector at the top of the CSS, and you'll see every color/font/spacing token defined as a custom property. The component classes below it (`.p-ai`, `.p-card`, `.p-cta`, etc.) show how those tokens compose into the visual you see when rendered.

Reference but don't try to import the HTML at runtime — this is a Compose-native app. The HTML is the *design system spec*; Compose is the implementation.

## 3. The functional contract — do NOT change

The polish pass touches visual rendering only. The following are committed and working; do not modify them:

- `GemmaService.kt` — engine init, model staging to filesDir, streaming inference
- `SynthesisState.kt` including `extractSynthesisInProgress(buffer: String)` — the JSON-aware streaming text extractor. **Critical.** Polish must preserve the existing state transitions: Thinking → Streaming(decoded content only) → Ready.
- `CarCopilotApp.onCreate` — the appScope.async init pattern
- `MainActivity` and NavHost routing — home ↔ issue
- The `LaunchedEffect` in `IssueScreen.kt` that collects from `gemma.streamSynthesis(...)` — the collect block stays, only what's *rendered* from the state changes
- Resilience paths to `Ready(fallback, isFallback=true)` — all five paths stay wired
- `DtcTable.kt` content, prompt files in assets, fixture file content

What you ARE allowed to change:
- `ui/theme/*.kt` — colors, typography, theme tokens
- `HomeScreen.kt` — Compose layout, styling
- `IssueScreen.kt` — Compose layout, styling (NOT the LaunchedEffect logic)
- `AnimatedAIStrip.kt` (or whatever it's named) — only visual rendering of the SynthesisState; the state contract is fixed
- Any new component files (e.g., `IssueCard.kt`, `TabBar.kt`, `EvidenceSection.kt`, `ThinkingDots.kt`)
- `res/font/` — add Geist + JetBrains Mono font files
- `res/values/colors.xml` if you use it, or hardcoded colors in Theme.kt
- `AndroidManifest.xml` — only if a theme update requires it

If you find yourself wanting to change anything in the "do NOT change" list, stop and ask.

## 4. Design tokens (extracted from the mockup CSS)

### Colors

Map these into a `CarCopilotColors` object in `ui/theme/Color.kt`. These are the *only* colors the app uses — no Material3 defaults, no generated palettes.

```kotlin
object CarCopilotColors {
    // Surfaces
    val PhoneBg = Color(0xFF0A0A0A)              // --phone-bg
    val PhoneCard = Color(0xFF141414)            // --phone-card
    val PhoneCardSoft = Color(0xFF101010)        // --phone-card-soft

    // Text
    val Text = Color(0xFFEBEBE8)                  // --phone-text
    val TextMute = Color(0xFF8C8C87)              // --phone-text-mute
    val TextFaint = Color(0xFF5A5A55)             // --phone-text-faint
    val TextGhost = Color(0xFF444444)             // --phone-text-ghost

    // Lines
    val Line = Color(0x0FFFFFFF)                  // --phone-line (rgba(255,255,255,0.06))
    val LineBright = Color(0x1FFFFFFF)            // --phone-line-bright (rgba(255,255,255,0.12))

    // Severity / accent (semantic — never use raw values, always reference these)
    val Accent = Color(0xFFF5C518)                // --accent (yellow)
    val AccentDeep = Color(0xFF1A1400)            // --accent-deep (text on yellow buttons)
    val Severe = Color(0xFFE44545)                // --severe (red)
    val Healthy = Color(0xFF4ADE80)               // --healthy (green)

    // Inline highlighted text (when warm body mentions a key term)
    val AccentInline = Accent                     // .p-aitext .y
    val SevereInline = Severe                     // .p-aitext .r
    val HealthyInline = Healthy                   // .p-aitext .g
}
```

### Typography

Two font families. Both are open source and need to be bundled.

**Geist** (sans-serif, primary body):
- Download Geist-Regular.ttf, Geist-Medium.ttf, Geist-SemiBold.ttf from https://github.com/vercel/geist-font (or pull via the Google Fonts CDN equivalent)
- Place in `app/src/main/res/font/` as `geist_regular.ttf`, `geist_medium.ttf`, `geist_semibold.ttf`

**JetBrains Mono** (monospace, labels and codes):
- Download JetBrainsMono-Regular.ttf, JetBrainsMono-Medium.ttf from https://github.com/JetBrains/JetBrainsMono
- Place in `res/font/` as `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf`

Type scale (extract from the mockup CSS, port to Compose `TextStyle`):

| Usage | Font | Size | Weight | Letter spacing | Line height |
|---|---|---|---|---|---|
| Brand mark ("CAR·COPILOT") | JetBrains Mono | 11sp | Medium (500) | 0.18em → 1.98sp | — |
| Vehicle subtitle | JetBrains Mono | 11sp | Regular | 0.04em → 0.44sp | — |
| AI strip label ("Today's drive") | JetBrains Mono | 11sp | Regular | 0.16em → 1.76sp | — |
| AI strip body | Geist | 15sp | Regular | normal | 1.6 |
| Card title | Geist | 18sp | Medium (500) | normal | 1.3 |
| Card subtitle | Geist | 14sp | Regular | normal | 1.55 |
| Card meta line | Geist | 13sp | Regular | normal | 1.5 |
| CTA button | Geist | 14sp | Medium (500) | normal | — |
| Section labels ("Also", "Show raw data for a mechanic") | JetBrains Mono | 11sp | Regular | 0.14em–0.18em → 1.54–1.98sp | — |
| Evidence row key | JetBrains Mono | 12sp | Regular | 0.04em → 0.48sp | — |
| Evidence row value | JetBrains Mono | 12sp | Medium (500) | normal | — |
| DTC code in evidence | JetBrains Mono | 12sp | Medium (500) | 0.04em → 0.48sp | — |
| Tab bar label | JetBrains Mono | 11sp | Regular | 0.06em → 0.66sp | — |

Set these up as a `CarCopilotTypography` object in `ui/theme/Type.kt` and consume via `MaterialTheme.typography` or a custom local. Up to you — but every text style in the app should pull from this typography object, not hardcode size/weight.

### Spacing

Compose uses dp; the mockup uses px. They're 1:1 for our purposes (the mockup is sized for a phone-equivalent viewport).

| Region | Padding |
|---|---|
| Screen content area | 16dp top, 22dp horizontal, 20dp bottom |
| Top bar | 6dp top, 22dp horizontal, 16dp bottom |
| Card (`.p-card`) | 22dp all sides |
| Card title → subtitle gap | 8dp |
| Card subtitle → meta line gap | 20dp |
| Card meta line → CTA gap | 20dp |
| AI strip vertical padding | 4dp |
| AI strip left padding (content past the border) | 14dp |
| AI strip → next element | 32dp bottom margin |
| Card border-radius | 14dp |
| CTA button border-radius | 10dp |
| Tab bar (top border + 10dp top, 18dp bottom, 20dp horizontal) | as listed |
| Evidence row padding | 9dp vertical |
| Severe step row padding | 14dp vertical |

### Borders / strokes

- AI strip: 2dp left border in the severity-appropriate color (Accent/Severe/Healthy)
- Card: 1dp border in `Line` color all sides + 3dp left stripe in severity color
- Section dividers (between evidence rows, healthy stats, history rows): 1dp top border in `Line` color
- Evidence DTC box: 1dp border in `Color(0x26F5C518)` (yellow-tinted) + background `Color(0x0FF5C518)`

## 5. Component specifications

### 5.1 Theme setup

`ui/theme/Theme.kt`:

```kotlin
@Composable
fun CarCopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = CarCopilotColors.PhoneBg,
            surface = CarCopilotColors.PhoneCard,
            onBackground = CarCopilotColors.Text,
            onSurface = CarCopilotColors.Text,
            primary = CarCopilotColors.Accent,
            onPrimary = CarCopilotColors.AccentDeep,
            error = CarCopilotColors.Severe,
        ),
        typography = CarCopilotTypography,
        content = content,
    )
}
```

Wrap the NavHost in `CarCopilotTheme { ... }` at the top of `MainActivity`.

### 5.2 Top bar (shared component)

Same shape on every screen:

- Row, full width, padding (22dp horizontal, 6dp top, 16dp bottom)
- Vertical centered
- Left side: either the brand mark (Home) or a back button (Issue)
  - Brand mark: "CAR" + small yellow dot + "COPILOT" in JetBrains Mono Medium 11sp, 0.18em tracking. Below it, vehicle subtitle "2009 Corolla" in JetBrains Mono 11sp, TextMute, 0.04em tracking, 3dp gap.
  - Back button: `←` chevron icon (13dp) + "Back" or screen-context word in JetBrains Mono 11sp, 0.08em tracking, TextMute. Use `Icons.AutoMirrored.Filled.ArrowBackIosNew` or a custom Path.
- Right side: empty for now (offline pill is roadmap; do not implement)

Build as `TopBar(left: TopBarLeft, modifier: Modifier = Modifier)` where `TopBarLeft` is a sealed type with two variants.

### 5.3 AI strip (the heart of the visual)

This is the most load-bearing component. Spec carefully:

- A Row with two children: a colored vertical bar (2dp wide, full height of the strip content) and a Column with the label + body
- Outer modifier: bottom padding 32dp (margin to next element)
- Inner Column padding: 14dp leading (past the bar)
- Vertical bar height: matches the Column intrinsic height
- Vertical bar color: depends on severity
  - `Issue.severity == "warning"` → CarCopilotColors.Accent
  - `Issue.severity == "severe"` → CarCopilotColors.Severe
  - `Issue.severity == "healthy"` → CarCopilotColors.Healthy
- Label (e.g., "Today's drive", "Here's what I'm seeing", "Pull over now"): typography per table. Color matches severity:
  - warning → Accent
  - severe → Severe
  - healthy → Healthy
- Bottom margin between label and body: 10dp
- Body: depends on SynthesisState:
  - `Thinking` → render `ThinkingDots(severity)` instead of body text
  - `Streaming(partial)` → render `partial` as Text with body typography
  - `Ready(synthesis, goodNews, isFallback)` → render `synthesis` as Text. If `goodNews` is non-null, render it as a second paragraph below with 10dp gap.

`ThinkingDots(severity)`:

- Row of three Box dots
- Each dot 6dp size, circle shape, color = severity color
- Use `rememberInfiniteTransition` with a 1200ms cycle
- Each dot animates: opacity 0.3 → 1.0 → 0.3, translateY 0 → -3dp → 0, on a `keyframes` curve hitting peak at 40% of the cycle
- Stagger: dot 1 starts at t=0, dot 2 at t=150ms, dot 3 at t=300ms
- 4dp horizontal gap between dots
- Followed by a "thinking on-device…" label in JetBrains Mono 11sp TextMute, 10dp leading gap from the dots

When state transitions from Thinking → Streaming, the dots fade out and the text fades in. Use `AnimatedContent` or `Crossfade` with a 200ms duration.

### 5.4 Issue card (home variant)

This is the tappable card on the home screen that navigates to the Issue page.

- Column wrapped in a Surface with:
  - Background: CarCopilotColors.PhoneCard
  - 1dp border in CarCopilotColors.Line on all sides EXCEPT left
  - 3dp left stripe in CarCopilotColors.Accent (overlay it as a separate Box or use a Brush border)
  - 14dp corner radius
  - 22dp internal padding
  - `clickable { onTap() }` — fire navigation
- Children, top to bottom:
  - Title: Text, `Card title` typography, color = `#F5F5F5` (slightly brighter than body Text)
  - 8dp gap
  - Subtitle: Text, `Card subtitle` typography, color = TextMute, lineHeight 1.55
  - 20dp gap
  - Meta line: Text with inline weight styling. Format: "Safe for short trips · **$45** · **30 min** · DIY-friendly". The bold parts are weight 500 in color `#D8D8D8`; the rest is TextMute. Use AnnotatedString.
  - 20dp gap
  - CTA: a full-width Box styled as a button. Background Accent, text AccentDeep, 10dp radius, 13dp vertical / 16dp horizontal padding. Label centered, `CTA button` typography. Visual button only — taps bubble to the parent's clickable. (No separate onClick, since the whole card is tappable.)

The CTA text changes per the home variant decision table — for the misfire home, "Show me what's going on →" (the literal arrow character is fine).

### 5.5 Issue card (issue page variant)

Same card style as 5.4 but contents differ:

- Title: from `issue.title` ("Replace ignition coil — cylinder 1")
- Subtitle: from `issue.subtitle`
- Meta line: from `issue.meta` — cost, time, difficulty
- TWO CTAs stacked:
  - Primary: Accent background, "Walk me through the fix →" — visual only, tap is a no-op or navigates nowhere (this screen isn't built in Phase 5)
  - Secondary (ghost): transparent background, 1dp LineBright border, text color `#D8D8D8`, "Send this to a mechanic instead" — also no-op
  - 10dp gap between them

Both ghost and primary share the same size/padding.

### 5.6 "Also" section (home only)

Below the issue card on home:

- Section label: "Also" in JetBrains Mono 11sp TextMute, 0.14em tracking, 8dp top margin, 12dp bottom margin
- Each row: a Row with a leading em-dash (`—` in JetBrains Mono, TextFaint) then the row text in Geist 14sp color `#C8C8C8`, lineHeight 1.5
- 12dp gap between dash and text
- 9dp vertical padding per row
- Two rows for the misfire demo: "Tighten gas cap next time you stop" and "Front-left tire low at 22 PSI"

### 5.7 Evidence toggle and expanded section (issue page only)

The "Show raw data for a mechanic" toggle below the issue card:

- A Row, full width, 14dp vertical padding, top 1dp border in Line
- Background transparent
- `clickable { expanded = !expanded }`
- Left text: "SHOW RAW DATA FOR A MECHANIC" in JetBrains Mono 11sp TextMute, 0.14em tracking, ALL CAPS
- Right: a small chevron, rotates 180° when expanded, animated via `animateFloatAsState`

When expanded, below the toggle:

- AnimatedVisibility wrapping a Column with 8dp bottom padding
- Each evidence section has a label ("TROUBLE CODES" / "LIVE READINGS") in JetBrains Mono 10sp TextFaint, 0.16em tracking, 16dp top / 8dp bottom margin

DTC rows (TROUBLE CODES section):
- Box with background `Color(0x0FF5C518)`, 1dp border `Color(0x26F5C518)`, 8dp radius, 10dp vertical / 12dp horizontal padding, 6dp bottom margin
- DTC code: "P0301" in JetBrains Mono 12sp Medium, color Accent, 0.04em tracking
- 3dp gap
- Description: in JetBrains Mono 12sp TextMute, lineHeight 1.5

Live reading rows:
- Row with key + value, space-between, 9dp vertical padding, 1dp bottom border in Line (except last row)
- Key in JetBrains Mono 12sp TextMute, 0.04em tracking
- Value in JetBrains Mono 12sp Medium, Text color
- If reading is in a warning state, value color = Accent

### 5.8 Bottom tab bar

Two tabs: Home and History. Sticky to the bottom of the screen.

- Row, full width, top 1dp border in Line, background PhoneBg
- 10dp top padding, 18dp bottom padding, 20dp horizontal padding
- `Arrangement.SpaceAround` for the two tabs
- Each tab is a Column with centered alignment, 4dp horizontal padding
- Icon (20dp size) on top
- 5dp gap
- Label below: tab name in JetBrains Mono 11sp, 0.06em tracking
- Active tab: icon + label color = Accent
- Inactive tab: icon + label color = TextFaint

Use `Icons.Filled.Home` and `Icons.Filled.AccessTime` (or `History`) for icons.

Tapping History does nothing (visual only — the screen doesn't exist in Phase 5).

## 6. Phases & acceptance criteria

Work in this order, commit between each.

### Phase A — Theme foundation

1. Add Geist + JetBrains Mono fonts to `res/font/`.
2. Create `ui/theme/Color.kt` with `CarCopilotColors`.
3. Create `ui/theme/Type.kt` with `CarCopilotTypography` matching the type scale table.
4. Update `ui/theme/Theme.kt` to use the new palette + typography.
5. Wrap `NavHost` in `CarCopilotTheme` at MainActivity.

**Acceptance:** App still launches, screens still render (with default Compose layouts), but the colors and fonts are now the mockup's. No layout changes yet; the home and issue screens look "wrong" structurally but in the right palette.

**Commit:** `feat(phase-6a): theme tokens and typography`

### Phase B — TopBar + AI strip

1. Build the shared `TopBar` composable with both `TopBarLeft.Brand` and `TopBarLeft.Back` variants.
2. Build `ThinkingDots(severity)`.
3. Build the AI strip composable that consumes `SynthesisState` and renders accordingly. Preserve all existing state-handling logic; only change the visual.
4. Wire HomeScreen and IssueScreen to use the new TopBar + AI strip.

**Acceptance:** HomeScreen's AI strip shows thinking dots resolving to the canned synthesis with the correct vertical accent bar and label color. IssueScreen's AI strip shows thinking dots resolving into live-streamed text from Gemma. Both screens have the correct top bar.

**Commit:** `feat(phase-6b): topbar and AI strip`

### Phase C — Cards, also section, evidence, tab bar

1. Build `IssueCard` composable with primary CTA only (for home).
2. Build `IssueCardWithCTAs` composable with primary + ghost CTAs (for issue page).
3. Build the "Also" section rows.
4. Build `EvidenceToggle` + `EvidenceSection` composables.
5. Build the `TabBar` composable.
6. Compose HomeScreen with: TopBar, AI strip, IssueCard, Also section, TabBar.
7. Compose IssueScreen with: TopBar (back), AI strip, IssueCardWithCTAs, EvidenceToggle/Section, TabBar.

**Acceptance:** Both screens render the full mockup-equivalent layout. Tapping the home card navigates to issue. The issue page's evidence toggle expands/collapses smoothly. The tab bar appears on both screens with Home active on home and History inactive (and vice versa for the issue page — Home stays active since you came from there).

**Commit:** `feat(phase-6c): cards, also rows, evidence, tab bar`

### Phase D — Animation pass

1. Verify ThinkingDots animation feels right (1.2s cycle, phase-shifted, bouncing).
2. Add Crossfade or AnimatedContent on the SynthesisState transition so dots fade into text smoothly (200ms).
3. Add chevron rotation animation on EvidenceToggle (animateFloatAsState, 200ms).
4. Add AnimatedVisibility on the evidence content expanding (slide + fade, 300ms).

**Acceptance:** On a clean reinstall + warm-cache launch, the visual flow from app open → tap card → see thinking dots → see streaming text → tap evidence → see codes is smooth and feels like the mockup's animations.

**Commit:** `feat(phase-6d): animations`

## 7. Anti-goals

Do NOT do any of the following, even if the mockup shows them:

- The offline pill in the top right of each screen — Phase 5 doesn't model offline state
- Severity-colored AI strip variants (severe/healthy) on the issue page — the misfire issue is always warning route in Phase 5
- The Pre-flight, Walkthrough, Severe Issue, Drafted message, Healthy state, or History screens — none of these exist in Phase 5's NavHost; do NOT add them
- Splash screen — let the app launch directly into HomeScreen; the engine init happens in background
- Haptics
- Dark mode toggle (we are always dark)
- Locale switching (always English)
- Settings screen

If the visual polish is going well and Phase D wraps with time to spare, **stop**. Test the recording flow. Don't add features that weren't in the spec.

## 8. Things to verify before declaring polish done

Walk this checklist on the actual device:

- [ ] App launches into a properly themed HomeScreen (correct colors, fonts, layout)
- [ ] Thinking dots animate smoothly on first load
- [ ] Canned synthesis renders cleanly after the dots
- [ ] Issue card visually matches the mockup home-multi state
- [ ] Tapping the card transitions to IssueScreen
- [ ] IssueScreen's AI strip thinks (with proper dots) then streams real Gemma text word-by-word
- [ ] Streamed text reads cleanly — no JSON syntax visible, no flicker, no recomposition flashes
- [ ] Issue card on the issue page matches the mockup, with both CTAs visible (even though they don't navigate)
- [ ] Evidence toggle shows the chevron and the correct label
- [ ] Tapping the toggle expands smoothly to reveal DTC + live readings sections
- [ ] DTC code (P0301) shows with the yellow-tinted box styling
- [ ] Live readings show with appropriate severity colors (warning rows in yellow)
- [ ] Tab bar appears on both screens with correct active state
- [ ] Back from issue page returns to home

## 9. After polish

When all four phases land and the checklist in §8 is green:

1. Final commit if anything was missed: `chore(phase-6): pre-record polish complete`
2. Practice run on the warmed device (model already staged, shader cache built)
3. Record
4. Submit

The polish work is the last meaningful build effort before recording. If it eats more than 6 hours of agent time, stop and ship Phase 5's current state — judges care about on-device inference, not pixel-perfect typography. The mockup-fidelity is leverage, not a requirement.
