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
