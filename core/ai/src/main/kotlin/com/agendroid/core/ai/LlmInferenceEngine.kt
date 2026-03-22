// core/ai/src/main/kotlin/com/agendroid/core/ai/LlmInferenceEngine.kt
package com.agendroid.core.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val MODEL_FILE   = "gemma3-1b-it-int4.task"
private const val MAX_TOKENS   = 256
private const val TIMEOUT_SEC  = 30L
private const val TOP_K        = 40
private const val TEMPERATURE  = 0.7f

/**
 * Wraps MediaPipe [LlmInference] for Gemma 3 1B int4 inference.
 *
 * Not a Hilt singleton — instantiated and owned by [AiCoreService].
 * The LLM model (~800 MB) is loaded lazily on the first [generate] call.
 *
 * Model location: [Context.getFilesDir]/gemma3-1b-it-int4.task
 * Push with: adb shell run-as com.agendroid cp /data/local/tmp/gemma3-1b-it-int4.task
 *            /data/data/com.agendroid/files/
 *
 * API note (MediaPipe tasks-genai 0.10.32):
 * Temperature and topK are session-level options set via [LlmInferenceSession].
 * Each [generate] call creates a fresh session so sampling parameters apply per-turn.
 *
 * Thread safety: NOT thread-safe. Call [generate] from a single coroutine at a time.
 * [AiCoreService] serialises calls via a Mutex.
 */
class LlmInferenceEngine(private val context: Context) {

    /** Whether the model file is present in filesDir. */
    fun isModelAvailable(): Boolean =
        context.filesDir.resolve(MODEL_FILE).exists()

    private var llm: LlmInference? = null

    /**
     * Loads the LLM into memory. Call once before [generate].
     * No-op if already loaded.
     * Throws [IllegalStateException] if the model file is not present.
     */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (llm != null) return@withContext
        val modelPath = context.filesDir.resolve(MODEL_FILE).absolutePath
        check(context.filesDir.resolve(MODEL_FILE).exists()) {
            "Gemma model not found at $modelPath. Push it with: " +
            "adb shell run-as com.agendroid cp /data/local/tmp/$MODEL_FILE " +
            "/data/data/com.agendroid/files/$MODEL_FILE"
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    /**
     * Generates a response for [prompt], streaming partial tokens to [onToken].
     *
     * Opens a fresh [LlmInferenceSession] per call with temperature=[TEMPERATURE]
     * and topK=[TOP_K]. The session is closed automatically after generation.
     *
     * [onToken] is called on an internal MediaPipe thread — do not touch UI from it.
     * Returns the complete generated text.
     *
     * @throws IllegalStateException if [load] was not called first.
     */
    suspend fun generate(
        prompt: String,
        onToken: (partial: String, done: Boolean) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val engine = checkNotNull(llm) { "Call load() before generate()" }
        val latch  = CountDownLatch(1)
        val sb     = StringBuilder()

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .build()

        LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
            val listener = ProgressListener<String> { partial, done ->
                sb.append(partial)
                onToken(partial, done)
                if (done) latch.countDown()
            }

            session.addQueryChunk(prompt)
            session.generateResponseAsync(listener)
            latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)
        }

        sb.toString()
    }

    /** Releases the LLM from memory. Safe to call multiple times. */
    fun close() {
        llm?.close()
        llm = null
    }
}
