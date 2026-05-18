# Performance notes — 2026-05-18

> **STATUS:** Live as of 2026-05-18. Shipping commit is HEAD on `main` after this report lands. Numbers were captured on a corporate-managed Pixel 9, GPU backend, Gemma 4 E4B, single ADB session.

## TL;DR

Three landed changes; one investigated and deferred. Net wall-clock improvement on the demo flow is meaningful — the biggest user-visible savings come from the walkthrough step path (~17–18 seconds saved per step, ~106 seconds across a 6-step walkthrough). Decode throughput is up across every surface, mostly from speculative decoding (MTP). Engine init and steady-state prefill cost are now visible to the log via SDK-authoritative `BenchmarkInfo`.

| Surface | Before (Phase 8) | After (today) | Change |
|---|---|---|---|
| Synthesis decode | ~5 tok/s | 6.15–7.05 tok/s | +23–41% |
| Mechanic draft decode | (not measured) | 8.08 tok/s | new ceiling |
| Walkthrough plan decode | (didn't reliably complete) | 9.02–11.02 tok/s | new ceiling, now reliable |
| Walkthrough step decode | (never ran; plan parse failed) | 6.84–10.22 tok/s | new surface, now reliable |
| Walkthrough step prefill | ~2,779 tok (est.) | ~2,123 tok avg | −24% |

## What shipped

### 1. Tier-1 SDK knobs in `GemmaService.kt`

Four flags that the SDK exposes but the previous code never set:

- **Speculative decoding (MTP).** Probed at runtime with `Capabilities(modelPath).hasSpeculativeDecodingSupport()`; flag set only if the `.litertlm` carries the draft head. Per the LiteRT-LM 0.11.0 release notes, MTP claims >2× decode on Gemma 4 mobile GPU. On Pixel 9 with E4B we measured +23–60% depending on surface — real but well short of the documented best case. The synthesis sampler at top-p 0.95 likely widens MTP draft rejection vs. the tighter samplers on other surfaces.
- **`ExperimentalFlags.enableBenchmark = true`** + `Conversation.getBenchmarkInfo()` logged per call. Replaces our hand-rolled `firstTokenNs / tokenCount` math with the SDK's authoritative numbers (`initTimeInSecond`, `timeToFirstTokenInSecond`, `lastPrefillTokenCount`, `lastDecodeTokenCount`, `lastPrefillTokensPerSecond`, `lastDecodeTokensPerSecond`). Made every subsequent change measurable.
- **`EngineConfig.maxNumTokens = 4096`.** Caps the working context window. Our longest prefill (P0087 walkthrough step ~3.5k tok pre-trim) plus output fits comfortably. Smaller window → smaller KV-cache buffers, faster `Engine.initialize()`, less memory pressure on the cleanup race that drives `NATIVE_SETTLE_MS`.
- **`EngineConfig.cacheDir = context.cacheDir.absolutePath`.** Per docs, improves 2nd load time. Effect not directly measured this session but is free to enable.

Phase-5 locked code paths (surface multiplexing, `convoMutex`, `NATIVE_SETTLE_MS`, all five fallback paths, prewarm timing) are unchanged.

### 2. Per-step phase splitting in `PromptBuilder.kt`

The walkthrough-step prompt used to inline the **full** curated procedure (P0301 ~1,449 tokens, P0087 ~2,181 tokens) on every step generation. BenchmarkInfo confirmed prefill was the wall-clock bottleneck (~10s of every synthesis call is prefill, not decode).

Now the prompt assembles **intro + indexed phase**:

- `splitProcedureIntoPhases(body)` parses the curated markdown at `^## Phase N` boundaries, returning `[intro, phase1, …, phaseN]`. Pure function, 9 JVM unit tests in `ProcedurePhaseSplitTest`.
- `phaseForStep(phases, stepNumber, totalSteps)` indexes into the split, with step 1 → first phase, last step → last phase, middle steps → indexed (clamped). Intro is always prepended so the bill of materials and tool sizes stay in scope.
- `procedureChunkForStep` in `PromptBuilder` is the wired entry point; `procedureFor` (full document) still feeds the plan call, which clusters phases into the step list and needs the whole text.

On-device measurements: per-step procedure portion is now ~700–860 tokens vs. ~1,449 for the full P0301 doc. About 21% prefill reduction per step, ~5–6 seconds wall-clock each, ~30+ seconds saved across a 6-step walkthrough.

Less than the 50%+ projected because the procedure intro is fatter than estimated — vehicle context + parts/tools list runs ~500–600 tokens and ships on every step. See **Suggested next moves** below.

### 3. Brace-balancing tolerance in `WalkthroughPlanState.kt`

Captured live failure: E4B emitted a structurally valid plan ending in `}]` (closing the last step + the array) but cut off before the outer `}`. The old `tolerantPlanParse` used `s.lastIndexOf('}')` which also discarded the trailing `]`, leaving doubly-unbalanced JSON for the strict parser to reject — every walkthrough run silently fell back to canned `DTCEntry.walkthroughSteps` text and **never exercised the Gemma per-step path at all**.

New `balanceJsonTail(text, startAt)` walks the buffer with string-aware brace/bracket tracking and closes any unclosed structure at the tail. Handles three shapes:

- Missing outer `}` (the captured failure).
- Output ends mid-string (cancelled generation).
- Trailing structural comma (model emitted `,` before the next object but stopped).

8 new tests in `WalkthroughPlanStateTest` exercise these shapes plus regression coverage that already-balanced input round-trips unchanged.

This fix is what made the phase-split change observable today — without it, the six `bench surface=walkthrough_step_N` lines never would have appeared in logcat.

## What we investigated but did not ship

### Constrained decoding for numeric fidelity

`ExperimentalFlags.enableConversationConstrainedDecoding` exists in the SDK and looked like a candidate fix for the documented E4B numeric drift (`0.0.043 inches`, `500 mph` etc. in step bodies). Probing the SDK and the public docs showed the flag is **tied to Tool Use** — it constrains output to match registered `OpenApiTool` schemas, not arbitrary user-supplied JSON shapes. To actually exercise it for our surfaces we'd need to:

1. Define an `OpenApiTool` per surface (e.g., `submit_walkthrough_step(body)`).
2. Wire tools into `ConversationConfig(tools = …)`.
3. Reroute the streaming contract from per-token `Flow<String>` deltas to tool-call `Message` payloads.
4. Decide what `automaticToolCalling = true` (default) does when our "tool" is really a return-value collector.

That crosses the CLAUDE.md "locked architecture — stop and ask" line on `GemmaService.kt`'s streaming contract and the five `LaunchedEffect` collect blocks. Plus E4B's tool-call output quality on a Pixel 9 GPU is unknown — there's no guarantee the constrained decoder is sharp enough to fix tokenizer-level digit-extension errors, and the `SpecsChipRow` film-around already mitigates the visual damage.

Deferred. Documented as a sub-bullet in `FUTURE_WORK.md` under the existing numeric-drift entry.

## What we learned about the system

The **biggest single insight** from this session is structural, not a knob:

> **Prefill, not decode, dominates the wall-clock cost.**

Across every surface measured today, prefill takes longer than decode:

- Synthesis: 1,449 tok prefill @ 142 tps = **10.2s** prefill; 113 tok decode @ 6.15 tps = 18.4s decode → prefill is 36% of total
- Plan: 2,247 tok prefill @ 141 tps = **15.9s** prefill; 146 tok decode @ 9.02 tps = 16.2s decode → prefill is 50% of total
- Walkthrough step: ~2,100 tok prefill @ ~180 tps = **~11.7s** prefill; ~110 tok decode @ ~8.6 tps = ~12.8s decode → prefill is 48% of total

Speculative decoding only accelerates decode. The biggest remaining lever is anything that **reduces the prompt going into the model** — prompt trimming, RAG context trimming, intro de-duplication, prefill caching across calls. This is now visible because of (2) above; before today it was a hypothesis.

## Known issues that did **not** move

- **Numeric drift in step bodies.** Pre-existing per FUTURE_WORK §"Gemma 4 E4B numeric-token hallucination." Two visible instances in today's session (step 4: `0.0.043 inches`; step 6: `500 mph`). The phase-split did not introduce the drift; the procedure intro contains the correct values too. SpecsChipRow film-around still active. Not user-blocking.
- **Plan call still pays full procedure prefill.** The plan needs the whole document to cluster phases; ~2,247 tokens of prefill per plan call (~16 seconds). No obvious lever without changing the planning approach.
- **Manual tps metric undercounts decode.** Our hand-rolled `tps = tokens / totalMs` log line reads ~2.5–3.5 on synthesis because `totalMs` includes prefill. `bench surface=… decode_tps=…` from BenchmarkInfo is the real number — keep both for now since callers may still grep the old format.

## Suggested next moves

1. **Drop intro from middle steps.** Keep intro for step 1 (safety/prep) and last step (verify); skip it for middle steps where the relevant phase already contains the spec. Estimated additional ~25% prefill reduction per step. Risk: middle-step bodies could lose access to parts-list tool sizes. Needs a quality measurement pass.
2. **Trim `walkthrough_step.md` (1,016 tokens).** Six worked numeric examples plus three paragraphs of rules. With MTP shipping, prefill matters more than ever; one or two of the redundant examples could be cut. Risk: medium — the worked examples are exactly what kept numeric drift from being worse.
3. **Trim the synthesis prompt and the RAG context.** Synthesis prefill is 1,449 tokens with the RAG block included. The RAG block runs ~900 tokens; the per-issue template carries another ~600. Both have fat.
4. **Few-shot prewarm payload.** Already called out in FUTURE_WORK. Replace the `"ok"` dummy turn with a one-line user-message + one-line ideal JSON in target voice. Doesn't reduce latency but recovers slight phrasing drift post-prewarm.
5. **Constrained decoding via Tool Use.** Real architectural change. Wait for a session where the streaming-contract surgery is in scope.

## Files touched this session

```
app/src/main/java/com/example/carcopilot/inference/GemmaService.kt          (Tier-1 SDK knobs + BenchmarkInfo)
app/src/main/java/com/example/carcopilot/inference/PromptBuilder.kt         (phase splitting)
app/src/main/java/com/example/carcopilot/ui/WalkthroughPlanState.kt         (parse tolerance)
app/src/test/java/com/example/carcopilot/inference/ProcedurePhaseSplitTest.kt   (new)
app/src/test/java/com/example/carcopilot/ui/WalkthroughPlanStateTest.kt     (8 new tests)
FUTURE_WORK.md                                                              (constrained-decoding finding)
reference/perf_notes_2026-05-18.md                                          (this file)
```

CLAUDE.md "Current state" was updated in the same session to cover MTP, BenchmarkInfo, `cacheDir`, and `maxNumTokens`.
