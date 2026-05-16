package com.example.carcopilot.inference

import android.content.Context
import android.util.Log
import com.example.carcopilot.model.Issue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import java.io.File

private const val MODEL_FILE = "gemma-4-E4B-it.litertlm"
private const val STAGED_PATH = "/data/local/tmp/$MODEL_FILE"
private const val TAG = "GemmaService"

/**
 * Owns the LiteRT-LM Engine lifecycle and exposes streaming synthesis.
 * Construct once per process (from CarCopilotApp); never per-screen.
 *
 * Init kicks off lazily on first construction so the engine is warming
 * while the user sits on Home. By the time they tap into Issue, the
 * 30-60s shader-cache build is usually done.
 */
class GemmaService(
    private val context: Context,
    appScope: CoroutineScope,
) {
    val promptBuilder = PromptBuilder(context)

    /** Deferred engine — null until [initJob] resolves successfully. */
    @Volatile private var engine: Engine? = null

    /** Set to a non-null Throwable if init failed; callers fall back. */
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

    /** Suspends until init completes (success or failure). */
    suspend fun awaitReady() {
        initJob.join()
    }

    /**
     * Streams the assistant's response as per-token deltas. The caller appends.
     * On any error returns a flow that emits nothing and completes — caller's
     * empty buffer will fall back via [com.example.carcopilot.model.FALLBACK_SYNTHESIS_MISFIRE].
     */
    fun streamSynthesis(issue: Issue): Flow<String> = flow {
        val e = engine ?: run {
            Log.w(TAG, "streamSynthesis: engine not initialized; emitting empty")
            return@flow
        }
        val convo = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(promptBuilder.systemPrompt),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
            )
        )
        try {
            convo.sendMessageAsync(promptBuilder.renderSynthesisPrompt(issue))
                .collect { message -> emit(message.toString()) }
        } finally {
            try { convo.close() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.IO).onCompletion { /* engine stays open for the process lifetime */ }

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
