package com.example.carcopilot.ui

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Pace a token-delta [Flow] into a smooth per-character "typewriter" stream.
 *
 * Gemma emits chunky multi-character deltas at ~5 tok/s on Pixel 9 — rendered
 * raw, that reads as a stutter. We buffer the deltas into a target string and
 * drain one character at a time on a fixed interval so the UI looks like a
 * steady stream. The trade is a few hundred ms of perceived latency for a
 * much smoother visual; that trade is the whole point of the demo.
 *
 * The producer (model) and the consumer (UI tick) run as two siblings under
 * [coroutineScope]. When the upstream Flow completes we keep ticking until
 * the displayed length catches the final target, then return. If the parent
 * [LaunchedEffect] cancels, both children cancel with it — structured
 * concurrency handles teardown.
 *
 * @param source upstream delta Flow.
 * @param extractDisplay maps the *accumulated raw buffer* to the *display
 *     target* string. Synthesis/draft/history all wrap content in a JSON
 *     envelope, so this is where the per-surface in-progress extractor
 *     plugs in. Return empty for "show nothing yet" — we won't tick until
 *     it goes non-empty.
 * @param onStreaming invoked on each character tick with the cumulative
 *     displayed text. Drives [SynthesisState.Streaming] (or surface
 *     equivalent).
 * @param onDone invoked once with the full raw buffer after the upstream
 *     completes and we've drained everything. Callers use it for the final
 *     `parseOrFallback(...)` decision.
 * @param charIntervalMs minimum gap between rendered chars. 25ms ≈ 40 cps,
 *     which keeps the buffer from running away on Gemma's bursts while
 *     still feeling responsive.
 */
suspend fun typewriterCollect(
    source: Flow<String>,
    extractDisplay: (String) -> String,
    onStreaming: (String) -> Unit,
    onDone: (rawBuffer: String) -> Unit,
    charIntervalMs: Long = 25L,
): Unit = coroutineScope {
    val rawBuf = StringBuilder()
    // Producer (model collector) and consumer (tick loop) run on potentially
    // different threads under coroutineScope's dispatcher; AtomicReference /
    // AtomicBoolean give us safe publication of `target` and `producerDone`
    // without pulling in MutableStateFlow.
    val target = AtomicReference("")
    val producerDone = AtomicBoolean(false)

    val producer = launch {
        try {
            source.collect { delta ->
                rawBuf.append(delta)
                target.set(extractDisplay(rawBuf.toString()))
            }
        } finally {
            producerDone.set(true)
        }
    }

    var displayed = ""
    while (true) {
        val t = target.get()
        if (displayed.length < t.length) {
            displayed = t.substring(0, displayed.length + 1)
            onStreaming(displayed)
            delay(charIntervalMs)
        } else if (producerDone.get()) {
            break
        } else {
            // Producer hasn't given us more chars yet; idle briefly.
            delay(16)
        }
    }
    producer.join()
    onDone(rawBuf.toString())
}
