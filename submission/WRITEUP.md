# CAR·COPILOT

### A friend-on-the-phone mechanic that runs entirely on your Android — making car diagnostics accessible regardless of income or internet

**Track**: Impact — **Digital Equity & Inclusivity**.  Also targets **Special Technology — LiteRT**.
**Repo**: https://github.com/DigitalVeer/car-copilot.  **Video**: [YouTube TBD].  **Cover image**: Issue page mid-stream, phone in airplane mode.

---

## The problem

A check-engine light comes on. You don't know if your car will make it home. You don't know if the mechanic quoting $800 is being straight. You don't know whether to drive or to call a tow.

Three barriers decide who gets a good answer today: **money** (a $200 dealer diagnostic), **internet** (every consumer car-AI app is a cloud API behind a subscription paywall), and **knowledge** (the answer comes back in dialect — *"lean bank 1 trim, suspect MAF"* — to someone who just wants to know if it's safe to drive).

Those barriers stack hardest where it matters most. A driver in a tunnel, a parking garage, or a rural area has no signal. A driver in a developing market where mid-90s Corollas outnumber smartphones has no dealer network and no subscription-app coverage. The most useful place to put a car-savvy friend is exactly where cloud LLMs don't work.

CAR·COPILOT is that friend on the phone — but it lives on the phone. **No cloud calls. No location services. No internet permission for AI. No subscription.** Plug in any $20 OBD-II reader, and a Pixel 9 in airplane mode tells you what's wrong, in plain English, and walks you through fixing it. The whole product is one APK and one ~3.4 GB model file. After that, it works anywhere, for anyone, forever.

## What it does

Five Compose screens, every narrative surface streamed by Gemma 4 running locally.

- **Home** — trip-readiness verdict and the day's issues, severity-coded.
- **Issue** — two-paragraph diagnosis in a friend's voice; cost / time / DIY-difficulty; evidence drawer with DTC codes and live readings.
- **Walkthrough** — per-DTC step-by-step procedure with canonical torque/gap/pressure pinned beside the body in a `SpecsChipRow`, so any drift in the streamed prose reads as a visible delta against authoritative reference.
- **Mechanic draft** — a codes-and-evidence handoff message you paste into a text to a shop.
- **History** — patterns across past issues: *"Two coil failures in seven months on a Corolla — likely valve cover gasket oil leak."*

## Architecture: the deterministic-narrator split

The load-bearing engineering choice is what Gemma is **not** allowed to do.

`OBDSnapshot → RulesEngine → Classification + RagStore → PromptBuilder → GemmaService (5 surfaces) → JSON-aware extractors → UI (fail-soft fallback)`

Severity, route (DIY vs expert), confidence, likely cause, supporting signals, cost, and time — every safety-relevant field — is computed by `data/RulesEngine.kt` from named live readings against DTC-specific thresholds. A P0301 misfire with O₂ above 0.85 V earns *HIGH* and "leaking injector"; below 0.2 V with low RPM earns *HIGH* and "failed coil". A P0087 fuel-rail fault under 20,000 kPa earns *HIGH* and "clogged filter — cheap and the correct first hypothesis before checking the pump". `data/DTCTable.kt` carries 256 thin entries plus 16 curated procedures; `data/RagStore.kt` injects per-DTC and developing-market context.

Only then does Gemma narrate. Five surfaces, each with its own prompt template, per-surface sampler, JSON envelope, and a stateful extractor that pulls partial content from the streaming buffer without ever letting a half-finished escape sequence reach the user. Every surface has a canned-text fallback path — if LiteRT-LM throws, the user still sees text. The Issue data shape (`model/Schema.kt`) is the UI contract.

## How we used Gemma 4 on LiteRT

**Gemma 4 E4B** running through Google AI Edge's `com.google.ai.edge.litertlm:litertlm-android:0.11.0` on Pixel 9's Mali GPU. The `.litertlm` artifact (~3.41 GB) stages from `/data/local/tmp/` into the app's `filesDir` at first launch — the GPU delegate writes a sidecar cache the staged path's UID can't touch. The manifest carries two `<uses-native-library>` entries (`libvndksupport.so`, `libOpenCL.so`) authorizing the GPU backend's dlopen of the vendor OpenCL driver. A build flag (`-PmodelVariant=E2B`) flips to the half-size variant.

Engine init runs `async` from `CarCopilotApp.onCreate`. A parallel prewarm coroutine creates the synthesis Conversation, sends a one-shot few-shot voice anchor, and cancels after the first token. Pays the system-prompt prefill while the user reads Home — ~24% first-token latency win on the Issue page.

LiteRT-LM 0.11.0 allows one Conversation per Engine. `GemmaService.acquireConversationForSurfaceLocked(surface, sampler?)` multiplexes the slot, closing and recreating on switch. Three SDK knobs the previous code never set: **`ExperimentalFlags.enableSpeculativeDecoding`** flipped on at init only if `Capabilities(modelPath).hasSpeculativeDecodingSupport()` returns true (capability-gated so an E4B→E2B swap doesn't enable MTP on a model without the draft head); **`ExperimentalFlags.enableBenchmark = true`** + `Conversation.getBenchmarkInfo()` logged per call, surfacing SDK-authoritative `init / ttft / prefill_tokens / decode_tokens / prefill_tps / decode_tps`; and **`EngineConfig.maxNumTokens = 4096`** + `cacheDir = context.cacheDir.absolutePath`. The walkthrough-step surface drops to `temperature = 0.1, topP = 0.5` because step bodies must paraphrase numeric values from the curated procedure verbatim.

## Challenges overcome

**1. Prefill, not decode, dominates wall-clock cost.** Discovered via `BenchmarkInfo`: synthesis prefill 10.2s (1,449 tok), plan prefill 15.9s (2,247 tok), step prefill ~11.7s. Decode is roughly half on every surface. Speculative decoding accelerates the smaller half. Every token shaved off the prompt is roughly as valuable as the entire MTP win. Drove `PromptBuilder.splitProcedureIntoPhases`: instead of inlining the full curated procedure on every step, the prompt assembles intro + indexed phase. −21% prefill per step, ~30 seconds saved across a 6-step walkthrough.

**2. BPE tokenizer numeric drift.** Gemma 4 E4B reliably inserts decimals into simple integer+unit patterns: "10mm" → "10.10mm", "30 seconds" → "30.30 seconds". Multi-digit decimals survive; simple patterns don't. Lower temperature concentrates the sampler on the same wrong continuation. Solution: stop fighting the tokenizer. `DTCEntry.procedureSpecs` carries canonical values from the curated procedure; `SpecsChipRow.kt` pins them beside the streamed body. Drift becomes a visible feature, not an invisible hazard.

**3. Plan parser silently fell back.** E4B emitted a structurally valid plan ending in `}]` but cut off before the outer `}`. The old `tolerantPlanParse` used `s.lastIndexOf('}')` which discarded the trailing `]`, leaving doubly-unbalanced JSON. **Every walkthrough run silently fell back to canned text — the Gemma per-step path never exercised in practice.** A string-aware brace-balancing tail-closer (`WalkthroughPlanState.balanceJsonTail`) made the phase-split change observable.

**4. Native SIGSEGV in `liblitertlm_jni.so`** on rapid surface churn. LiteRT-LM 0.11.0 can crash if a Conversation is recreated before the previous one's native teardown completes. `convoMutex` serializes the close → settle → createConversation → sendMessage lifecycle end-to-end, with a 250 ms `NATIVE_SETTLE_MS` floor between every close and the next create.

## Why these choices were right

**Cloud** would have killed the offline case where the product matters most and turned the product into another subscription. **Letting Gemma classify** surfaces the same class of tokenizer errors as numeric drift, except on safety-critical fields. **MediaPipe** (the previous home for on-device Gemma) was deprecated for the LiteRT rebrand — no MTP, no `BenchmarkInfo`. Choosing LiteRT-LM 0.11.0 was the only path that surfaced the prefill insight above. The SpecsChipRow film-around is the right shape of fix: cheap, visible, and it makes a model limitation legible to the user rather than hiding it.

## Impact: Digital Equity & Inclusivity

The competition asks: *Break down barriers through linguistic diversity, intuitive interfaces, and tools that help close the AI skills gap.*

- **Income.** No subscription, no API cost, no dealer-diagnostic fee. Marginal cost of a diagnosis after install is zero.
- **Internet.** Works in airplane mode. Most useful where the network isn't — tunnels, garages, rural roads, developing markets.
- **Knowledge.** The friend-on-the-phone voice is the interface. One thought per sentence. No engineer vocabulary. The 16 deeply-curated DTC entries include a developing-market RAG document calibrated for places where the dealer network isn't a phone call away.

When frontier intelligence is genuinely local and genuinely free, the cost of expert advice collapses to the cost of the phone you already own.

## Numbers (Pixel 9, GPU backend, Gemma 4 E4B)

| Surface | Decode TPS | Prefill tokens | TTFT |
|---|---|---|---|
| Synthesis | 6.15–7.05 | 1,449 | 10.2s |
| Mechanic draft | 8.08 | ~1,200 | ~7s |
| Walkthrough plan | 9.02–11.02 | 2,247 | 15.9s |
| Walkthrough step | 6.84–10.22 | ~2,100 | ~11.7s |

179 JVM unit tests cover the streaming JSON extractors, phase splitter, OBDSnapshot schema, Issue builder, DTC table contract, deterministic classifier, and ELM327 protocol layer.

## What's next

BLE field test against a paired ELM327. E2B variant measurement against the locked golden outputs. Constrained decoding via Tool Use when the streaming-contract surgery is in scope. Embedding-based RAG over the 256-entry thin catalog.

---

**Project Links** — Code: https://github.com/DigitalVeer/car-copilot · Video: [YouTube TBD] · APK + model staging: [GitHub release TBD] · Engineering notes: `reference/perf_notes_2026-05-18.md`, `FUTURE_WORK.md`
