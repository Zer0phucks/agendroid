// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/llm/LlmInferenceRunner.kt
package com.agendroid.spike.callpipeline.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps MediaPipe LlmInference and measures per-call timing.
 * Create once; reuse across turns. Not thread-safe — call from a single coroutine.
 *
 * API note (MediaPipe tasks-genai 0.10.32):
 *   - The ProgressListener is passed directly to generateResponseAsync per call.
 *   - generateResponseAsync(prompt, listener) streams partial tokens to the listener.
 *   - The listener receives (partialResult: String, done: Boolean).
 *   - setTopK/setTemperature/setResultListener removed; use setMaxTopK on the model options.
 *
 * Manual step — copy model to app's internal files dir before running on hardware:
 *   adb shell run-as com.agendroid.spike.callpipeline cp \
 *       /data/local/tmp/gemma3-1b-it-int4.task \
 *       /data/data/com.agendroid.spike.callpipeline/files/gemma3-1b-it-int4.task
 * Model source: litert-community/Gemma3-1B-IT on HuggingFace (gemma3-1b-it-int4.task)
 */
class LlmInferenceRunner(
    private val context: Context,
    private val modelPath: String,
    private val maxTokens: Int = 50,
) {
    private var llm: LlmInference? = null

    data class InferenceResult(
        val text: String,
        val firstTokenMs: Long,   // time from prompt submission to first token received
        val totalMs: Long,        // time from prompt submission to generation complete
    )

    /** Loads the model. Call once before [runTurn]. */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setMaxTopK(1)    // greedy for consistent latency measurement
            .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    /** Runs one inference turn and returns timing. Must call [load] first. */
    suspend fun runTurn(prompt: String): InferenceResult = withContext(Dispatchers.IO) {
        checkNotNull(llm) { "Call load() before runTurn()" }

        val latch = CountDownLatch(1)
        val sb = StringBuilder()
        val startMs = System.currentTimeMillis()
        val firstTokenMs = AtomicLong(-1L)

        val listener = ProgressListener<String> { partialResult, done ->
            if (partialResult.isNotEmpty() && firstTokenMs.compareAndSet(-1L, System.currentTimeMillis() - startMs)) {
                // first non-empty token — timestamp captured above via CAS
            }
            sb.append(partialResult)
            if (done) latch.countDown()
        }

        llm!!.generateResponseAsync(prompt, listener)

        latch.await(30, TimeUnit.SECONDS)

        val totalMs = System.currentTimeMillis() - startMs
        val ftMs = firstTokenMs.get().let { if (it >= 0) it else totalMs }

        InferenceResult(
            text = sb.toString(),
            firstTokenMs = ftMs,
            totalMs = totalMs,
        )
    }

    fun close() {
        llm?.close()
        llm = null
    }
}
