package com.example.carcopilot.inference

import android.content.Context
import android.util.Log
import com.example.carcopilot.BuildConfig
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
 * Owns the LiteRT-LM Engine + a long-lived Conversation. Construct once per
 * process (from CarCopilotApp); never per-screen.
 *
 * Engine init kicks off lazily on construction. Once engine is ready, a
 * prewarm coroutine sends a tiny dummy message and cancels after the first
 * token to warm the system-prompt KV cache. Subsequent real synthesis calls
 * reuse the hoisted Conversation, so the system prompt's prefill is paid once
 * per process rather than per issue.
 */
class GemmaService(
    private val context: Context,
    appScope: CoroutineScope,
) {
    val promptBuilder = PromptBuilder(context)

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
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
                val convo = ensureConversationLocked() ?: return@withLock
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
     * Serialized via [convoMutex] so only one synthesis runs at a time on the
     * shared Conversation. On error, the Conversation is closed and nulled so
     * the next call rebuilds it (preserving the Phase 5 resilience contract).
     */
    fun streamSynthesis(issue: Issue): Flow<String> = flow {
        if (engine == null) {
            Log.w(TAG, "streamSynthesis: engine not initialized; emitting empty")
            return@flow
        }
        convoMutex.withLock {
            val convo = ensureConversationLocked() ?: run {
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
                throw t
            } finally {
                val totalMs = (System.nanoTime() - sendStart) / 1_000_000
                val firstMs = if (firstTokenNs > 0) (firstTokenNs - sendStart) / 1_000_000 else -1L
                val steadyTokens = (tokenCount - 1).coerceAtLeast(0)
                val steadyMs = (totalMs - firstMs).coerceAtLeast(1)
                val tps = if (steadyTokens > 0) steadyTokens * 1000.0 / steadyMs else 0.0
                Log.i(
                    METRIC_TAG,
                    "infer model=${BuildConfig.MODEL_VARIANT} first_token=${firstMs}ms " +
                        "total=${totalMs}ms tokens=$tokenCount tps=${"%.2f".format(tps)}" +
                        (if (failed) " failed=true" else "")
                )
                if (!failed) {
                    Log.i(
                        METRIC_TAG,
                        "infer_raw model=${BuildConfig.MODEL_VARIANT} text=${
                            rawBuf.toString().replace("\n", "\\n").replace("\r", "")
                        }"
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Build the hoisted Conversation if missing. Must be called under [convoMutex]. */
    private fun ensureConversationLocked(): Conversation? {
        conversation?.let { return it }
        val e = engine ?: return null
        return try {
            val c = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(promptBuilder.systemPrompt),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                )
            )
            conversation = c
            Log.i(TAG, "conversation created")
            c
        } catch (t: Throwable) {
            Log.w(TAG, "createConversation: ${t.javaClass.simpleName}: ${t.message}")
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
