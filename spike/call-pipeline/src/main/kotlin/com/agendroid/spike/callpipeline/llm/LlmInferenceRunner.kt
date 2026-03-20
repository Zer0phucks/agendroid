// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/llm/LlmInferenceRunner.kt
package com.agendroid.spike.callpipeline.llm

import android.content.Context
import com.google.mediapipe.tasks.core.OutputHandler
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
 *   adb push gemma3-1b-it-gpu-int4.task /sdcard/Download/agendroid-spike/
 * Model path on device: /sdcard/Download/agendroid-spike/gemma3-1b-it-gpu-int4.task
 * GPU variant preferred; fall back to gemma3-1b-it-cpu-int4.task if the GPU delegate fails.
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
            val startMs = callStartMs.get()
            if (partialResult.isNotEmpty() && callFirstTokenMs.compareAndSet(-1L, -2L)) {
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

        // Reset per-call state before triggering generation.
        val latch = CountDownLatch(1)
        val sb = StringBuilder()
        callBuffer.set(sb)
        callFirstTokenMs.set(-1L)
        callLatch.set(latch)
        val startMs = System.currentTimeMillis()
        callStartMs.set(startMs)

        llm!!.generateResponseAsync(prompt)
        latch.await(30, TimeUnit.SECONDS)

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
