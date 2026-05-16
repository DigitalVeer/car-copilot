# CLAUDE.md — CAR·COPILOT · Android

## What this is

This is the **Android port** of CAR·COPILOT, built in parallel with the Python reference implementation (a separate repo). Phase 5 is **complete and committed** — the app launches, navigates Home → Issue, runs on-device Gemma 4 E4B via LiteRT-LM, and streams the synthesis text into the AI strip live with the JSON envelope hidden. We are now in **Phase 6** — the visual polish pass to bring the app's appearance up to the HTML mockup target before recording the hackathon demo video.

**Read these before doing anything else:**
- `reference/carcopilot_phase_6_polish.md` — the controlling document for this phase. The polish spec wins when anything else conflicts.
- `reference/carcopilot_mockup_v09.html` — the visual target. Open it, find the `<style>` block, read the `:root` CSS custom properties — those are the design tokens you're porting into Compose. The component classes below (`.p-ai`, `.p-card`, etc.) show how the tokens compose into the visual.
- `reference/carcopilot_android_spec.md` — the Phase 5 spec. Use for understanding what's already been built (architecture, SynthesisState machinery, why the app is structured the way it is). **Do not redo Phase 5 work** — it is committed and working.
- `reference/carcopilot_design.md` — the original Python technical design. Sections 4 (schema) and Appendix A (prompts) are useful reference. Ollama / Docker / pytest sections are NOT applicable here.

## What we're building right now

**Phase 6 — visual polish.** The app currently runs end-to-end with default Material3 dark surfaces and system fonts. Phase 6 brings the UI up to the mockup's design system: custom palette, Geist + JetBrains Mono fonts, mockup-fidelity components (AI strip with thinking dots, accent-bordered cards, evidence section, two-icon tab bar).

Work the phases in `reference/carcopilot_phase_6_polish.md` §6 in order, committing between each:

- **Phase A** — Theme foundation: fonts in `res/font/`, `CarCopilotColors`, `CarCopilotTypography`, wrap `NavHost` in `CarCopilotTheme`.
- **Phase B** — Shared `TopBar` + `AnimatedAIStrip` + `ThinkingDots`.
- **Phase C** — Issue card (both variants), "Also" rows, evidence toggle + section, tab bar.
- **Phase D** — Animation pass: dots bouncing, Thinking→Streaming transition, chevron rotation, evidence expand.

Pause and report after each phase. Do NOT start the next phase without user review.

## Hard constraints — never violate

- **No cloud API calls.** All inference runs on the device via LiteRT-LM. This is the central product claim — do not break it.
- **No location services.** Never request `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` in the manifest. Artifacts that reference location use the literal string `"my current location"`.
- **No internet permission for AI features.** Internet permission may be needed for development logging — but no AI request ever goes off-device.
- **No mode 04** (clear DTCs). The agent does not expose this.
- **Gemma never classifies.** The DTC table is the source of truth for severity, route, cost, time. LiteRT-LM only generates narrative text.
- **Issue schema is the contract.** Kotlin data classes mirror `reference/python-src/schema.py`. Adding or removing fields requires explicit approval.
- **Fail soft.** If LiteRT-LM errors, throws, or returns unparseable text, fall back to the canned synthesis string. The app must never crash on the user.
- **Phase 5 functional contract is locked.** Do not modify: `GemmaService`, `SynthesisState` (including `extractSynthesisInProgress`), `CarCopilotApp.onCreate` init, `NavHost` routing, the `LaunchedEffect` collect block in `IssueScreen`, resilience paths to `Ready(fallback)`, DTC table, prompt files, or fixtures. Read polish spec §3 for the explicit list. If something in that list needs to change, stop and ask.

## Conventions

- **Language:** Kotlin only. Do not use Java.
- **Async:** Kotlin coroutines (`CoroutineScope(Dispatchers.IO)`, `LaunchedEffect`, `withContext`), not RxJava, not threads.
- **UI:** Jetpack Compose. Native composables, NOT a WebView wrapper. The HTML mockup at `reference/carcopilot_mockup_v09.html` is **visual reference only** — extract colors, fonts, spacing, animation timings from its CSS and build equivalent Compose components. Do not bundle the HTML in the app.
- **Navigation:** Single Activity with Compose `NavController`. Two destinations: `HomeScreen` and `IssueScreen`. Both already exist and are wired.
- **Fonts:** Bundle Geist and JetBrains Mono in `res/font/` and reference them via `FontFamily` in the theme. Both are open source — Geist from Vercel, JetBrains Mono from JetBrains' GitHub.
- **Theme:** Custom dark palette extracted from the mockup CSS variables (`--phone-bg`, `--phone-card`, `--accent`, `--severe`, `--healthy`, etc.). Material3 surfaces with overrides. Polish spec §4 has the exact hex values.
- **LLM SDK:** `com.google.ai.edge.litertlm:litertlm-android:0.11.0`. Package `com.google.ai.edge.litertlm.*` — classes `Engine`, `EngineConfig`, `Backend`, `Conversation`, `ConversationConfig`, `SamplerConfig`, `Message`, `Contents`. (This replaces the older `com.google.mediapipe.tasks.genai.llminference.LlmInference` path — that namespace still exists but is being deprecated for the LiteRT-LM rebrand.)
- **Model format:** `.litertlm` (the Android/iOS/desktop build). The `.task` files in the same litert-community Hugging Face repos are the **web** build and will not load via the Android SDK. E4B = `gemma-4-E4B-it.litertlm`, ~3.41 GB.
- **Manifest:** the GPU backend requires two `<uses-native-library>` entries inside `<application>` to dlopen the vendor OpenCL driver:
  ```xml
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  ```
- **Model staging:** the GPU delegate writes a sidecar weights cache next to the model file. `/data/local/tmp/` is not writable by the app's UID, so the model lives in `context.filesDir` before `Engine.initialize()`. Already wired in `CarCopilotApp.onCreate` — do not change.
- **Streaming:** `Conversation.sendMessageAsync(String): Flow<Message>` emits **per-token deltas** (not cumulative running text — confirmed empirically in Checkpoint A). Compose collectors append, never replace. Steady-state ~5 tok/s after ~6s first-token latency on Pixel 9. The `IssueScreen` extracts synthesis content from the partial JSON buffer via `SynthesisState.extractSynthesisInProgress`; this is already implemented and locked.
- **Min SDK:** 26 (Android 8.0). Target SDK: current stable.
- **Package:** `com.example.carcopilot` — do not rename.

## Build commands (run from project root)

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device (Windows/PowerShell)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

# Inspect runtime logs (filter to our tag)
adb logcat -s CarCopilot:V

# Uninstall (for clean reinstall)
adb uninstall com.example.carcopilot

# Verify device is connected before installing
adb devices
```

The target phone is a corporate-managed Pixel 9 — sideload via APK browser is blocked, but ADB install works because USB debugging is authorized. Always install via `adb install`, never instruct the user to drag-and-drop an APK.

## Scope — what to polish, what to skip

**In scope for Phase 6 — visual restyling of existing screens:**
- `HomeScreen` — port to match mockup home-multi state (brand top bar, AI strip with thinking dots, tappable issue card, "Also" rows, tab bar)
- `IssueScreen` — port to match mockup issue page (back top bar, AI strip with streaming text, issue card with two CTAs, evidence toggle + section, tab bar)
- Theme and typography supporting both screens

**Out of scope for Phase 6 (do NOT build):**
- Pre-flight, Walkthrough, Severe Issue, Drafted Message, Healthy state, History screens — none exist in NavHost; do NOT add to NavHost
- Severity-variant rendering of the AI strip (the demo issue is always warning route)
- Offline pill in the top right (we don't model offline state)
- Splash screen, haptics, transitions beyond what's in the polish spec
- Settings, vehicle selection, locale switching

If the agent finds itself implementing anything outside the polish spec §6 phases, stop and ask.

## What is explicitly out of scope (do not work on these without asking)

- Real Bluetooth OBD-II integration
- Walkthrough text generation (live)
- Mechanic draft generation (live)
- History tab / pattern detection
- Spanish translation
- Push notifications, lockscreen integration
- Multiple scenarios beyond misfire
- Settings screen, vehicle selection
- Tests (Kotlin unit tests, instrumented tests — skip for hackathon)
- Mockup HTML refactoring

## Voice constraints (when working with prompts or fallback text)

Every user-facing string must follow the voice rules in `reference/prompts/system.md`:

- One thought per sentence.
- Concrete, not abstract.
- When you name a problem, say what to do about it.
- No engineer vocabulary: never "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."
- Imagine the user is a friend on the phone, not a developer.

If you find yourself writing canned fallback text that sounds like a developer wrote it, rewrite it as something a friend would say.

## Phase 6 acceptance protocol

Each phase in `carcopilot_phase_6_polish.md` §6 has explicit acceptance criteria. When you finish a phase:

1. Run the acceptance check from the polish spec.
2. Commit with a phase-tagged message: `feat(phase-6a): theme tokens and typography`, `feat(phase-6b): topbar and AI strip`, `feat(phase-6c): cards, also rows, evidence, tab bar`, `feat(phase-6d): animations`.
3. Report results to the user.
4. **Pause.** Do not proceed to the next phase without explicit go-ahead.

The final §8 visual checklist in the polish spec gates the entire Phase 6 — when all four phases (A–D) are committed and §8 is green on the actual device, polish is complete and recording can begin.

## Notes for whoever picks this up

- The Python repo (separate directory) is the reference implementation. The voice-tuned prompts in `reference/prompts/` are the highest-value artifact in this whole project — they took real iteration to produce. Don't rewrite them; bundle them as-is.
- The mockup HTML is the design system source of truth for Phase 6. Open it in a browser side-by-side with the running app while polishing — visual mismatches show up immediately.
- The hackathon deadline is Monday. If Phase 6 starts eating more than 6 hours of agent time, stop and ship what's there — judges care about on-device inference, not pixel-perfect typography. The polish is leverage on top of a working demo, not a replacement for it.