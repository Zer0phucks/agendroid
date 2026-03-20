// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/llm/LlmInferenceRunner.kt
package com.agendroid.spike.callpipeline.llm

import android.content.Context
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps MediaPipe LlmInference and measures per-call timing.
 * Create once; reuse across turns. Not thread-safe — call from a single coroutine.
 *
 * API note (MediaPipe tasks-genai 0.10.14):
 *   - The result listener is registered via [LlmInference.LlmInferenceOptions.builder().setResultListener()]
 *     at construction time, NOT passed to generateResponseAsync.
 *   - [LlmInference.generateResponseAsync] accepts only the prompt String.
 *   - The listener receives (partialResult: String, done: Boolean) via
 *     [OutputHandler.ProgressListener.run].
 *
 * Manual step — push model to device before running on hardware:
 *   adb push gemma3-1b-it-int4.task /sdcard/Download/agendroid-spike/
 * Model path on device: /sdcard/Download/agendroid-spike/gemma3-1b-it-int4.task
 * Source: litert-community/Gemma3-1B-IT on HuggingFace (gemma3-1b-it-int4.task)
 */
class LlmInferenceRunner(
    private val context: Context,
    private val modelPath: String,
    private val maxTokens: Int = 50,
) {
    private var llm: LlmInference? = null

    // Shared mutable state for the per-call listener wired at construction.
    // Each call to runTurn sets these before invoking generateResponseAsync.
    private val callLatch = AtomicReference<CountDownLatch?>()
    private val callFirstTokenMs = AtomicLong(-1L)
    private val callStartMs = AtomicLong(0L)
    private val callBuffer = AtomicReference<StringBuilder?>()

    // Fix 1: tainted-buffer guard — set to true when runTurn() times out so the
    // still-running listener does not corrupt the next call's state.
    private val callTimedOut = AtomicReference<AtomicBoolean?>(null)

    // Fix 3: per-call first-token-seen flag, eliminating the -2L intermediate state.
    private val callFirstTokenSeen = AtomicReference<AtomicBoolean?>(null)

    data class InferenceResult(
        val text: String,
        val firstTokenMs: Long,   // time from prompt submission to first token received
        val totalMs: Long,        // time from prompt submission to generation complete
    )

    /**
     * Loads the model. Call once before [runTurn].
     *
     * The result listener is registered here so that MediaPipe can route streaming
     * tokens to whichever [runTurn] call is currently active. Each [runTurn] call
     * sets [callLatch], [callFirstTokenMs], [callStartMs], and [callBuffer] before
     * invoking [LlmInference.generateResponseAsync], and the listener reads them.
     */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        val listener = OutputHandler.ProgressListener<String> { partialResult, done ->
            // Fix 1: if the previous call timed out, discard all tokens from its
            // still-running MediaPipe thread so they cannot corrupt the next call.
            if (callTimedOut.get()?.get() == true) return@ProgressListener

            val startMs = callStartMs.get()

            // Fix 3: use the per-call AtomicBoolean instead of the -2L CAS pattern.
            val seen = callFirstTokenSeen.get()
            if (partialResult.isNotEmpty() && seen != null && !seen.getAndSet(true)) {
                callFirstTokenMs.set(System.currentTimeMillis() - startMs)
            }

            callBuffer.get()?.append(partialResult)
            if (done) {
                callLatch.get()?.countDown()
            }
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setTopK(1)           // greedy for consistent latency measurement
            .setTemperature(0f)
            .setResultListener(listener)
            .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    /** Runs one inference turn and returns timing. Must call [load] first. */
    suspend fun runTurn(prompt: String): InferenceResult = withContext(Dispatchers.IO) {
        checkNotNull(llm) { "Call load() before runTurn()" }

        // Poison any still-running listener from the previous (possibly timed-out) call
        // BEFORE touching any other shared state, so it bails out immediately.
        callTimedOut.get()?.set(true)

        // Reset per-call state before triggering generation.
        val latch = CountDownLatch(1)
        val sb = StringBuilder()
        callBuffer.set(sb)

        // Fix 3: install a fresh first-token-seen flag and reset the timestamp.
        val firstTokenSeen = AtomicBoolean(false)
        callFirstTokenSeen.set(firstTokenSeen)
        callFirstTokenMs.set(-1L)

        // Fix 1: install a fresh timed-out flag for this call.
        val timedOut = AtomicBoolean(false)
        callTimedOut.set(timedOut)

        // Fix 2: callStartMs must be set before callLatch so the listener never
        // reads a stale start time if it fires between the two assignments.
        val startMs = System.currentTimeMillis()
        callStartMs.set(startMs)    // must be before callLatch.set
        callLatch.set(latch)

        llm!!.generateResponseAsync(prompt)

        // Fix 1: capture the return value so we can mark the call as timed out.
        val completed = latch.await(30, TimeUnit.SECONDS)
        if (!completed) timedOut.set(true)

        val totalMs = System.currentTimeMillis() - startMs
        val firstTokenMs = callFirstTokenMs.get().let { if (it >= 0) it else totalMs }

        InferenceResult(
            text = sb.toString(),
            firstTokenMs = firstTokenMs,
            totalMs = totalMs,
        )
    }

    fun close() {
        llm?.close()
        llm = null
    }
}
