# CLAUDE.md — CAR·COPILOT · Android

## What this is

CAR·COPILOT is an on-device car diagnostic copilot. The Android app takes an OBD-II snapshot, runs deterministic classification against a built-in DTC table, then streams a friend-on-the-phone explanation from Gemma 4 running locally via LiteRT-LM. No cloud calls, no location services. The hackathon demo shipped; this repo is the post-demo working tree as we extend toward real Bluetooth OBD.

The Python repo in a sibling directory is the original reference implementation. The voice-tuned prompts in `reference/prompts/` are the highest-value artifact in the whole project — they took real iteration to produce. Bundle them as-is; don't rewrite.

## Current state

What's live in the app today:

- **Five screens** wired in `MainActivity`'s NavHost: Home, Issue, Walkthrough, Mechanic Draft, History.
- **Three live Gemma surfaces**, all streaming token-deltas via LiteRT-LM:
  - `streamSynthesis` — the AI strip on the Issue page
  - `streamMechanicDraft` — the editable text on the Mechanic Draft page
  - `streamHistoryPattern` — the pattern explanation on the History page
- **Walkthrough screen** renders canned steps from `DTCTable` (not yet a live generation surface; prompt exists at `reference/prompts/walkthrough.md` for when it goes live).
- **Data layer** (Phase 11): `OBDDataSource` interface → `FixtureOBDDataSource` impl reading `app/src/main/assets/misfire.json`. `IssueBuilder` composes an `Issue` from an `OBDSnapshot` plus a `DTCTable`. The Phase-12-prep fields (`source`, `engineFamily`, `permanentDtcs`) carry on every snapshot for the future Bluetooth path.
- **Performance** (Phase 8): the `Engine` initializes in `appScope.async` at process start, and a parallel `prewarmJob` sends a dummy "ok" turn and cancels at first token. The Conversation slot is hoisted across calls for KV-cache reuse. ~24% first-token latency win measured on Pixel 9.
- **Surface multiplexing** (Phase 10A): LiteRT-LM 0.11.0 allows only one Conversation per Engine. `GemmaService.acquireConversationForSurfaceLocked(surface)` closes the existing Conversation when a different surface (synthesis/draft/history) acquires the slot. Each transition pays a system-prompt prefill.
- **JVM unit test suite** (`./gradlew test`): tests in `app/src/test/java/...` cover the streaming JSON extractor (`SynthesisStateTest`, `MechanicDraftStateTest`) and the OBDSnapshot schema (`OBDSnapshotTest`).

## What's broken

One known issue, mitigated but root cause is in the SDK:

- **Native instability on rapid surface churn** — LiteRT-LM 0.11.0 can SIGSEGV inside `liblitertlm_jni.so` when the user navigates between Issue / Walkthrough / Mechanic / History faster than in-flight prefills complete. Phase 11.5 (`edd06db`) serialized surface switches behind `convoMutex` end-to-end and added a `NATIVE_SETTLE_MS = 250` settle delay after `Conversation.close()` before the next `createConversation`. The mitigation holds for the linear demo flow. Full crash signature, trigger pattern, and proper-fix options live in `FUTURE_WORK.md`.

## Hard constraints — never violate

- **No cloud API calls.** All inference runs on the device via LiteRT-LM. This is the central product claim.
- **No location services.** Never request `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. Artifacts that reference location use the literal string `"my current location"`.
- **No internet permission for AI features.** Internet permission may be acceptable for dev logging, but no AI request ever leaves the device.
- **No mode 04** (clear DTCs). The app does not expose this.
- **Gemma never classifies.** `DTCTable` is the source of truth for severity, route, cost, time. Gemma only generates narrative text on top.
- **Issue schema is the UI contract.** `model/Schema.kt`. Adding or removing fields needs explicit approval — every screen renders an `Issue`.
- **Fail soft.** If LiteRT-LM errors, throws, or returns unparseable text, fall back to canned text (`model/Fallbacks.kt` for synthesis; `DTCTable` entries for mechanic draft and walkthrough; `model/History.PATTERN` for history). The app must never crash on the user.

## Locked architecture — do not modify without asking

These contracts are load-bearing. Touching any of them requires a stop-and-ask:

**Phase 5 functional contract:**
- `inference/GemmaService.kt` — engine init, model staging to `filesDir`, streaming inference
- `ui/SynthesisState.kt` including `extractSynthesisInProgress(buffer: String)` — the JSON-aware streaming text extractor
- `CarCopilotApp.onCreate` — the `appScope.async` init pattern
- `MainActivity` NavHost wiring
- The `LaunchedEffect` collect blocks in `IssueScreen`, `MechanicDraftScreen`, `HistoryScreen`
- All five resilience paths to `Ready(fallback, isFallback=true)`
- `data/DTCTable.kt` content, prompt files in `app/src/main/assets/`, `misfire.json` fixture content

**Since Phase 5 — also load-bearing:**
- **Surface multiplexing** (`GemmaService.acquireConversationForSurfaceLocked`) — collapsing this breaks live mechanic draft and history pattern; the single-Conversation-per-Engine SDK constraint is real.
- **`convoMutex` end-to-end + `NATIVE_SETTLE_MS` settle delay** (Phase 11.5) — this is the SIGSEGV workaround. Removing the mutex or the delay reintroduces the native crash.
- **`OBDDataSource` interface** (Phase 11A) — keep it `suspend` and keep the `Result<OBDSnapshot>` return. BLE will need both. The header in `data/OBDDataSource.kt` says this explicitly; honor it.
- **`appScope.async` engine init + parallel `prewarmJob`** (Phase 8) — the prewarm cuts first-token latency by ~24%. Inlining or removing it loses the speedup.
- **`testOptions { unitTests { isReturnDefaultValues = true } }`** in `app/build.gradle.kts` — what makes Android-namespaced code (anything that touches `android.util.Log`) runnable as JVM unit tests. Don't strip it as "unused config."

## Where things live

| Concern | Source of truth |
|---|---|
| What the app is and does | This file, intro |
| What's been built (history) | `git log` (canonical); this file's "Current state" is a summary, not a phase-by-phase log |
| Known issues + roadmap | `FUTURE_WORK.md` |
| Voice rules | `reference/prompts/system.md` |
| Visual design tokens (colors, type, spacing) | `reference/carcopilot_mockup_v09.html` `<style>` block |
| DTC classifier table | `app/src/main/java/com/example/carcopilot/data/DTCTable.kt` |
| Issue data shape | `app/src/main/java/com/example/carcopilot/model/Schema.kt` |
| OBD adapter seam | `app/src/main/java/com/example/carcopilot/data/OBDDataSource.kt` |
| Live-generation surfaces | `app/src/main/java/com/example/carcopilot/inference/GemmaService.kt` |
| Fallback text | `app/src/main/java/com/example/carcopilot/model/Fallbacks.kt`, `DTCTable` entries, `model/History.kt` |
| Prompts (consumed at runtime) | `app/src/main/assets/system.md`, `issue_synthesis.md`, `mechanic_draft.md`, `history_pattern.md` (mirrors of `reference/prompts/`) |

## Conventions

- **Language:** Kotlin only. No Java.
- **Async:** Kotlin coroutines (`CoroutineScope(Dispatchers.IO)`, `LaunchedEffect`, `withContext`, `NonCancellable` where teardown must complete). No RxJava, no threads.
- **UI:** Jetpack Compose. Native composables, NOT a WebView. The HTML mockup at `reference/carcopilot_mockup_v09.html` is visual reference only — extract colors, fonts, spacing, animation timings from its CSS and build equivalent Compose components.
- **Navigation:** Single Activity with Compose `NavController`. Five destinations: `home`, `issue`, `walkthrough`, `draft`, `history`, all wired in `MainActivity`.
- **Fonts:** Geist and JetBrains Mono in `app/src/main/res/font/`, referenced via `FontFamily` in `ui/theme/Type.kt`. Both open source — Geist from Vercel, JetBrains Mono from JetBrains' GitHub.
- **Theme:** Custom dark palette in `ui/theme/Color.kt` extracted from the mockup's `:root` CSS variables (`--phone-bg`, `--phone-card`, `--accent`, `--severe`, `--healthy`, etc.). Material3 surfaces with overrides.
- **LLM SDK:** `com.google.ai.edge.litertlm:litertlm-android:0.11.0`. Package `com.google.ai.edge.litertlm.*` — `Engine`, `EngineConfig`, `Backend`, `Conversation`, `ConversationConfig`, `SamplerConfig`, `Message`, `Contents`. (Replaces the older `com.google.mediapipe.tasks.genai.llminference` namespace, which still exists but is deprecated for the LiteRT-LM rebrand.)
- **Model format:** `.litertlm` (Android/iOS/desktop). The `.task` files in the same `litert-community` Hugging Face repos are the **web** build and will not load via the Android SDK. Default E4B (`gemma-4-E4B-it.litertlm`, ~3.41 GB). Switch to E2B at build time: `./gradlew assembleDebug -PmodelVariant=E2B` — read via `BuildConfig.MODEL_VARIANT` in `GemmaService`.
- **Manifest:** the GPU backend requires two `<uses-native-library>` entries inside `<application>` to dlopen the vendor OpenCL driver:
  ```xml
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  ```
- **Model staging:** the GPU delegate writes a sidecar weights cache next to the model file. `/data/local/tmp/` isn't writable by the app's UID, so the model copies into `context.filesDir` before `Engine.initialize()`. Already wired in `GemmaService.stageModel` — do not change.
- **Streaming:** `Conversation.sendMessageAsync(String): Flow<Message>` emits **per-token deltas** (not cumulative — confirmed empirically in Phase 5 Checkpoint A). Compose collectors append, never replace. Steady-state ~5 tok/s after ~6s first-token latency on Pixel 9. The streaming UIs extract the in-progress content from a partial JSON buffer via per-surface extractors that are JSON-aware and never let a stray backslash or half-finished escape reach the user.
- **Min SDK:** 26 (Android 8.0). **Target SDK:** 36 (`app/build.gradle.kts`).
- **Package:** `com.example.carcopilot` — do not rename.

## Build commands (run from project root)

```bash
# Build debug APK (default E4B)
./gradlew assembleDebug

# Build with the E2B variant for the Phase 8 measurement harness
./gradlew assembleDebug -PmodelVariant=E2B

# Run JVM unit tests
./gradlew test

# Install on connected device
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

# Inspect runtime logs (filter to our tag)
adb logcat -s CarCopilot:V

# Uninstall (for clean reinstall)
adb uninstall com.example.carcopilot

# Verify device is connected before installing
adb devices
```

The target phone is a corporate-managed Pixel 9 — sideload via APK browser is blocked, but ADB install works because USB debugging is authorized. Always install via `adb install`, never instruct the user to drag-and-drop an APK.

## OBD emulator (Python, dev-only)

A standalone Python 3 TCP server lives at `emulator/obd_emulator.py` — it speaks ELM327 AT commands over a socket so the upcoming Phase-12 transport layer can be developed without an ELM327 dongle and a car. Stdlib only, no `pip install`. Run with `python3 emulator/obd_emulator.py [--scenario corolla|hilux] [--port 35000]`. See `emulator/README.md` for scenarios and protocol coverage. The Android side has not yet been wired to it — Phase 12 work.

## Voice constraints (for prompts and fallback text)

Every user-facing string follows the voice rules in `reference/prompts/system.md` — that file is the source of truth:

- One thought per sentence.
- Concrete, not abstract.
- When you name a problem, say what to do about it.
- No engineer vocabulary: never "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."
- Imagine the user is a friend on the phone, not a developer.

If you find yourself writing canned fallback text that sounds like a developer wrote it, rewrite it as something a friend would say.

## Reference materials

`reference/prompts/*.md` and `reference/carcopilot_mockup_v09.html` are **authoritative** — voice rules and design tokens. Treat them as read-only unless the change is intentional.

Other docs under `reference/` are **historical or mixed** — each carries a STATUS banner at the top describing what's still live vs. what's archaeology. They're kept around for the *why* behind decisions (e.g., why LiteRT-LM and not AICore, why Compose-native and not WebView). Don't take any specification in those files as current without checking the banner.

## Out of scope (do not work on without asking)

- Real Bluetooth OBD-II integration (the `OBDDataSource` seam is shape-ready, but `BluetoothOBDDataSource` is unbuilt — see FUTURE_WORK)
- Live walkthrough generation
- Real history persistence (`History.ENTRIES/STATS/PATTERN` is in-memory fixture)
- Spanish translation
- Push notifications, lockscreen integration
- Additional DTC scenarios beyond the bundled misfire (`DTCTable` has one entry — extending is data work, see FUTURE_WORK's RAG-backed DTC table item)
- Settings, vehicle selection, locale switching
- Mockup HTML refactoring (read-only design source of truth)
