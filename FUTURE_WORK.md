# Post-hackathon follow-ups

A running list of work that's *not* in scope before the demo but is worth coming back to. Phase 5 is locked; the demo ships Monday. Everything below is exploratory.

## Performance

Inference latency on the Issue page is slower than we want: ~6s to first token, ~5 tok/s steady state on a Pixel 9 with the GPU backend. The whole synthesis takes ~30s end-to-end. The ThinkingDots animation now covers the first-token gap well enough for the demo, but for real use this needs to come down.

Options to explore, in rough order of likely payoff:

- **Smaller quantization of E4B.** The current build is whatever single `.litertlm` artifact `litert-community/gemma-4-E4B-it` publishes today (~3.41 GiB on disk; confirm against the repo's quantization label — possibly `int4` or `int8`). If a smaller quantization (e.g. `int4` with weight-only packing, or a tighter sub-channel scheme) appears later, swap and remeasure. Quality may degrade on long-form coherence but our outputs are two short paragraphs.
- **Switch to Gemma 4 E2B.** Half the parameter count, much faster prefill and decode. The synthesis is short and structurally constrained (a JSON envelope around two paragraphs), so E2B might hold up. Side-by-side a few prompts against the locked golden outputs before committing.
- **Prefill caching.** If LiteRT-LM exposes a way to retain the KV cache for the system prompt across conversations, the per-issue prefill cost (currently dominant) drops to just the per-issue user-message tokens. Worth checking the SDK for a `ConversationConfig` or `Engine` knob; the public surface in 0.11.0 didn't obviously expose it.
- **GPU vs CPU backend.** We're on `Backend.GPU()` per `GemmaService` and the `<uses-native-library>` entries in `AndroidManifest.xml` for `libOpenCL.so` / `libvndksupport.so`. On some Tensor parts CPU is competitive and avoids the ~30s first-launch shader-cache build. Measure both with the same prompt.
- **Trim the synthesis prompt and cap output length.** The system prompt at `app/src/main/assets/prompts/system.md` plus the per-issue user message determine prefill cost; the sampler config in `GemmaService.streamSynthesis` doesn't currently set a `maxTokens`. A hard cap (e.g. 200 tokens) bounds the worst case and the prompt itself probably has fat to cut without hurting voice.

When picking this back up, set up a tiny on-device benchmark harness (one `LaunchedEffect` that runs the misfire synthesis N times and logs time-to-first-token + tokens/sec) before changing anything, so changes are measured rather than guessed.

### Phase 8 follow-ups

The Phase 8 measurement pass (E4B baseline vs E2B vs E4B-with-prewarm) landed a hoisted `Conversation` and an eager prewarm; left these threads for later.

- **`BenchmarkInfo` introspection.** `Conversation.getBenchmarkInfo()` is exposed in the SDK but currently unread. Reading it would distinguish two hypotheses for why the hoisted Conversation showed barely-measurable KV-cache reuse between calls in the same session: (a) the SDK re-prefills the full tape on every `sendMessage`, in which case hoisting yields nothing on the prefill side and the only real win is JIT'd decode kernels; or (b) the per-issue prompt (~1200 tokens of DTCs + live readings + template) dominates the system prompt (~250 tokens) in prefill cost, so cache reuse *is* working but the saved fraction is tiny. The two have different next moves — (a) means caching is a dead end on this SDK version, (b) means shrinking the per-issue prompt is a bigger lever than caching.
- **Few-shot prewarm payload.** The dummy `"ok"` turn is unavoidable context pollution at the SDK level (no prefill-only API on Kotlin 0.11.0, no rollback after `cancelProcess`), but the *content* of the pollution is ours to design. A purpose-built prewarm — a one-line user message plus a one-line ideal assistant response in the target voice and exact JSON shape — could turn the pollution from a cost into a few-shot voice anchor. Plausibly recovers the slight phrasing drift we currently see post-prewarm while keeping the 24% latency win. Cheap to try.
- **Unbounded conversation history.** The hoisted Conversation grows context unboundedly across `sendMessage` calls — the SDK has no eviction, no truncation, no `clearHistory()`. Fine for the demo (one issue per session, force-stop between launches) but would eventually fill the context window in any sustained use. Needs a trim policy — likely close + recreate the Conversation after every Nth call, or after the rendered tape exceeds a token budget — when we add real OBD-II diagnoses and users see multiple issues per session.
- **Single-Conversation-per-Engine constraint.** LiteRT-LM 0.11.0 rejects a second `createConversation` while another session is open (`FAILED_PRECONDITION: A session already exists`). Phase 10A worked around this by multiplexing one slot between surfaces with close+recreate on switch, paying a system-prompt prefill per transition. If a later SDK release lifts this constraint, revisit parallel prewarmed Conversations — one per surface (synthesis, draft, history) — for faster cross-surface navigation. Discovered when implementing live mechanic draft generation.

## Phase 11 follow-ups

- **Service history seam.** `History.ENTRIES / STATS / PATTERN` (in `model/History.kt`) is currently fixture-direct from `HistoryScreen`. Future `HistoryRepository` seam to back it with local persistence once history actually accumulates from real diagnoses — separate from `OBDDataSource` because service history is app-local state, not adapter output.
