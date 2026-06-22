@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)

package com.example.carcopilot.inference

import android.content.Context
import android.util.Log
import com.example.carcopilot.BuildConfig
import com.example.carcopilot.model.Classification
import com.example.carcopilot.model.HistoryEntry
import com.example.carcopilot.model.Issue
import com.example.carcopilot.ui.PlanStep
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Phase 8 measurement harness — switch via -PmodelVariant=E2B at build time.
private val MODEL_FILE = when (BuildConfig.MODEL_VARIANT) {
    "E2B" -> "gemma-4-E2B-it.litertlm"
    else -> "gemma-4-E4B-it.litertlm"
}
private val STAGED_PATH = "/data/local/tmp/$MODEL_FILE"
private const val TAG = "GemmaService"
private const val METRIC_TAG = "CarCopilot"

/**
 * Minimum gap between a Conversation.close() and the next createConversation
 * on the same Engine. Empirically chosen to give LiteRT-LM 0.11.0's native
 * cleanup time to complete before a new prefill kicks off; the SDK's
 * close() returns to Kotlin before native teardown finishes. Tune up if the
 * SIGSEGV still appears under rapid surface churn.
 */
private const val NATIVE_SETTLE_MS: Long = 250

/**
 * Hard ceiling on the LiteRT-LM Engine's working context window. The longest
 * single prefill we ship today is the walkthrough-step prompt against the
 * P0087 procedure (~3,520 tokens of system + template + grounding) plus its
 * bounded ~300-token output; 4096 leaves slack without provisioning KV-cache
 * buffers for a window we never use. Smaller window → smaller native
 * allocations, faster [Engine.initialize], and less memory pressure on the
 * native cleanup race that drives [NATIVE_SETTLE_MS]. Raise this if a future
 * surface exceeds the cap — overflow surfaces as a generation failure that
 * falls through the standard Phase-5 fallback path.
 */
private const val MAX_CONTEXT_TOKENS: Int = 4096

/**
 * Default sampler used by every surface unless overridden at acquire time.
 * Temperature 0.3 is the right balance for the synthesis / draft / history
 * narrative surfaces — enough latitude for Gemma to sound like a friend on
 * the phone without descending into purple prose.
 */
private val DEFAULT_SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)

/**
 * Tighter sampler used only by [streamWalkthroughStep]. Step bodies must
 * paraphrase numeric values from the curated procedure verbatim — torque,
 * gap, time, bolt size, socket size. At temperature 0.3 (the default), Gemma
 * mangles them: "30 minutes" becomes "300 minutes", "0.043 inch" becomes
 * "10.0433 inch", "10mm" becomes "10.1010mm". The voice and structure
 * survive but the numbers drift. Temperature 0.1 + topP 0.5 collapses the
 * sampling distribution toward the most-probable token, which for a
 * verbatim-quote constraint is "the number that's in the prompt." topK
 * stays at 40 — the limiting filter here is top-p, not top-k.
 */
private val STEP_NUMERIC_FIDELITY_SAMPLER =
    SamplerConfig(topK = 40, topP = 0.5, temperature = 0.1)

/**
 * MTP-acceptance-tuned sampler for the informational narrative surfaces
 * (mechanic draft, history pattern, walkthrough plan).
 *
 * Speculative decoding (MTP) accepts a drafted token only when the target
 * model would have produced it; a wide sampler makes the target sample more
 * randomly, rejecting more drafts and shrinking the decode speedup. perf_notes
 * 2026-05-18 flagged the default top-p 0.95 as the likely reason MTP landed at
 * +23–60% rather than the >2× headline. Pulling top-p to 0.9 and temperature
 * to 0.2 peaks the target distribution → higher draft acceptance → faster
 * decode, while staying far looser than the numeric-fidelity sampler so these
 * surfaces keep their conversational latitude.
 *
 * Deliberately NOT applied to [streamSynthesis]: the Issue page is the
 * wow-moment voice surface and stays on [DEFAULT_SAMPLER]'s wider settings.
 * The draft is a factual note to a shop and the history pattern is an
 * explanatory paragraph — both tolerate the tighter distribution with no
 * meaningful loss of warmth. Output change is real but small; confirm the
 * voice on device alongside the per-surface `bench … decode_tps` delta before
 * extending it to synthesis.
 */
private val MTP_NARRATIVE_SAMPLER = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.2)

/**
 * Prewarm user message used by [GemmaService.prewarmJob]. Designed as a
 * one-shot voice + shape anchor for the synthesis surface: it shows one
 * compact synthesis input alongside the ideal JSON output, then asks for
 * a single-token "ok" so the cancelled-after-first-token decode wastes
 * almost nothing. The example output follows the system-prompt voice
 * rules (one thought per sentence, concrete, "your car", warm closing
 * line in good_news, no third-person "this is" opener) so the prewarm
 * turn doubles as a few-shot template the model has just seen when the
 * first real synthesis call arrives.
 */
private val PREWARM_FEW_SHOT: String = """
    Reference example of synthesis output (voice + JSON shape):

    INPUT: 2015 Toyota Corolla, P0301 misfire on cylinder 1, ignition coil suspected.
    OUTPUT: {"synthesis":"Cylinder 1 keeps misfiring — you'll feel it as a stumble at idle. On older Corollas this is almost always a worn ignition coil.","good_news":"Your car only needs a 30-minute fix and I'll walk you through it."}

    Reply with the single token "ok" to confirm.
""".trimIndent()

/**
 * Owns the LiteRT-LM Engine + a single Conversation slot tagged by surface.
 * Construct once per process (from CarCopilotApp); never per-screen.
 *
 * LiteRT-LM 0.11.0 allows only one Conversation per Engine at a time, so the
 * slot is multiplexed between surfaces (synthesis, draft, …) by close+recreate
 * on surface switch — each surface gets a clean KV cache containing only the
 * system prompt, plus an optional prewarm dummy turn on the prewarmed slot.
 *
 * Engine init kicks off lazily on construction. Once engine is ready, a
 * prewarm coroutine creates the synthesis Conversation and sends a tiny dummy
 * message, cancelling after the first token. Issue page is the wow-moment
 * screen and benefits most from a warm cache; transient surfaces (mechanic
 * draft, history) lazy-create on entry and accept the cold first-token
 * latency. When a transient surface takes ownership, the next return to
 * synthesis pays a fresh system-prompt prefill — acceptable given the linear
 * demo flow.
 */
class GemmaService(
    private val context: Context,
    appScope: CoroutineScope,
) {
    val promptBuilder = PromptBuilder(context)

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var currentSurface: String? = null
    private val convoMutex = Mutex()

    /**
     * Monotonic nanos of the most recent Conversation.close(). The next
     * createConversation waits until at least [NATIVE_SETTLE_MS] have passed
     * since this stamp — close() returns to Kotlin before LiteRT-LM 0.11.0
     * finishes native-side teardown, and a follow-up RunPrefillAsync against
     * the new Conversation can SIGSEGV inside liblitertlm_jni.so if the old
     * one's cleanup is still in flight. See FUTURE_WORK "native instability
     * on rapid surface churn" for the crash signature.
     */
    @Volatile private var lastCloseNs: Long = 0L

    /** Set to a non-null Throwable if engine init failed; callers fall back. */
    @Volatile var initError: Throwable? = null
        private set

    private val initJob: Job = appScope.async(Dispatchers.IO) {
        try {
            val modelFile = stageModel()
            // Capability-gated Multi-Token Prediction. LiteRT-LM 0.11.0 release
            // notes claim >2× decode on Gemma 4 mobile GPU with zero quality
            // degradation when the model file carries the draft head.
            // Capabilities reads the .litertlm header — cheap relative to the
            // Engine.initialize() that follows. We probe at runtime instead of
            // hardcoding so an E2B-vs-E4B variant swap doesn't enable MTP
            // against a model that doesn't support it.
            val mtpSupported = try {
                Capabilities(modelFile.absolutePath).use { it.hasSpeculativeDecodingSupport() }
            } catch (t: Throwable) {
                Log.w(TAG, "mtp probe failed: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            ExperimentalFlags.enableSpeculativeDecoding = if (mtpSupported) true else null
            Log.i(METRIC_TAG, "mtp model=${BuildConfig.MODEL_VARIANT} enabled=$mtpSupported")
            // Silent-off guard: when the staged .litertlm has no draft head the
            // flag falls back to the model default (off) and every surface
            // decodes at the unaccelerated rate with no error — the failure mode
            // is invisible without this line. Re-stage an MTP-enabled Gemma 4
            // build if this fires on a release where speedup is expected.
            if (!mtpSupported) {
                Log.w(TAG, "MTP unavailable for ${BuildConfig.MODEL_VARIANT}: model carries no draft head; decode runs unaccelerated")
            }
            // Enable Conversation.getBenchmarkInfo(). Surfaces SDK-authoritative
            // init time, time-to-first-token, prefill/decode token counts, and
            // prefill/decode TPS — gives us a real answer to the "is the
            // hoisted Conversation actually reusing KV cache" question from
            // FUTURE_WORK §BenchmarkInfo introspection.
            ExperimentalFlags.enableBenchmark = true
            val e = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    maxNumTokens = MAX_CONTEXT_TOKENS,
                    cacheDir = context.cacheDir.absolutePath,
                )
            )
            e.initialize()
            engine = e
            Log.i(TAG, "engine initialized")
        } catch (t: Throwable) {
            initError = t
            Log.w(TAG, "engine init failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private val prewarmJob: Job = appScope.launch(Dispatchers.IO) {
        initJob.join()
        if (initError != null || engine == null) {
            Log.i(METRIC_TAG, "prewarm skipped: engine not initialized")
            return@launch
        }
        val warmStart = System.nanoTime()
        try {
            convoMutex.withLock {
                val convo = acquireConversationForSurfaceLocked("synthesis") ?: return@withLock
                // LiteRT-LM Flow does NOT auto-cancel underlying generation when the
                // consumer stops collecting (callbackFlow.awaitClose is empty), so we
                // must call cancelProcess() explicitly to stop decode after first
                // token. Trade-off: the prewarm turn (user message + the partial
                // assistant decode before cancel) stays in conversation history.
                //
                // Since we're paying that pollution either way, the user message is
                // designed as a few-shot voice anchor — it carries one worked example
                // of a synthesis input + the ideal JSON output in the friend-on-the-
                // phone voice. The next real synthesis call has just seen the shape
                // and tone it should produce, recovering the slight phrasing drift
                // we used to see after a "ok"-only prewarm. Asking for "ok" back
                // keeps the decoded-and-cancelled token cheap.
                var cancelled = false
                try {
                    convo.sendMessageAsync(PREWARM_FEW_SHOT).collect { _ ->
                        if (!cancelled) {
                            cancelled = true
                            try { convo.cancelProcess() } catch (_: Throwable) {}
                        }
                    }
                } catch (_: CancellationException) {
                    // expected when cancel propagates
                } catch (t: Throwable) {
                    Log.w(TAG, "prewarm collect: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
            val warmMs = (System.nanoTime() - warmStart) / 1_000_000
            Log.i(METRIC_TAG, "prewarm model=${BuildConfig.MODEL_VARIANT} warmup_ms=$warmMs")
        } catch (t: Throwable) {
            Log.w(TAG, "prewarm failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Suspends until engine init AND prewarm both complete. */
    suspend fun awaitReady() {
        initJob.join()
        prewarmJob.join()
    }

    /**
     * Streams the assistant's response as per-token deltas. The caller appends.
     * On any error returns a flow that emits nothing and completes — caller's
     * empty buffer will fall back via [com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE].
     *
     * Serialized via [convoMutex] so only one generation runs at a time. If the
     * Conversation slot is currently owned by a different surface, it is
     * closed and recreated — Phase 10A trade-off for LiteRT-LM's
     * one-Conversation-per-Engine constraint. On error, the Conversation is
     * closed and nulled so the next call rebuilds it (preserving the Phase 5
     * resilience contract).
     */
    fun streamSynthesis(issue: Issue, classification: Classification? = null): Flow<String> =
        streamSurface("synthesis") { promptBuilder.renderSynthesisPrompt(issue, classification) }

    /**
     * Streams the mechanic-draft assistant response as per-token deltas,
     * parallel in shape to [streamSynthesis]. The caller appends. On any error
     * returns a flow that emits nothing and completes — caller's empty buffer
     * will fall back via [com.example.carcopilot.model.Issue.mechanicDraft].
     *
     * Lazy-creates the draft Conversation on first entry; if the slot is
     * currently owned by synthesis, closes it and recreates fresh. First-token
     * latency therefore includes a cold system-prompt prefill the first time
     * the user navigates here in a session.
     */
    fun streamMechanicDraft(issue: Issue): Flow<String> =
        streamSurface("draft", sampler = MTP_NARRATIVE_SAMPLER) {
            promptBuilder.renderMechanicDraftPrompt(issue)
        }

    /**
     * Streams the history-pattern assistant response as per-token deltas,
     * parallel in shape to [streamMechanicDraft]. The caller appends. On any
     * error returns a flow that emits nothing and completes — caller's empty
     * buffer falls back via [com.example.carcopilot.model.History.PATTERN].
     *
     * Lazy-creates the history Conversation on first entry; if the slot is
     * held by synthesis or draft, closes it and recreates fresh. First-token
     * latency therefore includes a cold system-prompt prefill the first time
     * the user opens History in a session.
     */
    fun streamHistoryPattern(history: List<HistoryEntry>, currentIssue: Issue?): Flow<String> =
        streamSurface("history", sampler = MTP_NARRATIVE_SAMPLER) {
            promptBuilder.renderHistoryPatternPrompt(history, currentIssue)
        }

    /**
     * Streams the walkthrough plan envelope as per-token deltas. The caller
     * appends and feeds the assembled JSON through
     * [com.example.carcopilot.ui.parseWalkthroughPlanOrFallback]. On any
     * error (engine missing, missing curated procedure, generation failure)
     * the flow emits nothing and the screen's empty buffer falls back to the
     * canned [com.example.carcopilot.data.DTCEntry.walkthroughSteps].
     *
     * Plan and step calls share the surface tag `walkthrough` on purpose:
     * the plan stays in the Conversation's KV cache so each subsequent step
     * generation sees the plan turn (plus prior step turns) and stays
     * coherent with what the user is already looking at. Metric label
     * distinguishes the two phases for performance triage.
     */
    fun streamWalkthroughPlan(issue: Issue): Flow<String> =
        streamSurface(
            surface = "walkthrough",
            metricLabel = "walkthrough_plan",
            sampler = MTP_NARRATIVE_SAMPLER,
        ) {
            promptBuilder.renderWalkthroughPlanPrompt(issue)
        }

    /**
     * Streams the body for a single walkthrough step as per-token deltas.
     * Caller assembles and feeds through
     * [com.example.carcopilot.ui.parseWalkthroughStepOrFallback]. On any
     * error the flow emits nothing and the screen falls back to the canned
     * [com.example.carcopilot.model.WalkthroughStep.body] from
     * [com.example.carcopilot.data.DTCEntry.walkthroughSteps].
     *
     * Tags the surface as `walkthrough_step_${planStep.number}` so each
     * step forces a Conversation close+recreate vs. the prior plan or step
     * call. The W1 design shared the surface tag `walkthrough` across plan
     * and steps to reuse KV cache, but Pixel 9 + E4B + GPU empirically
     * fails on step-1's invocation when the plan turn (≈1200 token input +
     * ≈150 token output) is still in KV — the compiled model executor
     * throws `Status Code: 13 Failed to invoke the compiled model` at
     * llm_litert_compiled_model_executor.cc:756. Fresh convo per step
     * gives each call only the system prompt in KV, comfortably fitting
     * the compiled model's effective context cap. The trade-off is one
     * extra system-prompt prefill (~250 tokens, ~1s) and one
     * NATIVE_SETTLE_MS wait per step.
     */
    fun streamWalkthroughStep(
        issue: Issue,
        planStep: PlanStep,
        totalSteps: Int,
    ): Flow<String> =
        streamSurface(
            surface = "walkthrough_step_${planStep.number}",
            sampler = STEP_NUMERIC_FIDELITY_SAMPLER,
        ) {
            promptBuilder.renderWalkthroughStepPrompt(issue, planStep, totalSteps)
        }

    /**
     * The one streaming path behind every public `stream*` surface. Each
     * surface differs only in three axes: the KV-cache [surface] tag, the
     * [sampler], and the [buildPrompt] lambda; everything else — the engine
     * guard, the [convoMutex] serialization, the per-token instrumentation,
     * the SIGSEGV-mitigation reset on failure (NonCancellable cancel + close +
     * NATIVE_SETTLE_MS stamp), and the metric/benchmark logging — is identical
     * and now lives here once.
     *
     * [metricLabel] defaults to [surface] but is split out for the walkthrough
     * plan, whose KV-cache tag is `walkthrough` (shared with steps for cache
     * locality) while its metric line reads `walkthrough_plan`.
     *
     * On error the Conversation is closed and nulled so the next call rebuilds
     * it, preserving the Phase-5 resilience contract: the flow rethrows, the
     * caller's collector sees the failure, and its empty/partial buffer falls
     * back to canned text.
     */
    private fun streamSurface(
        surface: String,
        metricLabel: String = surface,
        sampler: SamplerConfig = DEFAULT_SAMPLER,
        buildPrompt: () -> String,
    ): Flow<String> = flow {
        if (engine == null) {
            Log.w(TAG, "stream[$metricLabel]: engine not initialized; emitting empty")
            return@flow
        }
        convoMutex.withLock {
            val convo = acquireConversationForSurfaceLocked(surface, sampler) ?: run {
                Log.w(TAG, "stream[$metricLabel]: conversation creation failed; emitting empty")
                return@withLock
            }
            // Phase 8 instrumentation — model=…, first_token=…ms, total=…ms, tokens=…, tps=…
            val sendStart = System.nanoTime()
            var firstTokenNs: Long = -1
            var tokenCount = 0
            val rawBuf = StringBuilder()
            var failed = false
            try {
                convo.sendMessageAsync(buildPrompt())
                    .collect { message ->
                        if (firstTokenNs < 0) firstTokenNs = System.nanoTime()
                        tokenCount += 1
                        val s = message.toString()
                        rawBuf.append(s)
                        emit(s)
                    }
            } catch (t: Throwable) {
                failed = true
                Log.w(TAG, "stream[$metricLabel] collect: ${t.javaClass.simpleName}: ${t.message}")
                // Reset hoisted Conversation — it may be in a bad state. Wrapped
                // in NonCancellable so a propagating CancellationException can't
                // skip cancelProcess/close/timestamp, and the next surface acquire
                // can safely wait NATIVE_SETTLE_MS from lastCloseNs.
                withContext(NonCancellable) {
                    try { convo.cancelProcess() } catch (_: Throwable) {}
                    try { convo.close() } catch (_: Throwable) {}
                    conversation = null
                    currentSurface = null
                    lastCloseNs = System.nanoTime()
                }
                throw t
            } finally {
                logSurfaceMetrics(
                    surfaceLabel = metricLabel,
                    sendStart = sendStart,
                    firstTokenNs = firstTokenNs,
                    tokenCount = tokenCount,
                    rawBuf = rawBuf,
                    failed = failed,
                )
                if (!failed) logBenchmarkInfo(metricLabel, convo)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Single-line writer for [Conversation.getBenchmarkInfo]. Gated by
     * [ExperimentalFlags.enableBenchmark] set at Engine init — when the flag
     * is off, the SDK reports zeros and we still log them (harmless, the
     * shape stays grep-able). Defensive: a conversation closed in the surface
     * method's catch handler will throw when this fires from finally, so
     * swallow rather than mask the underlying failure.
     */
    private fun logBenchmarkInfo(surfaceLabel: String, convo: Conversation) {
        val info: BenchmarkInfo = try {
            convo.getBenchmarkInfo()
        } catch (_: Throwable) {
            return
        }
        Log.i(
            METRIC_TAG,
            "bench surface=$surfaceLabel model=${BuildConfig.MODEL_VARIANT} " +
                "init=${"%.3f".format(info.initTimeInSecond)}s " +
                "ttft=${"%.3f".format(info.timeToFirstTokenInSecond)}s " +
                "prefill_tokens=${info.lastPrefillTokenCount} " +
                "decode_tokens=${info.lastDecodeTokenCount} " +
                "prefill_tps=${"%.2f".format(info.lastPrefillTokensPerSecond)} " +
                "decode_tps=${"%.2f".format(info.lastDecodeTokensPerSecond)}"
        )
    }

    /**
     * Single-line metric writer for every surface. Emits the `infer surface=…`
     * line (first-token / total / steady-state TPS) and, on success, the
     * `infer_raw surface=…` capture. Called once from [streamSurface]; the
     * SDK-authoritative `bench surface=…` line is emitted alongside it via
     * [logBenchmarkInfo].
     */
    private fun logSurfaceMetrics(
        surfaceLabel: String,
        sendStart: Long,
        firstTokenNs: Long,
        tokenCount: Int,
        rawBuf: StringBuilder,
        failed: Boolean,
    ) {
        val totalMs = (System.nanoTime() - sendStart) / 1_000_000
        val firstMs = if (firstTokenNs > 0) (firstTokenNs - sendStart) / 1_000_000 else -1L
        val steadyTokens = (tokenCount - 1).coerceAtLeast(0)
        val steadyMs = (totalMs - firstMs).coerceAtLeast(1)
        val tps = if (steadyTokens > 0) steadyTokens * 1000.0 / steadyMs else 0.0
        Log.i(
            METRIC_TAG,
            "infer surface=$surfaceLabel model=${BuildConfig.MODEL_VARIANT} " +
                "first_token=${firstMs}ms total=${totalMs}ms tokens=$tokenCount " +
                "tps=${"%.2f".format(tps)}" + (if (failed) " failed=true" else "")
        )
        if (!failed) {
            Log.i(
                METRIC_TAG,
                "infer_raw surface=$surfaceLabel model=${BuildConfig.MODEL_VARIANT} text=${
                    rawBuf.toString().replace("\n", "\\n").replace("\r", "")
                }"
            )
        }
    }

    /**
     * Return the Conversation owned by [surface], creating it if the slot is
     * empty or held by a different surface. Must be called under [convoMutex].
     *
     * On surface switch, the existing Conversation is closed before the new
     * one is allocated — LiteRT-LM 0.11.0 rejects a second createConversation
     * while another session is open with FAILED_PRECONDITION. The function
     * suspends because it may need to wait [NATIVE_SETTLE_MS] since the last
     * close (whether on this acquire or on a prior catch handler's cleanup)
     * before calling createConversation. Holding the mutex across that wait
     * is intentional — it's the synchronization point that keeps a second
     * surface from racing the previous Conversation's native teardown.
     */
    private suspend fun acquireConversationForSurfaceLocked(
        surface: String,
        sampler: SamplerConfig = DEFAULT_SAMPLER,
    ): Conversation? {
        val existing = conversation
        if (existing != null && currentSurface == surface) return existing
        if (existing != null) {
            try { existing.cancelProcess() } catch (_: Throwable) {}
            try { existing.close() } catch (_: Throwable) {}
            conversation = null
            currentSurface = null
            lastCloseNs = System.nanoTime()
            Log.i(TAG, "conversation closed for surface switch → $surface")
        }
        val sinceCloseMs = (System.nanoTime() - lastCloseNs) / 1_000_000
        if (lastCloseNs != 0L && sinceCloseMs < NATIVE_SETTLE_MS) {
            val waitMs = NATIVE_SETTLE_MS - sinceCloseMs
            Log.i(TAG, "settle: waiting ${waitMs}ms for native cleanup before createConversation[$surface]")
            delay(waitMs)
        }
        val e = engine ?: return null
        return try {
            val c = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(promptBuilder.systemPrompt),
                    samplerConfig = sampler,
                )
            )
            conversation = c
            currentSurface = surface
            Log.i(TAG, "conversation[$surface] created")
            c
        } catch (t: Throwable) {
            Log.w(TAG, "createConversation[$surface]: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun stageModel(): File {
        val target = File(context.filesDir, MODEL_FILE)
        val staged = File(STAGED_PATH)
        if (target.exists() && staged.exists() && target.length() == staged.length()) {
            Log.i(TAG, "model already staged: ${target.absolutePath}")
            return target
        }
        if (target.exists() && !staged.exists()) {
            // Previously copied; staged source missing (typical for repeat launches).
            Log.i(TAG, "model present, staged source missing — reusing: ${target.absolutePath}")
            return target
        }
        require(staged.exists()) {
            "Model missing at $STAGED_PATH. Run: adb push $MODEL_FILE /data/local/tmp/"
        }
        Log.i(TAG, "copying model ${staged.length()} bytes → ${target.absolutePath}")
        staged.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
        }
        return target
    }
}
