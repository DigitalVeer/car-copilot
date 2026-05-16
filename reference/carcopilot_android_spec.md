# CAR·COPILOT — Phase 5 spec · Android pivot

**Status:** active · **Scope:** minimum viable on-device Gemma demo for hackathon submission · **Deadline:** Sunday evening (hard checkpoint Saturday evening for go/no-go)

This document supplements `carcopilot_design.md`. The Python work in Phases 1-2 is the reference implementation; this phase ports the load-bearing parts to Android and runs Gemma on-device via Mediapipe LLM Inference. The deliverable is one Android APK that demonstrates a single end-to-end flow with Gemma generating synthesis text locally on the phone.

---

## 1. Goal

Demonstrate the central product claim — *Gemma running on-device, no cloud, on a phone you might already own* — with one real flow recorded for the hackathon video.

**Non-goal:** A complete app. We're building enough to honestly say "Gemma is running on the phone in this demo," not a shippable product.

## 2. Minimum demo flow (the only flow we build)

Single flow, single scenario:

1. User opens the app. Sees Home screen with the misfire issue card (data is canned — same `misfire.json` fixture content).
2. User taps the card. Navigates to the Issue page.
3. The AI strip on the Issue page shows a typing animation, then resolves to **Gemma-generated synthesis text generated live on the device** for this specific issue.
4. Below the AI strip: card meta, evidence toggle — all from canned data, all static.
5. User can tap evidence toggle to reveal DTCs + live readings.
6. End of demo. Walkthrough, pre-flight, mechanic draft are visible in the mockup but not wired up.

That's it. One Gemma call, on-device, visible to the camera. Everything else stays static.

## 3. What ports from Python (don't rebuild)

| Python artifact | Android equivalent |
|---|---|
| `prompts/system.md` + `prompts/issue_synthesis.md` | Bundled as `res/raw/` files, concatenated at runtime |
| `Issue`, `IssueMeta`, `DTC`, `LiveReading` schema | Kotlin `data class` definitions in `model/` package |
| `tests/fixtures/obd_snapshots/misfire.json` | `res/raw/misfire.json`, parsed at startup |
| `dtc_table.py` (just the P0301 entry for now) | A Kotlin object literal — one entry is enough |
| `fallback.py` synthesis for misfire | A Kotlin constant string in `model/Fallbacks.kt` |
| `gemma_adapter.py` JSON-output handling | LiteRT-LM call with response parsing in `inference/GemmaService.kt` |
| `carcopilot_mockup_v09.html` | **Visual reference only.** Extract color values, font weights, spacing, animation timings from the CSS — do NOT bundle the HTML. The UI is native Compose. |

**Critical: the UI is native Jetpack Compose, not a WebView.** The HTML mockup at `reference/carcopilot_mockup_v09.html` is a visual reference — the agent reads it to understand what colors, fonts, spacing, and animation feel match the design. The agent then builds equivalent native Compose components that match the look as closely as practical. No WebView, no JS bridge, no `evaluateJavascript`. The Gemma call is a direct coroutine call from the tap handler.

Compose composables to build (minimum for the misfire flow):
- `CarCopilotTheme` — dark theme with custom palette + Geist/JetBrains Mono fonts from `res/font/`
- `HomeScreen` — top bar, AI strip with animated dots, issue card (tappable), "Also" rows, bottom tab bar
- `IssueScreen` — top bar with back, AI strip (animates while LiteRT-LM is running), issue card with meta, evidence toggle expanding to DTCs + live readings list, bottom tab bar
- `ThinkingDots` — three-dot infinite-transition composable for the AI strip "thinking" state
- `AnimatedAIStrip` — wraps thinking + text states, fades in real text when inference completes

Screens that exist in the mockup but are NOT built in Phase 5 (visible in mockup, not built in Android):
- Pre-flight, Walkthrough, Severe Issue page, Drafted Message, Healthy state, History tab

The home card's CTA tap navigates to IssueScreen via a NavController. No other navigation is wired up for Phase 5.

## 4. Technical stack

- **Language:** Kotlin
- **UI:** Jetpack Compose. Single-Activity app with a NavController routing between HomeScreen and IssueScreen. No WebView.
- **LLM runtime:** LiteRT-LM (the rebrand of MediaPipe LLM Inference for the Gemma 4 generation). Gradle dependency `com.google.ai.edge.litertlm:litertlm-android:0.11.0`. Package `com.google.ai.edge.litertlm.*` — classes `Engine`, `EngineConfig`, `Backend`, `Conversation`, `ConversationConfig`, `SamplerConfig`, `Message`, `Contents`. **Note:** earlier drafts of this spec said the SDK was still under `com.google.mediapipe.tasks.genai` — that namespace exists but is being deprecated. Use the LiteRT-LM artifact above.
- **Model:** Gemma 4 in `.litertlm` format (the Android/iOS/desktop build). The `.task` files in the same Hugging Face repos are the **web** build and will not load via the Android SDK. E4B (`gemma-4-E4B-it.litertlm`, ~3.41 GB) is the consistency target — tuned against `gemma4:e4b` on Ollama in Phase 2. Fall back to E2B if E4B is too slow on the target device.
- **Manifest requirements:** the GPU backend needs the app to declare optional vendor libraries via `<uses-native-library>` inside `<application>`:
  ```xml
  <uses-native-library android:name="libvndksupport.so" android:required="false"/>
  <uses-native-library android:name="libOpenCL.so" android:required="false"/>
  ```
  Without these, OpenCL fails to dlopen on Android 12+ and the runtime falls back to OpenGL (slower) or fails outright. Confirmed on Pixel 9 (Mali-G715) — with the declarations, OpenCL loads cleanly.
- **Model staging:** the GPU delegate writes a sidecar weights cache next to the model file. `/data/local/tmp/` is not writable by the app's UID, so the model must live in the app's private `context.filesDir` (or a subdirectory) before `Engine.initialize()`. Either download directly into `filesDir`, or stage from `/data/local/tmp/` on first run with a size-match check.
- **Why not AICore:** AICore's ML Kit Prompt API does not support structured JSON output as of the current Developer Preview. Per recent engineering case studies, models routed through AICore "frequently add markdown code fences, mix natural language with JSON, or translate JSON keys" because AICore exposes no sampler configuration. LiteRT-LM gives us the sampler + prompting control needed to enforce schema adherence.
- **Min SDK:** 26 (Android 8.0). Pixel 9 is on Android 14+.
- **Architecture:** Activity → ComposeNavHost → HomeScreen (Compose) → tap card → IssueScreen (Compose) → `LaunchedEffect` collects a `Flow<String>` from `GemmaService.streamSynthesis()` → each delta appends to the running text → on flow completion, parse the assembled JSON → state hoisted back into Compose, text appears live in the AI strip.

## 5. Go/no-go checkpoints

**Checkpoint A (Friday end of day): can LiteRT-LM load Gemma 4 on the target device and produce reliable JSON?**

Model availability is confirmed: Gemma 4 E2B and E4B are both packaged as `.task` files in the litert-community repositories. The checkpoint is no longer about "does the model exist" — it's about "does it run on our actual device, and does it produce JSON we can parse."

Steps:

1. Download the `.litertlm` file from `litert-community` on Hugging Face. Try E4B first (consistency with the Ollama-tuned prompts) — `gemma-4-E4B-it.litertlm` is ~3.41 GB. The repo also has a `gemma-4-E4B-it-web.task` (~2.96 GB) but that's the **web** build — do not download it for Android use.
2. Write a 30-line standalone Android sample (an instrumented test is fine — it isolates the smoke path from the production app code) that loads the model via LiteRT-LM and runs **the actual `issue_synthesis.md` prompt** filled with the misfire fixture's data. Don't just test "any prompt works" — test the one we'll ship.
3. Verify two things from the output:
   - **JSON parses cleanly.** No markdown fences, no leading prose, no key renaming. If parse fails, this is the gotcha the arXiv paper warned about — adjust the prompt to be more explicit about JSON-only output, lower temperature, set top-k conservatively.
   - **Synthesis matches the voice we tuned.** Compare the on-device output against the Ollama output you committed in Phase 2's voice tuning pass 3. They should read similarly. If the on-device version regresses noticeably (more clinical, more verbose, code-leakage returning), the prompts may need a light pass specifically for the LiteRT-LM runtime — different sampler defaults can shift output character even with the same prompt.

If both pass: checkpoint A is green, proceed to scaffolding the app.
If JSON parsing is flaky but synthesis quality is fine: add a tolerant JSON parser (strip ```json fences, strip leading non-JSON, retry up to 2x with stricter prompting) and proceed.
If synthesis quality regresses badly: do one voice-tuning pass against the on-device model before scaffolding the app — it's faster to fix now than after the UI is wired up.
**If the model won't load at all** (memory, OS version, hardware): fall back to Option B (honest Python reframing) immediately. Don't burn Saturday.

**Checkpoint B (Saturday end of day): can the WebView + JS bridge round-trip a real Gemma call?**

By Saturday evening, the app should:
- Launch and show the HTML mockup
- Respond to a tap on the issue card by triggering the synthesis call
- Display the Gemma-generated text in the AI strip (real, not canned)

**If checkpoint B fails: ship Option B fallback Sunday morning.** Use the Saturday-evening state as the "this is what we built" demo, with narration explaining the gap honestly.

## 6. Build order for the agent

```
Day 1 (Friday):
  - Checkpoint A: LiteRT-LM + Gemma 4 smoke test (no app yet, just verify the model loads
    and produces parseable JSON for the actual issue_synthesis prompt)
  - If A passes: scaffold Android Studio project (single Activity, Compose, NavController)
  - Set up the Compose theme: dark palette (extract from mockup CSS variables),
    Geist + JetBrains Mono fonts in res/font/, color tokens matching --accent, --severe, etc.

Day 2 (Saturday):
  - Build HomeScreen composable matching mockup home-multi state
  - Build IssueScreen composable matching mockup issue page
  - Build ThinkingDots + AnimatedAIStrip composables for the AI strip
  - Implement GemmaService wrapping LiteRT-LM with JSON parsing + fallback
  - Wire IssueScreen LaunchedEffect to GemmaService
  - Test on real device end-to-end: tap card, see thinking, see real Gemma text
  - Checkpoint B at end of day

  HARD SCHEDULE GUARD: by Saturday 3pm, if HomeScreen + IssueScreen aren't both
  rendering with the AI strip showing live Gemma text, drop styling polish.
  Use system fonts, plain dark backgrounds, default Compose spacing. The Gemma
  moment matters more than the polish — judges care that it's on-device, not
  whether the font matches the mockup.

Day 3 (Sunday):
  - Record demo on real device with camera
  - Edit video
  - Write submission

Fallback at any checkpoint failure:
  - Switch to Option B (Python backend + mockup, honest reframing)
  - Use what was built up to that point as the writeup's "architecture proof"
```

## 7. Compose invocation pattern

The Gemma call lives in `GemmaService` and streams via `sendMessageAsync` — verified in Checkpoint A to emit per-token deltas (each `Message` is new text, not cumulative). Compose code must append. Steady-state ~5 tok/s after ~6s first-token latency on Pixel 9.

```kotlin
class GemmaService(private val engine: Engine, private val systemPrompt: String) {
  /** Streams the assistant's response as token deltas. */
  fun streamSynthesis(issue: Issue): Flow<String> {
    val convo = engine.createConversation(
      ConversationConfig(
        systemInstruction = Contents.of(systemPrompt),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
      )
    )
    val prompt = buildPrompt(issue)  // concatenates system.md context + issue_synthesis.md
    return convo.sendMessageAsync(prompt)
      .map { it.toString() }
      .onCompletion { convo.close() }
  }
}

sealed interface SynthesisState {
  data object Thinking : SynthesisState
  data class Streaming(val partial: String) : SynthesisState
  data class Ready(val synthesis: String, val goodNews: String?, val isFallback: Boolean) : SynthesisState
}

// In IssueScreen.kt:
@Composable
fun IssueScreen(issue: Issue, gemma: GemmaService) {
  var state by remember { mutableStateOf<SynthesisState>(SynthesisState.Thinking) }
  LaunchedEffect(issue.id) {
    val buf = StringBuilder()
    try {
      gemma.streamSynthesis(issue).collect { delta ->
        buf.append(delta)
        state = SynthesisState.Streaming(buf.toString())
      }
      state = parseOrFallback(buf.toString(), issue)
    } catch (e: Exception) {
      state = SynthesisState.Ready(FALLBACK_SYNTHESIS_MISFIRE, null, isFallback = true)
    }
  }
  AnimatedAIStrip(state)
}
```

If the LiteRT-LM call throws or the assembled response can't be parsed as JSON, fall back to `FALLBACK_SYNTHESIS_MISFIRE` (the canned string ported from `fallback.py`). The composable transitions to `SynthesisState.Ready(fallbackText, isFallback=true)` and continues rendering normally — the demo never visibly breaks.

For JSON tolerance: parse the **assembled** string after the flow completes, not the incremental partials. Strip ```json fences if present, strip leading non-JSON prose, locate the outermost `{...}` block, parse. If still failing, fall back. This mirrors the Python `gemma_adapter.py` retry/fallback strategy.

## 8. What's explicitly out of scope (don't build, don't promise)

- Real Bluetooth OBD-II integration — Pixel reads the canned `misfire.json` fixture from assets at startup.
- Walkthrough text generation — the Walkthrough screen in the mockup is static; the demo doesn't navigate there.
- Mechanic draft generation — same.
- History tab — same.
- Spanish translation — same.
- Push notifications, lockscreen integration — same.
- Multiple scenarios (overheat, healthy) — single misfire only.
- Settings, vehicle selection, anything that adds a screen.

## 9. What the demo video shows (Sunday recording)

- The Pixel 9 in someone's hand. The phone is real, the camera frames it tight.
- Open app. Home screen visible (real, in WebView).
- Tap card. Navigate to Issue page.
- Typing animation runs. **This is the moment Gemma is computing on-device.**
- Synthesis text appears. **This text was generated live by Gemma on this phone, no internet.**
- Tight shot on the screen as the user reads it. Voiceover explains what just happened.
- Tap evidence toggle. Show the codes for credibility.
- Cut.

Total demo runtime: 30-45 seconds of phone footage. The rest of the 3-minute video is context (Maria persona, architecture, polymorphic UI principle shown via the mockup), recorded separately.

## 10. Honest disclosures for the writeup

Whatever Android state we end up shipping, the writeup must be explicit about:
- Which model is actually running on the phone (Gemma 4 if checkpoint A passed, Gemma 3 e4b otherwise — name it)
- Which flows are live vs. mockup (just the synthesis call is live; rest of UI is the HTML mockup)
- What's roadmap (real BT, walkthrough on-device generation, history, Spanish, the rest)

The submission's strength is *the architecture* — deterministic classification + on-device generative voice + local fallback. Even a minimal Android demo proves that architecture works on a phone. That's the honest claim.
3