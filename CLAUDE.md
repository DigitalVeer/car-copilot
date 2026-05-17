# CLAUDE.md — CAR·COPILOT · Android

## What this is

This is the Android app for **CAR·COPILOT**. The app runs on-device Gemma 4 via LiteRT-LM and streams diagnostic synthesis text live with no internet required. The target user is a vehicle owner in a developing market (East Africa, SE Asia) who needs real diagnostic intelligence without connectivity or money for a mechanic.

Phases 5–9 are **complete and committed** — the app launches, navigates all five screens, runs on-device Gemma 4 E4B via LiteRT-LM, streams synthesis and mechanic draft text live, and has a fully polished UI matching the HTML mockup. We are now building the real OBD-II data pipeline on top of this foundation.

**Read these before doing anything else:**
- `angaza_context.md` — product context, target market, hardware BOM, demo scenarios, build priorities. Design doc, not gospel — discuss before treating anything in it as fixed.
- `reference/carcopilot_android_spec.md` — Phase 5 architecture. Use for understanding what's been built.
- `reference/carcopilot_phase_6_polish.md` — visual polish spec (complete, for reference only).
- `reference/carcopilot_mockup_v09.html` — visual design source of truth. The `<style>` block has all design tokens.
- `reference/carcopilot_design.md` — Python technical design. Sections 4 (schema) and Appendix A (prompts) are useful. Ollama / Docker / pytest sections are NOT applicable here.

---

## What's been built (do not redo this work)

| Phase | What | Status |
|---|---|---|
| 5 | On-device Gemma streaming, SynthesisState machinery, NavHost | ✅ Done |
| 6 | Full visual polish — custom palette, Geist + JetBrains Mono, AI strip, cards, animations | ✅ Done |
| 7 | WalkthroughScreen, MechanicDraftScreen, HistoryScreen | ✅ Done |
| 8 | Performance — hoisted Conversation lifetime, eager prewarm | ✅ Done |
| 9 | Mockup spacing fidelity | ✅ Done |
| — | VehicleState schema (OBD pipeline input contract) | ✅ Done |
| — | Unit tests (34 tests, JVM, no device needed) | ✅ Done |

---

## What we're building now — Phase 10: OBD data pipeline

Work in this order — each unblocks the next:

1. **Python OBD emulator** — TCP server serving ELM327 AT command responses over WiFi. Android connects via WiFi socket instead of Bluetooth. One config flag (`DataSource`) switches transports.
2. **OBD transport layer** — `OBDTransport` interface with `TcpTransport` and `BluetoothSPPTransport` implementations. `OBDService` sends AT commands, parses PID responses, emits `Flow<VehicleState>`.
3. **RAG seed data** — `assets/rag/dtc_common.json` with the 15 research-backed DTC scenarios (Toyota 2KD-FTV diesel + 1ZZ-FE petrol).
4. **Rules engine** — pure Kotlin, no Android dependencies. Takes `VehicleState`, looks up RAG, produces `Classification` deterministically.
5. **Prompt rebuild** — update `PromptBuilder` to take `VehicleState` + `Classification` instead of hardcoded `Issue`.
6. **Wire together** — `OBDService` → `VehicleState` → rules engine → Gemma → `Issue` → existing screens (screens do not change).

Do not start #2 without #1 working end-to-end. Do not start #5 without #3 and #4.

### Pipeline architecture

```
OBDTransport (TcpTransport | BluetoothSPPTransport)
    ↓
OBDService → Flow<VehicleState>
    ↓
Rules engine + RAG lookup (assets/rag/*.json)
    ↓
Classification (deterministic — no AI involved)
    ↓
PromptBuilder → Gemma prompt string
    ↓
GemmaService → Flow<String> (streaming tokens)
    ↓
SynthesisState → Issue → existing screens (unchanged)
```

**Key principle:** Gemma does not diagnose. The rules engine diagnoses. Gemma translates structured output into plain language in the user's locale. This is a task a small quantized model can do reliably.

---

## VehicleState — the pipeline input contract

`VehicleState` (`model/VehicleState.kt`) is the single object the rules engine, prompt builder, and UI badge logic read from. It is produced by `OBDService` and is the only thing that crosses between the OBD layer and the AI layer.

**Critical — diesel and petrol PIDs are mutually exclusive:**
- Never put STFT/LTFT in a diesel scenario — diesel engines have no O2 feedback trim loop
- Never put rail pressure in a petrol scenario
- Always set `engineFamily` explicitly — the rules engine uses it to distinguish structurally absent fields from unread ones

**Three data sources — always set `source` correctly:**
- `DataSource.FIXTURE` — hardcoded Kotlin data, no hardware
- `DataSource.EMULATOR` — Python TCP emulator over WiFi
- `DataSource.BLUETOOTH` — real ELM327 dongle over Classic BT SPP

---

## Hard constraints — never violate

- **Offline must work fully and independently.** The Gemma competition requires demonstrable on-device inference — judges must see Gemma running without internet. The demo story is: show it working offline, then switch to online mode live to show enhanced capabilities. Both modes must work for the demo. Design `GemmaService` so an online inference path can be added without rearchitecting — but the offline path must never depend on connectivity.
- **No location permission in the manifest (MVP).** Location is not needed for any current feature. For Bluetooth, require the user to pre-pair the ELM327 dongle in system settings and use `BLUETOOTH_CONNECT` — this avoids needing `ACCESS_FINE_LOCATION` for BT scanning on older APIs. Future online features (nearby mechanics, regional pricing) will need location — don't permanently block it, just don't add it until there's a feature that uses it. Use the literal string `"my current location"` in prompts where location context is needed.
- **No mode 04** (clear DTCs). The agent does not expose this.
- **Gemma never classifies.** The rules engine + RAG are the source of truth for severity, route, cost, time. LiteRT-LM only generates narrative text.
- **Issue schema is the contract.** Kotlin data classes mirror `reference/python-src/schema.py`. Adding or removing fields requires explicit approval.
- **Fail soft.** If LiteRT-LM errors, throws, or returns unparseable text, fall back to the canned synthesis string. The app must never crash on the user.
- **`./gradlew test` must pass before pushing to main.** No exceptions.

---

## Phase 5 functional contract — do not modify without discussion

These are committed and working. Do not change them:

- `GemmaService.kt` — engine init, model staging to filesDir, streaming inference
- `SynthesisState.kt` including `extractSynthesisInProgress` — JSON-aware streaming text extractor. Preserves Thinking → Streaming → Ready transitions.
- `MechanicDraftState.kt` including `extractDraftInProgress` — same pattern for mechanic draft surface.
- `CarCopilotApp.onCreate` — appScope.async init pattern
- `MainActivity` and NavHost routing — five destinations: `home`, `issue`, `walkthrough`, `draft`, `history`
- The `LaunchedEffect` collect blocks in `IssueScreen` and `MechanicDraftScreen`
- Resilience paths to `Ready(fallback, isFallback=true)` — all paths stay wired
- DTC table content, prompt files in assets, fixture file content

If something in this list needs to change, stop and discuss first.

---

## Test gate

```bash
# Must pass before any push to main — run from project root
./gradlew test
```

34 unit tests, JVM only, no device, ~5 seconds. Tests live in `src/test/`. Add a test for any new parsing function, schema field, or state machine. Never delete a test.

`src/androidTest/GemmaSmokeTest` requires a physical device with the Gemma model staged — hardware validation only, not a push gate.

---

## Conventions

- **Language:** Kotlin only. Do not use Java.
- **Async:** Kotlin coroutines (`CoroutineScope(Dispatchers.IO)`, `LaunchedEffect`, `withContext`), not RxJava, not threads.
- **UI:** Jetpack Compose. Native composables, NOT a WebView wrapper. The HTML mockup at `reference/carcopilot_mockup_v09.html` is visual reference only — do not bundle the HTML in the app.
- **Navigation:** Single Activity with Compose `NavController`. Five destinations: `home`, `issue`, `walkthrough`, `draft`, `history`. All already exist and are wired.
- **Fonts:** Geist and JetBrains Mono bundled in `res/font/`. Both open source.
- **Theme:** `CarCopilotTheme` wrapping `NavHost` in `MainActivity`. Custom dark palette from mockup CSS variables. Do not use Material3 defaults or generated palettes.
- **LLM SDK:** `com.google.ai.edge.litertlm:litertlm-android:0.11.0`. Classes: `Engine`, `EngineConfig`, `Backend`, `Conversation`, `ConversationConfig`, `SamplerConfig`, `Message`, `Contents`.
- **Model format:** `.litertlm` (Android/iOS/desktop build). The `.task` files are the web build and will not load via the Android SDK. E4B = `gemma-4-E4B-it.litertlm` (~3.41 GB). E2B = `gemma-4-E2B-it.litertlm` (~1.3 GB).
- **Manifest:** GPU backend requires two `<uses-native-library>` entries inside `<application>`:
  ```xml
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  ```
- **Model staging:** model lives in `context.filesDir` before `Engine.initialize()`. Already wired in `CarCopilotApp.onCreate` — do not change.
- **Streaming:** `sendMessageAsync(String): Flow<Message>` emits per-token deltas (not cumulative). Confirmed empirically in Checkpoint A. Compose collectors append, never replace. Steady-state ~5 tok/s after ~6s first-token latency on Pixel 9.
- **Min SDK:** 26 (Android 8.0). Target SDK: 36. Package: `com.example.carcopilot` — do not rename.
- **No comments** unless the WHY is non-obvious. Never describe what the code does.

## Build commands (run from project root)

```bash
# Run unit tests (no device needed)
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

# Inspect runtime logs
adb logcat -s CarCopilot:V

# Uninstall (for clean reinstall)
adb uninstall com.example.carcopilot

# Verify device is connected
adb devices

# Switch to E2B model at build time
./gradlew assembleDebug -PmodelVariant=E2B
```

The target demo device is a Pixel 9. Always install via `adb install`, never drag-and-drop APK.

---

## Voice constraints (prompts and user-facing strings)

Every user-facing string must follow the voice rules in `reference/prompts/system.md`:

- One thought per sentence.
- Concrete, not abstract.
- When you name a problem, say what to do about it.
- No engineer vocabulary: never "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."
- Imagine the user is a friend on the phone, not a developer.

The voice-tuned prompts in `reference/prompts/` are the highest-value artifact in this project — they took real iteration to produce. Don't rewrite them; bundle them as-is.

---

## Notes

- `FUTURE_WORK.md` tracks post-hackathon performance work (E2B swap, prefill caching, unbounded context, single-Conversation constraint). Do not pursue now.
- The mockup HTML is the design system source of truth for any future UI work. Open it in a browser side-by-side with the running app — visual mismatches show up immediately.
- Deadline: May 18, 2026.
