# CLAUDE.md — CAR·COPILOT · Android

## What this is

CAR·COPILOT is an on-device car diagnostic copilot. The Android app takes an OBD-II snapshot, runs deterministic classification against a built-in DTC table, then streams a friend-on-the-phone explanation from Gemma 4 running locally via LiteRT-LM. No cloud calls, no location services. The hackathon demo shipped; this repo is the post-demo working tree as we extend toward real Bluetooth OBD.

The Python repo in a sibling directory is the original reference implementation. The voice-tuned prompts in `reference/prompts/` are the highest-value artifact in the whole project — they took real iteration to produce. Bundle them as-is; don't rewrite.

## Current state

What's live in the app today:

- **Five screens** wired in `MainActivity`'s NavHost: Home, Issue, Walkthrough, Mechanic Draft, History. The Home screen also crossfades from a `SplashScreen` while `GemmaService.awaitReady()` resolves.
- **Five live Gemma surfaces**, all streaming token-deltas via LiteRT-LM:
  - `streamSynthesis` — the AI strip on the Issue page
  - `streamMechanicDraft` — the editable text on the Mechanic Draft page
  - `streamHistoryPattern` — the pattern explanation on the History page
  - `streamWalkthroughPlan` — the per-DTC step envelope, grounded against `assets/walkthroughs/<CODE>.md`
  - `streamWalkthroughStep` — per-step body, using a tighter sampler (temp 0.1, top-p 0.5) for numeric fidelity, plus a `SpecsChipRow` of canonical torque/gap/pressure values pinned beside the body so drifted numbers in the prose are immediately visible against an authoritative reference.
- **Classification + RAG** (Will's branch, merged): `data/RulesEngine.kt` produces a deterministic `Classification` (confidence, likely cause, supporting signals) from an `OBDSnapshot`. `data/RagStore.kt` retrieves per-DTC context documents from `assets/rag/dtc_context.json` plus an always-applicable general document. Both feed into the synthesis prompt (`{supporting_signals}`, `{rag_context}`, `{engine_family}` fields).
- **Data layer**: `OBDDataSource` interface implemented by three sources selected at build time via `-PdataSource=FIXTURE|EMULATOR|BLUETOOTH` (`BuildConfig.DATA_SOURCE`).
  - `FixtureOBDDataSource` reads one of the bundled scenarios — `misfire.json` (P0301 Corolla) or `hilux_fuel_rail.json` (P0087 Hilux diesel) — selected by the `ACTIVE_FIXTURE` const in the file. One-line edit to switch.
  - `TcpOBDDataSource` + `BluetoothOBDDataSource` share an `Elm327Protocol.kt` ELM327 transport. The BLUETOOTH path requires runtime `BLUETOOTH_CONNECT` (API 31+); see `MainActivity.BluetoothGatedSnapshotViewer`.
- **DTC table**: `DTCTable.DEFAULT` carries three deep entries (P0301, P0087, P0171) with full title/subtitle/walkthrough/mechanic-draft/specs payloads. At app startup `CarCopilotApp` calls `DTCTable.DEFAULT.withThin(ThinDtcLoader.load(this))` to layer a 256-entry thin catalog from `assets/dtc_codes.json` on top — deep entries always win on conflict.
- **Performance** (Phase 8): `Engine` initializes in `appScope.async` at process start; a parallel `prewarmJob` sends a dummy "ok" turn and cancels at first token. Conversation slot hoisted across calls for KV-cache reuse. ~24% first-token latency win on Pixel 9.
- **Surface multiplexing** (Phase 10A): LiteRT-LM 0.11.0 allows one Conversation per Engine. `GemmaService.acquireConversationForSurfaceLocked(surface, sampler?)` closes and recreates the Conversation when a different surface acquires the slot (or when the per-step sampler differs). Each transition pays a system-prompt prefill.
- **JVM unit test suite** (`./gradlew test`, 158 tests): coverage spans the streaming JSON extractors (`SynthesisStateTest`, `MechanicDraftStateTest`, `HistoryPatternStateTest`, `WalkthroughPlanStateTest`, `WalkthroughStepStateTest`), the OBDSnapshot schema (`OBDSnapshotTest`), the snapshot-to-Issue builder (`IssueBuilderTest`), the DTC table contract (`DTCTableTest`), the deterministic classifier (`RulesEngineTest`), and the ELM327 protocol layer (`Elm327ProtocolTest`). The on-device Gemma smoke test (`app/src/androidTest/.../GemmaSmokeTest.kt`) is a separate lane gated on a model push to `/data/local/tmp/`. The emulator carries its own stdlib `unittest` suite at `emulator/test_obd_emulator.py`.

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
| What's been built (history) | `git log` (canonical); this file's "Current state" is a summary |
| Known issues + roadmap | `FUTURE_WORK.md` |
| Voice rules | `reference/prompts/system.md` |
| Visual design tokens | `reference/carcopilot_mockup_v10_light.html` `<style>` block (v09 dark is archived) |
| DTC classifier table (deep) | `app/src/main/java/com/example/carcopilot/data/DTCTable.kt` |
| DTC classifier table (thin, 256 codes) | `app/src/main/assets/dtc_codes.json` (loaded via `ThinDtcLoader`) |
| Issue data shape | `app/src/main/java/com/example/carcopilot/model/Schema.kt` |
| Classification shape | `app/src/main/java/com/example/carcopilot/model/Classification.kt` |
| Deterministic classifier | `app/src/main/java/com/example/carcopilot/data/RulesEngine.kt` |
| RAG context catalog | `app/src/main/assets/rag/dtc_context.json` (loaded via `RagStore`) |
| OBD adapter seam | `app/src/main/java/com/example/carcopilot/data/OBDDataSource.kt` |
| BLE / TCP transport | `data/BluetoothOBDDataSource.kt`, `data/TcpOBDDataSource.kt`, shared `data/Elm327Protocol.kt` |
| Live-generation surfaces | `app/src/main/java/com/example/carcopilot/inference/GemmaService.kt` |
| Prompt assembly + RAG retrieval | `app/src/main/java/com/example/carcopilot/inference/PromptBuilder.kt` |
| Fallback text | `model/Fallbacks.kt` (per-DTC map + `synthesizeFromClassification()`), `DTCTable` entries, `model/History.kt` |
| Prompts (consumed at runtime) | `app/src/main/assets/system.md`, `issue_synthesis.md`, `mechanic_draft.md`, `history_pattern.md`, `walkthrough_plan.md`, `walkthrough_step.md`. Originally mirrored from `reference/prompts/`; the assets versions have since added `{engine_family}`, `{supporting_signals}`, and `{rag_context}` template fields that the reference copies don't carry. **The assets versions are now the source of truth at runtime.** |
| Curated walkthrough procedures | `app/src/main/assets/walkthroughs/<CODE>.md` (P0301, P0087 today; wired via `PromptBuilder.procedures` map) |
| Walkthrough specs (chip row) | `DTCEntry.procedureSpecs` populated from the curated procedure files; rendered by `ui/components/SpecsChipRow.kt` |

## Conventions

- **Language:** Kotlin only. No Java.
- **Async:** Kotlin coroutines (`CoroutineScope(Dispatchers.IO)`, `LaunchedEffect`, `withContext`, `NonCancellable` where teardown must complete). No RxJava, no threads.
- **UI:** Jetpack Compose. Native composables, NOT a WebView. The HTML mockup at `reference/carcopilot_mockup_v10_light.html` is visual reference only — extract colors, fonts, spacing, animation timings from its CSS and build equivalent Compose components. The older `carcopilot_mockup_v09.html` (dark theme) is archived; its tokens are no longer current.
- **Navigation:** Single Activity with Compose `NavController`. Five destinations: `home`, `issue`, `walkthrough`, `draft`, `history`, all wired in `MainActivity`.
- **Fonts:** Geist and JetBrains Mono in `app/src/main/res/font/`, referenced via `FontFamily` in `ui/theme/Type.kt`. Both open source — Geist from Vercel, JetBrains Mono from JetBrains' GitHub.
- **Theme:** Apple HIG-influenced light palette in `ui/theme/Color.kt` with indigo (#6366F1) as the single primary action color. Tokens mirror the `:root` CSS variables in `reference/carcopilot_mockup_v10_light.html`. **Two-tone semantic discipline:** every status color has a Fill variant (`Accent`, `Severe`, `Healthy`, `Warning`) for backgrounds/bars/dots and an Inline variant (`AccentInline`, `SevereInline`, `HealthyInline`, `WarningInline`) for text on white. Fill values are illegible as body text on a light surface — never assign a Fill to a `Text` composable. `severity.accentColor()` returns the fill; `severity.accentInlineColor()` returns the inline companion. Brand warmth (the single amber dot in the brand mark) is its own token (`BrandWarmth`), reserved for that one decorative role. Material3 surfaces with overrides.
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

# Run the emulator's Python test suite (stdlib only, no pip install)
python3 emulator/test_obd_emulator.py

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

A standalone Python 3 TCP server lives at `emulator/obd_emulator.py` — speaks ELM327 AT commands over a socket so the transport layer can be developed without an ELM327 dongle and a car. Stdlib only, no `pip install`. Run with `python3 emulator/obd_emulator.py [--scenario corolla|hilux] [--port 35000]`. Scenarios and protocol coverage in `emulator/README.md`. The Android side connects via `TcpOBDDataSource`, selected at build time with `-PdataSource=EMULATOR`.

## Voice constraints (for prompts and fallback text)

Every user-facing string follows the voice rules in `reference/prompts/system.md` — that file is the source of truth:

- One thought per sentence.
- Concrete, not abstract.
- When you name a problem, say what to do about it.
- No engineer vocabulary: never "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."
- Imagine the user is a friend on the phone, not a developer.

If you find yourself writing canned fallback text that sounds like a developer wrote it, rewrite it as something a friend would say.

## Reference materials

`reference/prompts/*.md` and `reference/carcopilot_mockup_v10_light.html` are **authoritative** — voice rules and design tokens. Treat them as read-only unless the change is intentional. `reference/carcopilot_mockup_v09.html` is **archived** (dark theme, superseded by v10_light) and kept only for the history of why the original tokens were shaped the way they are.

Other docs under `reference/` are **historical or mixed** — each carries a STATUS banner at the top describing what's still live vs. what's archaeology. They're kept around for the *why* behind decisions (e.g., why LiteRT-LM and not AICore, why Compose-native and not WebView). Don't take any specification in those files as current without checking the banner.

## Out of scope (do not work on without asking)

- Real history persistence (`History.ENTRIES/STATS/PATTERN` is in-memory fixture; future seam is a `HistoryRepository`)
- Spanish translation
- Push notifications, lockscreen integration
- Settings, vehicle selection, locale switching
- Mockup HTML refactoring (read-only design source of truth)
- Constrained / template-based decoding for numeric fidelity (the `SpecsChipRow` film-around is the shipped mitigation; deeper fix is a model/SDK-level change — see FUTURE_WORK)
