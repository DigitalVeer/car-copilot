# CLAUDE.md — CAR·COPILOT · Android

## What this is

This is the **Android port** of CAR·COPILOT, built in parallel with the Python reference implementation (a separate repo). We are in **Phase 5** of the project — the on-device Gemma pivot for the Kaggle Gemma 4 for Good hackathon. The goal is one APK on a real phone running one flow with Gemma generating text live on-device.

**Read these before doing anything else:**
- `reference/carcopilot_android_spec.md` — the controlling document for this phase. The spec wins when anything else conflicts.
- `reference/carcopilot_design.md` — the original technical design (Python reference). Use sections 4 (schema), 6 (Gemma adapter), and Appendix A (prompts) as design reference. **Sections about Ollama, Docker, and pytest are NOT applicable here.**
- `reference/carcopilot_mockup_v09.html` — the visual target. The runtime version will be bundled in `app/src/main/assets/` once you scaffold the app. The reference version in `reference/` is the canonical original; don't edit it.

## What we're building right now

**Phase 5, Checkpoint A.** Per spec §5:

Do NOT scaffold the Android Studio project yet. The current task is to verify Gemma 4 loads on the target device via LiteRT-LM and produces parseable JSON for our actual `issue_synthesis.md` prompt. Once Checkpoint A is green and the user has reviewed the results, proceed to scaffolding.

After Checkpoint A passes:
- Scaffold one Activity, one WebView covering full screen
- Load `carcopilot_mockup_v09.html` (copied into `app/src/main/assets/` as `mockup.html`)
- JS bridge: `Android.generateSynthesis(issueJson)` → Kotlin coroutine → LiteRT-LM call → `webView.evaluateJavascript("window.onSynthesisReady(...)")`
- One canned scenario (misfire), one canned Issue baked into the app from `reference/fixtures/misfire.json`
- Fallback synthesis (canned Kotlin string) used if LiteRT-LM throws

## Hard constraints — never violate

- **No cloud API calls.** All inference runs on the device via LiteRT-LM. This is the central product claim — do not break it.
- **No location services.** Never request `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` in the manifest. Artifacts that reference location use the literal string `"my current location"`.
- **No internet permission for AI features.** Internet permission may be needed for development logging — but no AI request ever goes off-device.
- **No mode 04** (clear DTCs). The agent does not expose this.
- **Gemma never classifies.** The DTC table is the source of truth for severity, route, cost, time. LiteRT-LM only generates narrative text.
- **Issue schema is the contract.** Kotlin data classes mirror `reference/python-src/schema.py`. Adding or removing fields requires explicit approval.
- **Fail soft.** If LiteRT-LM errors, throws, or returns unparseable text, fall back to the canned synthesis string. The app must never crash on the user.

## Conventions

- **Language:** Kotlin only. Do not use Java.
- **Async:** Kotlin coroutines (`CoroutineScope(Dispatchers.IO)`, `LaunchedEffect`, `withContext`), not RxJava, not threads.
- **UI:** Jetpack Compose. Native composables, NOT a WebView wrapper. The HTML mockup at `reference/carcopilot_mockup_v09.html` is **visual reference only** — extract colors, fonts, spacing, animation timings from its CSS and build equivalent Compose components. Do not bundle the HTML in the app.
- **Navigation:** Single Activity with Compose NavController. Two destinations for Phase 5: HomeScreen and IssueScreen.
- **Fonts:** Bundle Geist and JetBrains Mono in `res/font/` and reference them via `FontFamily` in the theme. Both are open source.
- **Theme:** Custom dark palette extracted from the mockup CSS variables (`--phone-bg`, `--phone-card`, `--accent`, `--severe`, `--healthy`, etc.). Material3 surfaces with overrides.
- **LLM SDK:** `com.google.mediapipe.tasks.genai.llminference.LlmInference` (LiteRT-LM is the framework rebrand; the namespace is unchanged at SDK level).
- **Min SDK:** 26 (Android 8.0). Target SDK: current stable.
- **Package:** `com.example.carcopilot` is fine for hackathon — do not rename, it's not worth the gradle churn.

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

## Scope — what to build, what to skip entirely

**Build natively in Compose (the only screens that exist in the app):**
- `HomeScreen` — matches mockup home-multi state. Brand top bar, AI strip with thinking-dots animation that resolves to canned synthesis text on first load, tappable issue card (misfire), "Also" rows, bottom tab bar.
- `IssueScreen` — matches mockup issue page. Back button top bar, AI strip that triggers a live LiteRT-LM call on entry, issue card with title/sub/meta, evidence toggle expanding to DTCs + live readings.

**Do not build at all (the mockup HAS these screens but the Android app does NOT):**
- Pre-flight screen
- Walkthrough screen
- Severe Issue page
- Drafted message screen
- Healthy state
- History tab
- All polymorphic variants beyond the misfire-DIY case

The bottom tab bar in the Android app shows Home and History tabs but tapping History does nothing or shows a placeholder — it does not navigate to a built screen. Same for the "Send this to a mechanic instead" button on IssueScreen if you render it — it's visual only.

If the agent finds itself implementing more than HomeScreen + IssueScreen, stop and ask. Two screens is the entire app for Phase 5.

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
- Refactoring the mockup HTML (only add the JS bridge hooks the spec requires)

## Voice constraints (when working with prompts or fallback text)

Every user-facing string must follow the voice rules in `reference/prompts/system.md`:

- One thought per sentence.
- Concrete, not abstract.
- When you name a problem, say what to do about it.
- No engineer vocabulary: never "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."
- Imagine the user is a friend on the phone, not a developer.

If you find yourself writing canned fallback text that sounds like a developer wrote it, rewrite it as something a friend would say.

## Checkpoint protocol

**Checkpoint A: Gemma 4 loads on device and produces parseable JSON.**
- Use the E4B `.task` file first (consistency with Python voice tuning). Fall back to E2B if E4B is too slow or memory-bound.
- Test with the actual `issue_synthesis.md` prompt + `misfire.json` data, not a stripped-down test prompt.
- Report: model file used, raw output, JSON parses cleanly (yes/no), voice comparison vs. Python output, inference latency.
- WAIT for user review before scaffolding.

**Checkpoint B: WebView + JS bridge round-trips a real Gemma call.**
- App launches, mockup renders, tap card navigates to Issue page, synthesis text appears generated live.
- Report: install succeeded, app didn't crash, real Gemma text appeared in AI strip.
- WAIT for user review before any further work.

## When you finish a checkpoint

1. Run the verification steps from the spec.
2. Commit with a phase-tagged message: `feat(phase-5-checkpoint-a): liteRT-LM smoke test green` or similar.
3. Report results to the user.
4. **Pause.** Do not proceed to the next checkpoint without explicit go-ahead.

## Notes for whoever picks this up

- The Python repo (separate directory) is the reference implementation. The voice-tuned prompts in `reference/prompts/` are the highest-value artifact in this whole project — they took real iteration to produce. Don't rewrite them; bundle them as-is.
- The mockup HTML is also load-bearing. Only add the JS bridge hooks the Android spec specifies. Don't restyle, don't restructure.
- The hackathon deadline is Monday. The honest fallback if Android stalls is documented in spec §5 — submit the Python proof + mockup with the architecture-proof framing. That fallback is real and defensible; don't ship broken Android code to avoid it.