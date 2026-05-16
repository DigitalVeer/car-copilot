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
| `prompts/system.md` + `prompts/issue_synthesis.md` | Bundled as raw resources, concatenated at runtime |
| `Issue`, `IssueMeta`, `DTC`, `LiveReading` schema | Kotlin `data class` definitions |
| `tests/fixtures/obd_snapshots/misfire.json` | `assets/misfire.json`, parsed at startup |
| `dtc_table.py` (just the P0301 entry for now) | A Kotlin object literal — one entry is enough |
| `fallback.py` synthesis for misfire | A Kotlin constant string, used if Mediapipe fails |
| `gemma_adapter.py` JSON-output handling | Mediapipe LLM Inference call with response parsing |
| `carcopilot_mockup_v09.html` | WebView asset, rendered as-is for the entire UI |

**Critical:** the WebView IS the UI. You are not rebuilding any of the mockup in native Android. The mockup HTML loads in a WebView, and a JS bridge calls into native Kotlin when the user taps the issue card. Native Kotlin runs the Mediapipe LLM Inference call and pushes the result back into the WebView via JS injection.

## 4. Technical stack

- **Language:** Kotlin
- **UI:** Single-Activity app, one WebView covering full screen
- **LLM runtime:** Mediapipe LLM Inference Task for Android
- **Model:** Gemma 4 in Mediapipe-compatible format. **Verify availability before committing to this path** — see §5 checkpoint A.
- **Min SDK:** 26 (Android 8.0). Pixel 9 is on Android 14+.
- **Architecture:** Activity → WebView (loads `assets/carcopilot_mockup_v09.html`) → JS bridge `Android.generateSynthesis(issueJson)` → Kotlin coroutine → Mediapipe LLM Inference → JS callback `window.onSynthesisReady(text)`

## 5. Go/no-go checkpoints

**Checkpoint A (Friday end of day): can Mediapipe LLM Inference load Gemma 4 on the target device at all?**

Before writing any app code, do this smoke test:

1. Find a Gemma 4 `.task` model file (check kaggle.com, mediapipe model zoo, ai.google.dev). Note: as of writing, Mediapipe LLM Inference is known to support Gemma 2 and Gemma 3 variants — Gemma 4 may or may not have a packaged `.task` file. Verify before assuming.
2. If Gemma 4 isn't available as a `.task`, fall back options in priority order:
   - (a) Convert Gemma 4 weights to Mediapipe format yourself (likely too time-consuming for the deadline — skip).
   - (b) Use the closest Gemma variant that IS packaged and disclose this honestly in the writeup ("running Gemma 3 e4b due to Gemma 4 not yet being packaged for Mediapipe LLM Inference at submission time; same prompts and architecture port directly when Gemma 4 ships"). Hackathon is named for Gemma 4 — this is a non-trivial deviation but defensible if explained.
   - (c) Use AICore (Pixel-specific) which exposes Gemini Nano — but this is a different model entirely, not Gemma. Avoid unless desperate; deviates from the hackathon prompt.
3. Write a 30-line standalone Android sample that loads the model and runs one prompt. If you get any text out, checkpoint A passes.

**If checkpoint A fails: fall back to Option B (honest reframing) immediately.** Don't burn Saturday building an app that can't run the model.

**Checkpoint B (Saturday end of day): can the WebView + JS bridge round-trip a real Gemma call?**

By Saturday evening, the app should:
- Launch and show the HTML mockup
- Respond to a tap on the issue card by triggering the synthesis call
- Display the Gemma-generated text in the AI strip (real, not canned)

**If checkpoint B fails: ship Option B fallback Sunday morning.** Use the Saturday-evening state as the "this is what we built" demo, with narration explaining the gap honestly.

## 6. Build order for the agent

```
Day 1 (Friday):
  - Checkpoint A: Mediapipe + Gemma 4 smoke test (no app yet, just verify the model loads)
  - If A passes: scaffold Android Studio project, single Activity, WebView loading the mockup HTML

Day 2 (Saturday):
  - Implement Kotlin DTC table (one entry: P0301)
  - Implement Kotlin fallback synthesis (canned text for misfire)
  - Implement Mediapipe LLM Inference wrapper (mirrors gemma_adapter.py interface)
  - Implement JS bridge: Android.generateSynthesis(issueJson) -> calls Mediapipe -> returns string
  - Wire up: tap card -> bridge call -> show typing animation -> inject result text into AI strip
  - Checkpoint B at end of day

Day 3 (Sunday):
  - Record demo on real device with camera
  - Edit video
  - Write submission

Fallback at any checkpoint failure:
  - Switch to Option B (Python backend + mockup, honest reframing)
  - Use what was built up to that point as the writeup's "architecture proof"
```

## 7. JS bridge contract

The mockup HTML needs minimal changes to wire up the bridge. In the existing `showScreen('issue')` handler, after the typing animation starts:

```javascript
// In the existing mockup code, when issue page opens:
if (window.Android && window.Android.generateSynthesis) {
  const issueJson = JSON.stringify(getCurrentIssueData());
  window.Android.generateSynthesis(issueJson);
  // Android will call window.onSynthesisReady(text) when done
}

window.onSynthesisReady = function(text) {
  document.querySelector('[data-screen="issue"] .p-aitext').innerHTML = text;
  document.querySelector('[data-screen="issue"] .p-ai').classList.remove('thinking');
};
```

Native side:

```kotlin
class AndroidBridge(private val webView: WebView, private val llm: GemmaLlm) {
  @JavascriptInterface
  fun generateSynthesis(issueJson: String) {
    CoroutineScope(Dispatchers.IO).launch {
      val result = try {
        llm.generateSynthesis(parseIssue(issueJson))
      } catch (e: Exception) {
        FALLBACK_SYNTHESIS  // canned text from fallback.py port
      }
      webView.post {
        webView.evaluateJavascript(
          "window.onSynthesisReady(${JSONObject.quote(result)})", null
        )
      }
    }
  }
}
```

If Mediapipe fails on any call, the bridge silently falls back to canned text. The demo never breaks.

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

---

## How to hand this to the agent

In a fresh Claude Code session, in a new directory (don't pollute the Python repo):

> Read carcopilot_android_spec.md, then read carcopilot_design.md sections 4 (schema), 6 (Gemma adapter), and Appendix A (prompts) in the Python repo for reference. Start with §5 Checkpoint A — verify whether Gemma 4 is available as a Mediapipe LLM Inference .task file before writing any app code. Report back with: (1) what model file you found, (2) where you found it, (3) whether the smoke test produced text on a test device. Do not start scaffolding the app until I confirm checkpoint A is green.