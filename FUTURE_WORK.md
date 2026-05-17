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

Options to explore, in rough order of likely payoff:

- **Smaller quantization of E4B.** The current build is whatever single `.litertlm` artifact `litert-community/gemma-4-E4B-it` publishes today (~3.41 GiB on disk; confirm against the repo's quantization label — possibly `int4` or `int8`). If a smaller quantization (e.g. `int4` with weight-only packing, or a tighter sub-channel scheme) appears later, swap and remeasure. Quality may degrade on long-form coherence but our outputs are two short paragraphs.
- **Switch to Gemma 4 E2B for runtime.** Half the parameter count, much faster prefill and decode. The build harness already exists — `./gradlew assembleDebug -PmodelVariant=E2B` flips `BuildConfig.MODEL_VARIANT` and `GemmaService.MODEL_FILE` to `gemma-4-E2B-it.litertlm`. What's missing is the side-by-side measurement against the locked golden outputs to decide whether E2B holds up on voice. The synthesis is short and structurally constrained (a JSON envelope around two paragraphs), so it might.
- **Prefill caching.** If LiteRT-LM exposes a way to retain the KV cache for the system prompt across conversations, the per-issue prefill cost (currently dominant) drops to just the per-issue user-message tokens. Worth checking the SDK for a `ConversationConfig` or `Engine` knob; the public surface in 0.11.0 didn't obviously expose it.
- **GPU vs CPU backend.** We're on `Backend.GPU()` per `GemmaService` and the `<uses-native-library>` entries in `AndroidManifest.xml` for `libOpenCL.so` / `libvndksupport.so`. On some Tensor parts CPU is competitive and avoids the ~30s first-launch shader-cache build. Measure both with the same prompt.
- **Trim the synthesis prompt and cap output length.** The system prompt at `app/src/main/assets/system.md` plus the per-issue user message determine prefill cost; the sampler config in `GemmaService.streamSynthesis` doesn't currently set a `maxTokens`. A hard cap (e.g. 200 tokens) bounds the worst case and the prompt itself probably has fat to cut without hurting voice.

When picking this back up, set up a tiny on-device benchmark harness (one `LaunchedEffect` that runs the misfire synthesis N times and logs time-to-first-token + tokens/sec) before changing anything, so changes are measured rather than guessed.

### BenchmarkInfo introspection

`Conversation.getBenchmarkInfo()` is exposed in the SDK but currently unread. Reading it would distinguish two hypotheses for why the hoisted Conversation showed barely-measurable KV-cache reuse between calls in the same session: (a) the SDK re-prefills the full tape on every `sendMessage`, in which case hoisting yields nothing on the prefill side and the only real win is JIT'd decode kernels; or (b) the per-issue prompt (~1200 tokens of DTCs + live readings + template) dominates the system prompt (~250 tokens) in prefill cost, so cache reuse *is* working but the saved fraction is tiny. The two have different next moves — (a) means caching is a dead end on this SDK version, (b) means shrinking the per-issue prompt is a bigger lever than caching.

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

Surfaced from Will's `will/dev` branch alongside the `VehicleState` schema work that landed in Phase-12-prep. Will prototyped a JSON-asset DTC catalogue at `assets/rag/dtc_common.json` with ~15 scenarios, looked up at runtime instead of compiled into `DTCTable.DEFAULT`. Today's in-code table is fine because we have one entry (P0301); the moment we add a second the linear-growth comment in `DTCTable.kt` starts to bite. JSON-on-disk also opens the door to a vector-retrieval layer for fuzzy matches when an unknown DTC arrives. Worth picking up once the table has real breadth.

## Hardware path (BLE OBD)

### Python TCP OBD emulator over WiFi

Will's idea — a small Python server that speaks ELM327 over a TCP socket, with the Android app connecting via WiFi instead of Bluetooth. Decouples the future `BluetoothOBDDataSource` from dongle+car availability: you can develop and CI-test the transport against the emulator on a laptop, then swap the underlying socket for BLE. Also a useful Plan B for car-test sessions where the real hardware turns out to be finicky on the day. Aligns with the `DataSource.EMULATOR` value already in the schema (`data/DataSource.kt`).

The emulator itself landed in commit `77db981` and lives at `emulator/obd_emulator.py` with scenarios and protocol coverage documented in `emulator/README.md`. What's still open is the Android-side transport — a `TcpEmulatorOBDDataSource` (or similar) implementing the `OBDDataSource` interface against the emulator socket. That wiring is Phase 12 work and shares its structural shape with the BLE implementation below.

### Emulator gaps surfaced by verification

A standalone end-to-end exercise of `emulator/obd_emulator.py` — AT commands driven from a Python TCP probe, no Android client — confirmed the emulator boots cleanly, the AT handshake and Modes 01/03 behave correctly per scenario, and the DTC list swaps with `--scenario`. Three gaps to triage before Phase 12A's `TcpEmulatorOBDDataSource` leans on it:

- **Mode 01 PID `00` (supported-PIDs bitmap) returns `NO DATA`.** Neither scenario lists PID `00` in its `pids` dict, so the exact-lookup path in `ELM327Session.handle` falls through to `NO DATA`. Real ELM327 clients commonly lead with `0100` to confirm the link and learn the supported-PID mask before requesting any data PID — likely to confuse Phase-12A transport bringup. Cheap fix: precompute the bitmap from each scenario's PID keys (or short-circuit `0100` to a known-good mask) and emit it for `0100` / `0120` / `0140`.

- **Mode 09 (VIN / vehicle info) unimplemented.** The mode dispatcher only handles 01/02/03/07/0A and returns `?` for everything else; `emulator/README.md`'s "Protocol coverage" section omits Mode 09 by design. Matters only when the Android side wants VIN-derived `OBDSnapshot.engineFamily`. Lower priority — defer until BLE transport actually reads VIN.

- **Scenarios emit P0171 / P0087, not P0301.** Neither emulator scenario (`corolla` → P0171, `hilux` → P0087 + P1229) matches `app/src/main/assets/misfire.json`'s P0301. `emulator/README.md` already calls this disjoint out. Three resolution paths to pick between when Phase-12A wiring lands:
  1. **Add a `misfire` scenario to the emulator.** Returns P0301 plus misfire-shaped live readings. Keeps the existing Android demo flow working end-to-end through the emulator path with no Android-side changes.
  2. **Extend `DTCTable` to cover P0171 (and P0087).** Reuses the existing emulator scenarios as the primary demo content. Opens the question of whether `misfire.json` stays as a separate fixture path or gets replaced. Pairs naturally with the RAG-backed DTC table item above.
  3. **Both.** Add a misfire scenario *and* expand `DTCTable` via the RAG-backed catalogue. The catalogue grows to real breadth while the emulator continues to drive the existing demo end-to-end. Most work, least lock-in.

This isn't urgent before Phase 12A starts — the emulator is internally consistent and useful as-is for transport-layer development — but the `0100` gap will surface the first time the Android side issues a handshake.

### BluetoothOBDDataSource

The `OBDDataSource` interface and `OBDSnapshot` shape are already BLE-ready as of Phase 11A / 12-prep — suspending `readSnapshot()` returning `Result<OBDSnapshot>`, hot `connectionState` StateFlow, snapshot tagged with `DataSource.BLUETOOTH` provenance and `EngineFamily` from VIN decode. Phase 12 is the actual implementation: pairing flow, ELM327 AT command sequence, PID round-trip, DTC parsing, error handling for paired-but-not-linked / out-of-range / vehicle-ignition-off. The Python `RealOBDSource` in `reference/carcopilot_design.md §8.2` is the structural reference; the Kotlin equivalent will use BLE GATT rather than python-obd.
