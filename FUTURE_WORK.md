# Post-hackathon follow-ups

A running list of work that wasn't in scope for the hackathon demo but is worth coming back to. The demo has shipped; entries are grouped by theme rather than chronology.

## Known issues (mitigated, root cause open)

### Native instability on rapid surface churn

LiteRT-LM 0.11.0 can SIGSEGV inside `liblitertlm_jni.so` when the user navigates between Issue / Walkthrough / Mechanic / History faster than in-flight prefills complete. Phase 11.5 (`edd06db`) shipped the workaround described in "proper fix direction" below — surface switches are serialized end-to-end behind `GemmaService.convoMutex`, and a `NATIVE_SETTLE_MS = 250` settle delay sits between every `Conversation.close()` and the next `createConversation`. Under the linear demo flow the crash is not reproducible. It remains a real risk if a future change loosens the serialization, or if the SDK's native cleanup ever takes longer than the 250 ms budget.

**Crash signature.** `Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0` (null pointer dereference) on a `Thread-N` native worker, with the entire backtrace inside `liblitertlm_jni.so`:

```
#00 pc 00000000004c9060  liblitertlm_jni.so
#01 pc 0000000000704de4  liblitertlm_jni.so
#02 pc 0000000000732aa8  liblitertlm_jni.so
#03 pc 0000000000733398  liblitertlm_jni.so
#04 pc 000000000008a714  libc.so (__pthread_start)
```

The fatal frame is 12ms after `RunPrefillAsync status: OK` in the engine log — the crash is inside prefill execution, not at session creation.

**Trigger.** Open Issue → tap Mechanic before synthesis completes → tap back before draft completes → tap Mechanic again → repeat. Each transition closes one Conversation and opens a new one against the same Engine; before Phase 11.5, after a few cycles of cancel-mid-prefill + reopen, the native side would lose a pointer. Heavy GPU stalls (`Skipped 200+ frames`, `Davey! duration=6649ms`) typically preceded the crash by a second or two.

**Pre-existing in the SDK.** Reproduces on commit `993960e` (Phase-10B HEAD, before any Phase 11 refactor work). Verified by checking out `993960e`, rebuilding, and running the same rapid-nav pattern with `adb shell input` — same SIGSEGV signature, same native-only backtrace. The Phase 11 fixture-seam refactor is exonerated; this is a LiteRT-LM 0.11.0 issue exposed by Phase 10A's surface-multiplexing workaround for the single-Conversation-per-Engine constraint.

**Proper fix direction.**
- ✅ **Serialize surface-switch requests behind a mutex in `GemmaService`** — shipped in Phase 11.5. Holds `convoMutex` across the close → settle → createConversation → sendMessage → collect lifecycle so the SDK's cancellation path (`cancelProcess` → `CANCELLED: Process cancelled`) has time to fully quiesce the prefill thread before close, and the new Conversation never starts until at least `NATIVE_SETTLE_MS` ms after the last close.
- **Upgrade SDK if a later LiteRT-LM release lifts the single-Conversation-per-Engine constraint.** 0.11.0 rejects a second `createConversation` while another session is open. If parallel Conversations per surface become possible, the close-during-prefill race goes away entirely and the multiplexing in `GemmaService` simplifies considerably.
- **Disable mid-stream cancellation as a deeper backstop.** Block back navigation while the AI strip is in `Streaming` state (with a visible affordance — "still thinking…"). Cheap to implement, removes the cancel-mid-prefill case as a possibility, and doesn't depend on SDK behavior.

Owner: open. Priority: deferred (mitigation holding for the linear flow).

## Performance

Inference latency on the Issue page is still where Phase 8 left it: ~6s to first token, ~5 tok/s steady state on a Pixel 9 with the GPU backend. Whole synthesis takes ~30s end-to-end. The ThinkingDots animation covers the first-token gap; for real use this still needs to come down.

**Shipped 2026-05-18** (full writeup in `reference/perf_notes_2026-05-18.md`): MTP (capability-gated speculative decoding), `EngineConfig.maxNumTokens = 4096`, `cacheDir`, per-step phase splitting in `PromptBuilder`, brace-balancing tolerance in `WalkthroughPlanState`, and `BenchmarkInfo` logging on all five surfaces. Net wins: synthesis decode 5 → 6.15–7.05 tok/s, walkthrough step decode 6.84–10.22 tok/s (the surface didn't reliably run before — the plan parse fix unblocked it), and walkthrough step prefill down ~21% from the full-procedure shape. **The biggest revealed insight: prefill, not decode, dominates wall-clock cost on every surface** — every token cut from the prompt is roughly as valuable as the entire MTP win.

Options to explore, in rough order of likely payoff:

- **Smaller quantization of E4B.** The current build is whatever single `.litertlm` artifact `litert-community/gemma-4-E4B-it` publishes today (~3.41 GiB on disk; confirm against the repo's quantization label — possibly `int4` or `int8`). If a smaller quantization (e.g. `int4` with weight-only packing, or a tighter sub-channel scheme) appears later, swap and remeasure. Quality may degrade on long-form coherence but our outputs are two short paragraphs.
- **Switch to Gemma 4 E2B for runtime.** Half the parameter count, much faster prefill and decode. The build harness already exists — `./gradlew assembleDebug -PmodelVariant=E2B` flips `BuildConfig.MODEL_VARIANT` and `GemmaService.MODEL_FILE` to `gemma-4-E2B-it.litertlm`. What's missing is the side-by-side measurement against the locked golden outputs to decide whether E2B holds up on voice. The synthesis is short and structurally constrained (a JSON envelope around two paragraphs), so it might.
- **Prefill caching.** If LiteRT-LM exposes a way to retain the KV cache for the system prompt across conversations, the per-issue prefill cost (currently dominant) drops to just the per-issue user-message tokens. Worth checking the SDK for a `ConversationConfig` or `Engine` knob; the public surface in 0.11.0 didn't obviously expose it.
- **GPU vs CPU backend.** We're on `Backend.GPU()` per `GemmaService` and the `<uses-native-library>` entries in `AndroidManifest.xml` for `libOpenCL.so` / `libvndksupport.so`. On some Tensor parts CPU is competitive and avoids the ~30s first-launch shader-cache build. Measure both with the same prompt.
- **Trim the synthesis prompt and cap output length.** The system prompt at `app/src/main/assets/system.md` plus the per-issue user message determine prefill cost; the sampler config in `GemmaService.streamSynthesis` doesn't currently set a `maxTokens`. A hard cap (e.g. 200 tokens) bounds the worst case and the prompt itself probably has fat to cut without hurting voice.

When picking this back up, set up a tiny on-device benchmark harness (one `LaunchedEffect` that runs the misfire synthesis N times and logs time-to-first-token + tokens/sec) before changing anything, so changes are measured rather than guessed.

### BenchmarkInfo introspection

`Conversation.getBenchmarkInfo()` is exposed in the SDK but currently unread. Reading it would distinguish two hypotheses for why the hoisted Conversation showed barely-measurable KV-cache reuse between calls in the same session: (a) the SDK re-prefills the full tape on every `sendMessage`, in which case hoisting yields nothing on the prefill side and the only real win is JIT'd decode kernels; or (b) the per-issue prompt (~1200 tokens of DTCs + live readings + template) dominates the system prompt (~250 tokens) in prefill cost, so cache reuse *is* working but the saved fraction is tiny. The two have different next moves — (a) means caching is a dead end on this SDK version, (b) means shrinking the per-issue prompt is a bigger lever than caching.

**Update (2026-05-18, shipped).** Wired. `ExperimentalFlags.enableBenchmark = true` set at engine init; per-surface `logBenchmarkInfo` in `GemmaService` emits a `bench surface=… …` line after each successful generation with `init`, `ttft`, `prefill_tokens`, `decode_tokens`, `prefill_tps`, `decode_tps`. Across the demo session the data settled the open question: hypothesis (b) is right — the per-issue prompt is by far the prefill bottleneck (synthesis 1,449 tok, walkthrough step ~2,100 tok), and prefill takes longer than decode on every surface. Cache reuse may still be happening at the system-prompt level but the gain is invisible against the per-issue prompt cost. Concrete next lever: prompt trimming, not cache plumbing.

### Few-shot prewarm payload

The dummy `"ok"` turn in `GemmaService.prewarmJob` is unavoidable context pollution at the SDK level (no prefill-only API on Kotlin 0.11.0, no rollback after `cancelProcess`), but the *content* of the pollution is ours to design. A purpose-built prewarm — a one-line user message plus a one-line ideal assistant response in the target voice and exact JSON shape — could turn the pollution from a cost into a few-shot voice anchor. Plausibly recovers the slight phrasing drift we currently see post-prewarm while keeping the 24% latency win. Cheap to try.

### Unbounded conversation history

The hoisted Conversation grows context unboundedly across `sendMessage` calls — the SDK has no eviction, no truncation, no `clearHistory()`. Fine for the demo (one issue per session, force-stop between launches) but would eventually fill the context window in any sustained use. Needs a trim policy — likely close + recreate the Conversation after every Nth call, or after the rendered tape exceeds a token budget — when we add real OBD-II diagnoses and users see multiple issues per session.

## Architecture seams

### Single-Conversation-per-Engine constraint

LiteRT-LM 0.11.0 rejects a second `createConversation` while another session is open (`FAILED_PRECONDITION: A session already exists`). Phase 10A worked around this by multiplexing one slot between surfaces with close+recreate on switch, paying a system-prompt prefill per transition. If a later SDK release lifts this constraint, revisit parallel prewarmed Conversations — one per surface (synthesis, draft, history) — for faster cross-surface navigation. Discovered when implementing live mechanic draft generation; compounds with the native-instability issue above (a parallel-Conversation SDK would remove the close-during-prefill race entirely).

### Service history seam

`History.ENTRIES / STATS / PATTERN` (in `model/History.kt`) is currently fixture-direct from `HistoryScreen`. Future `HistoryRepository` seam to back it with local persistence once history actually accumulates from real diagnoses — separate from `OBDDataSource` because service history is app-local state, not adapter output.

### RAG-backed DTC table

**Two related features shipped, one still open.** Will's branch landed two artifacts:

- `assets/rag/dtc_context.json` — 4 retrieval documents (P0087, P0171, P0301-4, plus an always-applicable "general developing-market" doc) consumed by `data/RagStore.kt` and injected into the synthesis prompt's `{rag_context}` field.
- `assets/dtc_codes.json` — 256-entry thin DTC catalog loaded at startup via `ThinDtcLoader` and merged into `DTCTable.DEFAULT.withThin(...)`. Deep entries always win on conflict so curated data isn't overridden.

Still open: vector retrieval. Both stores are today exact-match by DTC code. Fuzzy match for unknown codes, semantic similarity across vehicle/symptom strings, and embeddings over the thin catalog are all future-work.

### Per-DTC fallback synthesis on HomeScreen

**Resolved.** `model/Fallbacks.kt` carries a per-DTC `FALLBACK_SYNTHESIS` + `FALLBACK_GOOD_NEWS` map covering 16 codes, plus `synthesizeFromClassification()` which produces a data-driven synthesis from `Classification.supportingSignals` when Gemma is unavailable. `HomeScreen.synthesisForHome` looks up by `issue.dtcs.firstOrNull()?.code` via `fallbackSynthesisFor` / `fallbackGoodNewsFor`, so a P0171 (or any mapped code) renders its own fallback text rather than the misfire default.

## Hardware path (BLE OBD)

### Python TCP OBD emulator over WiFi

**Shipped.** The emulator (`emulator/obd_emulator.py`) is wired to the Android side via `data/TcpOBDDataSource.kt`, selected at build time with `-PdataSource=EMULATOR`. End-to-end exercise pending a longer Pixel 9 session against the corolla and hilux scenarios. Plan B for car-test sessions where real hardware turns out to be finicky.

### BluetoothOBDDataSource

The `OBDDataSource` interface and `OBDSnapshot` shape are already BLE-ready as of Phase 11A / 12-prep — suspending `readSnapshot()` returning `Result<OBDSnapshot>`, hot `connectionState` StateFlow, snapshot tagged with `DataSource.BLUETOOTH` provenance and `EngineFamily` from VIN decode. Phase 12 is the actual implementation: pairing flow, ELM327 AT command sequence, PID round-trip, DTC parsing, error handling for paired-but-not-linked / out-of-range / vehicle-ignition-off. The Python `RealOBDSource` in `reference/carcopilot_design.md §8.2` is the structural reference; the Kotlin equivalent will use BLE GATT rather than python-obd.

### Bluetooth OBD transport — landed

`BluetoothOBDDataSource` (Classic SPP) and `TcpOBDDataSource` ship sharing `Elm327Protocol.kt`. Selected at build time via `-PdataSource=BLUETOOTH`. Manifest requests `BLUETOOTH_CONNECT` (API 31+) with the `usesPermissionFlags="neverForLocation"` carve-out so no location permission is implied. Real-car field-test on the Pixel 9 against a paired ELM327 is the remaining work; the transport and gating UI are in.

## Known model behaviors

### Gemma 4 E4B numeric-token hallucination on simple integer+unit patterns

When generating step bodies against the grounded P0301 procedure, Gemma 4 E4B on LiteRT-LM 0.11.0 reliably inserts decimal points into simple integer + unit patterns: "10mm" → "10.10mm", "30 seconds" → "30.30 seconds", "50 mph" → "50.0 mph". Multi-digit decimal values with internal structure ("0.043 inches", "18 Nm", "13 ft-lb") survive correctly. Separately, DTC codes drift by one character ("P0302" → "P0303") — a content-comprehension error distinct from the tokenizer effect.

Attempted mitigations (W2.1): lower temperature (0.1) + explicit numeric-preservation rule in walkthrough_step.md. Improved spec-heavy step from broken to clean; did not eliminate decimal-insertion on simple patterns.

Diagnosis: BPE tokenizer splits "10mm" into separate tokens such that the most-probable continuation after "10" is a decimal segment. Lower temperature concentrates sampling on the same wrong token rather than fixing it. Greedy decoding would not help.

Future approaches when revisited:
- Constrained decoding against an allow-list of canonical numbers extracted from the procedure
- Post-generation regex sweep replacing approximate matches with ground-truth values
- Template-based step body with placeholder slots filled deterministically from the procedure
- Re-evaluation when SDK ships a different decoder or model variant with different tokenization

**Update (2026-05-18, investigation only — not shipped).** LiteRT-LM 0.11.0 exposes `ExperimentalFlags.enableConversationConstrainedDecoding`, which initially looked like the proper fix for this. Probing the SDK and the public docs shows the flag is **tied to Tool Use** — it constrains output to match registered `OpenApiTool` schemas, not arbitrary user-supplied JSON shapes. To use it for the numeric-drift problem we'd need to (a) define an `OpenApiTool` per surface, (b) wire tools into `ConversationConfig(tools = …)`, and (c) reroute the streaming contract from per-token `Flow<String>` deltas to tool-call `Message` payloads. That last step crosses the Phase-5 lock on `inference/GemmaService.kt`'s streaming contract and the five `LaunchedEffect` collect blocks across the screens. Additional risk: E4B's tool-call output quality on a Pixel 9 GPU is unverified — no guarantee the constrained decoder is sharp enough to fix tokenizer-level digit-extension errors that the existing prompt + sampler tightening didn't catch. Deferred until a session where the streaming-contract surgery is in scope.

Not currently blocking: spec-heavy step bodies (the kind that matter for repair correctness) survive correctly. Simple bolt-size patterns drift but rarely affect outcome.

**Update (W2.1, commits `8197058` + `7c39164`).** Strengthened prompt and pinned-spec chip row shipped. The prompt change (worked examples for 30→3, 10→1010 patterns, duplicate-digit prohibition) eliminated the catastrophic runaway (~1200-zero step-6 hallucination) and the digit-extension class on the hilux/P0087 path. The digit-truncation class (30 Nm → 3 Nm, 20-30 pumps → 2-3) survived prompt + sampler tightening, so the application-level film-around — `DTCEntry.procedureSpecs` + `ui/components/SpecsChipRow.kt` — pins canonical values deterministically beside the streamed body. Drift in the body now reads as a visible delta against the authoritative chip, not invisible safety-critical damage. Net result: runaway gone, "1010 min" gone, "0 Nm" → "3 Nm" still appears but with "30 Nm (22 ft-lb)" right next to it.
