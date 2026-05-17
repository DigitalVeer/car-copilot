package com.example.carcopilot.inference

import android.content.Context
import android.util.Log
import com.example.carcopilot.BuildConfig
import com.example.carcopilot.model.HistoryEntry
import com.example.carcopilot.model.Issue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Set to a non-null Throwable if engine init failed; callers fall back. */
    @Volatile var initError: Throwable? = null
        private set

    private val initJob: Job = appScope.async(Dispatchers.IO) {
        try {
            val modelFile = stageModel()
            val e = Engine(
                EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.GPU())
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
                // Cheapest possible full generation: send "ok", take first token, cancel.
                // LiteRT-LM Flow does NOT auto-cancel underlying generation when the
                // consumer stops collecting (callbackFlow.awaitClose is empty), so we
                // must call cancelProcess() explicitly to stop decode.
                // Trade-off: the dummy turn (user="ok", assistant="<one token>") stays
                // in conversation history, polluting context for the first real call.
                var cancelled = false
                try {
                    convo.sendMessageAsync("ok").collect { _ ->
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
    fun streamSynthesis(issue: Issue): Flow<String> = flow {
        if (engine == null) {
            Log.w(TAG, "streamSynthesis: engine not initialized; emitting empty")
            return@flow
        }
        convoMutex.withLock {
            val convo = acquireConversationForSurfaceLocked("synthesis") ?: run {
                Log.w(TAG, "streamSynthesis: conversation creation failed; emitting empty")
                return@withLock
            }
            // Phase 8 instrumentation — model=…, first_token=…ms, total=…ms, tokens=…, tps=…
            val sendStart = System.nanoTime()
            var firstTokenNs: Long = -1
            var tokenCount = 0
            val rawBuf = StringBuilder()
            var failed = false
            try {
                convo.sendMessageAsync(promptBuilder.renderSynthesisPrompt(issue))
                    .collect { message ->
                        if (firstTokenNs < 0) firstTokenNs = System.nanoTime()
                        tokenCount += 1
                        val s = message.toString()
                        rawBuf.append(s)
                        emit(s)
                    }
            } catch (t: Throwable) {
                failed = true
                Log.w(TAG, "streamSynthesis collect: ${t.javaClass.simpleName}: ${t.message}")
                // Reset hoisted Conversation — it may be in a bad state.
                try { convo.close() } catch (_: Throwable) {}
                conversation = null
                currentSurface = null
                throw t
            } finally {
                val totalMs = (System.nanoTime() - sendStart) / 1_000_000
                val firstMs = if (firstTokenNs > 0) (firstTokenNs - sendStart) / 1_000_000 else -1L
                val steadyTokens = (tokenCount - 1).coerceAtLeast(0)
                val steadyMs = (totalMs - firstMs).coerceAtLeast(1)
                val tps = if (steadyTokens > 0) steadyTokens * 1000.0 / steadyMs else 0.0
                Log.i(
                    METRIC_TAG,
                    "infer surface=synthesis model=${BuildConfig.MODEL_VARIANT} " +
                        "first_token=${firstMs}ms total=${totalMs}ms tokens=$tokenCount " +
                        "tps=${"%.2f".format(tps)}" + (if (failed) " failed=true" else "")
                )
                if (!failed) {
                    Log.i(
                        METRIC_TAG,
                        "infer_raw surface=synthesis model=${BuildConfig.MODEL_VARIANT} text=${
                            rawBuf.toString().replace("\n", "\\n").replace("\r", "")
                        }"
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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
    fun streamMechanicDraft(issue: Issue): Flow<String> = flow {
        if (engine == null) {
            Log.w(TAG, "streamMechanicDraft: engine not initialized; emitting empty")
            return@flow
        }
        convoMutex.withLock {
            val convo = acquireConversationForSurfaceLocked("draft") ?: run {
                Log.w(TAG, "streamMechanicDraft: conversation creation failed; emitting empty")
                return@withLock
            }
            val sendStart = System.nanoTime()
            var firstTokenNs: Long = -1
            var tokenCount = 0
            val rawBuf = StringBuilder()
            var failed = false
            try {
                convo.sendMessageAsync(promptBuilder.renderMechanicDraftPrompt(issue))
                    .collect { message ->
                        if (firstTokenNs < 0) firstTokenNs = System.nanoTime()
                        tokenCount += 1
                        val s = message.toString()
                        rawBuf.append(s)
                        emit(s)
                    }
            } catch (t: Throwable) {
                failed = true
                Log.w(TAG, "streamMechanicDraft collect: ${t.javaClass.simpleName}: ${t.message}")
                try { convo.close() } catch (_: Throwable) {}
                conversation = null
                currentSurface = null
                throw t
            } finally {
                val totalMs = (System.nanoTime() - sendStart) / 1_000_000
                val firstMs = if (firstTokenNs > 0) (firstTokenNs - sendStart) / 1_000_000 else -1L
                val steadyTokens = (tokenCount - 1).coerceAtLeast(0)
                val steadyMs = (totalMs - firstMs).coerceAtLeast(1)
                val tps = if (steadyTokens > 0) steadyTokens * 1000.0 / steadyMs else 0.0
                Log.i(
                    METRIC_TAG,
                    "infer surface=draft model=${BuildConfig.MODEL_VARIANT} " +
                        "first_token=${firstMs}ms total=${totalMs}ms tokens=$tokenCount " +
                        "tps=${"%.2f".format(tps)}" + (if (failed) " failed=true" else "")
                )
                if (!failed) {
                    Log.i(
                        METRIC_TAG,
                        "infer_raw surface=draft model=${BuildConfig.MODEL_VARIANT} text=${
                            rawBuf.toString().replace("\n", "\\n").replace("\r", "")
                        }"
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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
    fun streamHistoryPattern(history: List<HistoryEntry>, currentIssue: Issue?): Flow<String> = flow {
        if (engine == null) {
            Log.w(TAG, "streamHistoryPattern: engine not initialized; emitting empty")
            return@flow
        }
        convoMutex.withLock {
            val convo = acquireConversationForSurfaceLocked("history") ?: run {
                Log.w(TAG, "streamHistoryPattern: conversation creation failed; emitting empty")
                return@withLock
            }
            val sendStart = System.nanoTime()
            var firstTokenNs: Long = -1
            var tokenCount = 0
            val rawBuf = StringBuilder()
            var failed = false
            try {
                convo.sendMessageAsync(promptBuilder.renderHistoryPatternPrompt(history, currentIssue))
                    .collect { message ->
                        if (firstTokenNs < 0) firstTokenNs = System.nanoTime()
                        tokenCount += 1
                        val s = message.toString()
                        rawBuf.append(s)
                        emit(s)
                    }
            } catch (t: Throwable) {
                failed = true
                Log.w(TAG, "streamHistoryPattern collect: ${t.javaClass.simpleName}: ${t.message}")
                try { convo.close() } catch (_: Throwable) {}
                conversation = null
                currentSurface = null
                throw t
            } finally {
                val totalMs = (System.nanoTime() - sendStart) / 1_000_000
                val firstMs = if (firstTokenNs > 0) (firstTokenNs - sendStart) / 1_000_000 else -1L
                val steadyTokens = (tokenCount - 1).coerceAtLeast(0)
                val steadyMs = (totalMs - firstMs).coerceAtLeast(1)
                val tps = if (steadyTokens > 0) steadyTokens * 1000.0 / steadyMs else 0.0
                Log.i(
                    METRIC_TAG,
                    "infer surface=history model=${BuildConfig.MODEL_VARIANT} " +
                        "first_token=${firstMs}ms total=${totalMs}ms tokens=$tokenCount " +
                        "tps=${"%.2f".format(tps)}" + (if (failed) " failed=true" else "")
                )
                if (!failed) {
                    Log.i(
                        METRIC_TAG,
                        "infer_raw surface=history model=${BuildConfig.MODEL_VARIANT} text=${
                            rawBuf.toString().replace("\n", "\\n").replace("\r", "")
                        }"
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Return the Conversation owned by [surface], creating it if the slot is
     * empty or held by a different surface. Must be called under [convoMutex].
     *
     * On surface switch, the existing Conversation is closed before the new
     * one is allocated — LiteRT-LM 0.11.0 rejects a second createConversation
     * while another session is open with FAILED_PRECONDITION.
     */
    private fun acquireConversationForSurfaceLocked(surface: String): Conversation? {
        val existing = conversation
        if (existing != null && currentSurface == surface) return existing
        if (existing != null) {
            try { existing.close() } catch (_: Throwable) {}
            conversation = null
            currentSurface = null
            Log.i(TAG, "conversation closed for surface switch → $surface")
        }
        val e = engine ?: return null
        return try {
            val c = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(promptBuilder.systemPrompt),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
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
