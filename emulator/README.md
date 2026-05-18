# CAR·COPILOT

> A friend-on-the-phone car mechanic that runs **entirely on your Android phone** — making car diagnostics accessible **regardless of income or internet**. No cloud calls. No location services. No subscription. Powered by **Gemma 4** running locally via Google AI Edge's **LiteRT-LM**.

**Built for the Gemma 4 Good Hackathon.** Impact Track: **Digital Equity & Inclusivity**. Also targets Special Technology Track: **LiteRT**.

[Video (3 min)](https://youtu.be/TBD) · [Live demo / APK](https://github.com/DigitalVeer/car-copilot/releases) · [Writeup](submission/WRITEUP.md) · [Engineering notes](reference/perf_notes_2026-05-18.md)

## Why it matters

Three barriers decide who gets a good answer when their check-engine light comes on: **money** (a $200 dealer diagnostic), **internet** (every consumer car-AI app is a cloud API behind a subscription paywall), and **knowledge** (the answer comes back in dialect to someone who just wants to know if it's safe to drive).

Those barriers stack hardest where it matters most — in tunnels, parking garages, rural roads, and developing markets where mid-90s Corollas outnumber smartphones and the dealer network doesn't reach. The most useful place to put a car-savvy friend is exactly where cloud LLMs don't work. CAR·COPILOT puts that friend on the phone — and lives on the phone. The whole product is one APK and one ~3.4 GB model file. After that, it works anywhere, for anyone, forever.

---

## What it does

Plug any OBD-II reader into your car. Open CAR·COPILOT. In airplane mode, a Pixel 9 reads your car's fault codes and live sensor data, classifies the problem, and streams a plain-English diagnosis in a friend-on-the-phone voice. Then it walks you through fixing it.

| Screen | What it streams |
|---|---|
| **Home** | Trip-readiness verdict + the day's issues, severity-coded |
| **Issue** | Two-paragraph diagnosis, cost / time / DIY-difficulty, evidence drawer |
| **Walkthrough** | Per-step repair procedure with canonical torque/gap/pressure pinned beside the body |
| **Mechanic draft** | Codes-and-evidence handoff message you paste into a text to a shop |
| **History** | The pattern across past issues ("two coil failures in seven months — likely valve cover gasket") |

## The engineering thesis: Gemma never classifies

```
OBDSnapshot → RulesEngine → Classification + RagStore → PromptBuilder → GemmaService (5 surfaces) → JSON-aware extractors → UI (fail-soft fallback)
```

`data/RulesEngine.kt` is the deterministic classifier. It owns severity, route, confidence, likely cause, supporting signals, cost, and time — every safety-relevant field. `data/DTCTable.kt` holds 256 thin DTC entries plus 16 deeply-curated procedures. `data/RagStore.kt` injects per-DTC context.

**Only then does Gemma narrate.** Five streaming surfaces, each with its own prompt template (`app/src/main/assets/*.md`), per-surface sampler, JSON envelope, and a stateful extractor that pulls partial content from the streaming buffer without ever letting a half-finished escape sequence reach the user. Every surface has a canned-text fallback path — if LiteRT-LM throws, the user still sees text.

## How we used Gemma 4 on LiteRT

| Concern | Where it lives |
|---|---|
| LiteRT-LM Engine + Conversation multiplexing | [`inference/GemmaService.kt`](app/src/main/java/com/example/carcopilot/inference/GemmaService.kt) |
| Per-surface prompt assembly + RAG retrieval | [`inference/PromptBuilder.kt`](app/src/main/java/com/example/carcopilot/inference/PromptBuilder.kt) |
| Streaming JSON extractors (per surface) | `ui/SynthesisState.kt`, `ui/MechanicDraftState.kt`, `ui/HistoryPatternState.kt`, `ui/WalkthroughPlanState.kt`, `ui/WalkthroughStepState.kt` |
| Deterministic classifier (Gemma's guardrail) | [`data/RulesEngine.kt`](app/src/main/java/com/example/carcopilot/data/RulesEngine.kt) |
| UI contract (the Issue schema) | [`model/Schema.kt`](app/src/main/java/com/example/carcopilot/model/Schema.kt) |
| Fallback text (per-DTC) | [`model/Fallbacks.kt`](app/src/main/java/com/example/carcopilot/model/Fallbacks.kt) |
| LiteRT manifest entries | [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) (`<uses-native-library>` for OpenCL) |

Highlights:

- **Capability-gated Multi-Token Prediction (MTP).** `ExperimentalFlags.enableSpeculativeDecoding` flips on at init only if `Capabilities(modelPath).hasSpeculativeDecodingSupport()` returns true. +23–60% decode TPS depending on surface.
- **SDK-authoritative benchmarking.** `ExperimentalFlags.enableBenchmark = true` + `Conversation.getBenchmarkInfo()` logged per call. Surfaces `init / ttft / prefill_tokens / decode_tokens / prefill_tps / decode_tps` to `adb logcat -s CarCopilot:V`. Made every subsequent perf change measurable — and revealed that prefill, not decode, dominates wall-clock cost on every surface.
- **Per-step phase splitting.** `PromptBuilder.splitProcedureIntoPhases` parses curated procedure markdown at `## Phase N` boundaries and ships intro + indexed phase per step instead of the full document. −21% prefill per step, ~30+ seconds saved across a 6-step walkthrough.
- **Per-surface samplers.** Synthesis / draft / history / plan use `topK = 40, topP = 0.95, temperature = 0.3` for friend-on-the-phone voice. Walkthrough-step drops to `topP = 0.5, temperature = 0.1` for numeric fidelity (the prompt must paraphrase torque and gap values verbatim).
- **Surface multiplexing under the single-Conversation-per-Engine constraint.** `GemmaService.acquireConversationForSurfaceLocked` closes and recreates the Conversation on surface switch, with a 250 ms native-cleanup floor + `convoMutex` to mitigate a SIGSEGV inside `liblitertlm_jni.so` we hit on rapid churn.
- **Prewarm + cancel-after-first-token.** Parallel coroutine at process start sends a few-shot voice anchor; cancels on the first emitted token. Pays the system-prompt prefill while the user reads the Home screen. ~24% first-token latency win on the Issue page.
- **JSON-aware tolerant parsing.** `WalkthroughPlanState.balanceJsonTail` walks the streamed buffer with string-aware brace/bracket tracking and closes any unclosed structure at the tail, so cancelled-mid-stream or model-stopped-early output parses instead of bouncing to fallback. (Pre-fix, this fallback was firing silently 100% of the time.)

## Numbers (Pixel 9, GPU backend, Gemma 4 E4B)

| Surface | Decode TPS | Prefill tokens | TTFT |
|---|---|---|---|
| Synthesis | 6.15–7.05 | 1,449 | 10.2s |
| Mechanic draft | 8.08 | ~1,200 | ~7s |
| Walkthrough plan | 9.02–11.02 | 2,247 | 15.9s |
| Walkthrough step | 6.84–10.22 | ~2,100 | ~11.7s |

Full perf writeup: [`reference/perf_notes_2026-05-18.md`](reference/perf_notes_2026-05-18.md).

## Testing

- **179 JVM unit tests** (`./gradlew test`) covering the streaming JSON extractors, procedure phase splitter, OBDSnapshot schema, snapshot-to-Issue builder, DTC table contract, deterministic classifier, and ELM327 protocol layer.
- **On-device Gemma smoke test** at `app/src/androidTest/.../GemmaSmokeTest.kt`, gated on a model push to `/data/local/tmp/`.
- **Python OBD emulator** at `emulator/obd_emulator.py` with its own stdlib `unittest` suite — `python3 emulator/test_obd_emulator.py`.

## Hard constraints

- **No cloud API calls.** All inference runs on the device via LiteRT-LM. This is the central product claim.
- **No location services.** `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` are never requested. The Bluetooth permission uses the `usesPermissionFlags="neverForLocation"` carve-out.
- **No internet permission for AI features.** No AI request ever leaves the device.
- **No mode 04** (clear DTCs). The app does not expose this.
- **Gemma never classifies.** `DTCTable` + `RulesEngine` are the source of truth for severity, route, cost, time. Gemma only generates narrative text on top.
- **Fail soft.** If LiteRT-LM errors, throws, or returns unparseable text, every surface falls back to canned text. The app must never crash on the user.

---

# Build & install

## Prerequisites

- **Android Studio** Iguana (2023.2.1) or newer. Anything that bundles a recent AGP 8.x and JDK 21+ works.
- **JDK 21+.** Android Studio ships its own JBR — point Gradle at it (Settings → Build → Build Tools → Gradle → Gradle JDK), or set `JAVA_HOME` to that JBR path if you build from the command line.
- **ADB on `PATH`.** Standard install location is `%LOCALAPPDATA%\Android\Sdk\platform-tools\` on Windows or `~/Library/Android/sdk/platform-tools/` on macOS.
- **A physical Android 8.0+ device with USB debugging authorized.** The reference target is a Pixel 9; any Tensor or Snapdragon-8-class device with an OpenCL-capable GPU should work. Emulators won't — the GPU delegate needs a real driver.

## Get the source

```bash
git clone https://github.com/DigitalVeer/car-copilot.git CarCopilot
cd CarCopilot
```

Open the project root in Android Studio. Wait for the Gradle sync to finish — the first sync downloads Compose BOM, LiteRT-LM, and the Kotlin toolchain.

## Get the model

The app needs **`gemma-4-E4B-it.litertlm`** (~3.41 GB). It is not in git.

1. Download from the [litert-community Hugging Face repo](https://huggingface.co/litert-community). Get the **`.litertlm`** file, *not* the `.task` file — `.task` is the web build and will not load via the Android SDK.
2. Push to the device:
   ```bash
   adb push gemma-4-E4B-it.litertlm /data/local/tmp/
   ```
3. On first launch, `CarCopilotApp.onCreate` constructs `GemmaService`, which copies the model from `/data/local/tmp/` into the app's private `filesDir`. Subsequent launches reuse the `filesDir` copy.

## Build and install

```bash
# Build the debug APK (default: E4B)
./gradlew assembleDebug

# Build with the E2B variant for measurement
./gradlew assembleDebug -PmodelVariant=E2B

# Install on the connected device
adb install -r -d app/build/outputs/apk/debug/app-debug.apk

# Tail runtime logs (project-tagged messages only)
adb logcat -s CarCopilot:V

# Run JVM unit tests
./gradlew test
```

First launch after install copies the model (~10–20s) and warms the GPU shader cache (~30–60s). The engine starts warming in `CarCopilotApp.onCreate` while the user sits on Home, so by the time they tap into the Issue page the prefill is already paid.

## Where to look next

- [`CLAUDE.md`](CLAUDE.md) — project guardrails, architecture, current state, what's in-scope and out-of-scope.
- [`FUTURE_WORK.md`](FUTURE_WORK.md) — known issues (including the SIGSEGV mitigation) and the post-demo backlog.
- [`reference/perf_notes_2026-05-18.md`](reference/perf_notes_2026-05-18.md) — Gemma inference perf measurements and what's been tuned. Read this before changing anything in `GemmaService.kt`.
- [`reference/prompts/*.md`](reference/prompts/) — voice rules (`system.md`) and per-surface prompt templates.
- [`submission/WRITEUP.md`](submission/WRITEUP.md) — the hackathon writeup.

## License

[TBD — recommend MIT or Apache 2.0 for the hackathon submission]
