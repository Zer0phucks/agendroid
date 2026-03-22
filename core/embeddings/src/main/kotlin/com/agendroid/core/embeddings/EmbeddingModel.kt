package com.agendroid.core.embeddings

import android.content.Context
import com.agendroid.core.common.di.DefaultDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
// TODO: Migrate to com.google.ai.edge.litert.* when GpuDelegateFactory.Options
//        is available on the compile classpath (LiteRT 1.0.1 classpath gap).
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL_ASSET = "all_minilm_l6_v2.tflite"
const val EMBEDDING_DIM = 384

/**
 * Wraps the all-MiniLM-L6-v2 TFLite model via LiteRT.
 *
 * The model was converted with mean-pooling and L2-normalisation baked in, so
 * [embed] returns a ready-to-use 384-dim float vector — no further processing needed.
 *
 * Thread safety: the underlying [Interpreter] is NOT thread-safe; always call
 * [embed] from the [DefaultDispatcher] coroutine context (enforced internally).
 *
 * Lifecycle: lazy initialisation on first [embed] call. Call [close] when the
 * owning scope is destroyed to release the TFLite model buffer.
 */
@Singleton
class EmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : Closeable {
    private val tokenizer: WordPieceTokenizer by lazy {
        WordPieceTokenizer.fromAssets(context)
    }

    private var _interpreter: Interpreter? = null
    private val interpreter: Interpreter
        get() = _interpreter ?: buildInterpreter().also { _interpreter = it }

    private fun buildInterpreter(): Interpreter {
        val modelBuffer = loadModelBuffer()
        val options = Interpreter.Options().apply {
            // Prefer GPU delegate; fall back to CPU if unavailable
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate())
            } else {
                setNumThreads(4)
            }
            compatList.close()
        }
        return Interpreter(modelBuffer, options)
    }

    private fun loadModelBuffer(): ByteBuffer {
        val fd = context.assets.openFd(MODEL_ASSET)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /**
     * Embeds [text] and returns a 384-dim L2-normalised float vector.
     * Suspends on [DefaultDispatcher] — safe to call from any coroutine scope.
     */
    suspend fun embed(text: String): FloatArray = withContext(dispatcher) {
        val ids  = tokenizer.tokenize(text)
        val mask = IntArray(WordPieceTokenizer.MAX_SEQ_LEN) { if (ids[it] != 0) 1 else 0 }
        val seqLen = WordPieceTokenizer.MAX_SEQ_LEN

        // LiteRT requires Long arrays for int64 model inputs
        val inputIds      = Array(1) { LongArray(seqLen) { ids[it].toLong() } }
        val attentionMask = Array(1) { LongArray(seqLen) { mask[it].toLong() } }
        val tokenTypeIds  = Array(1) { LongArray(seqLen) { 0L } }

        // Output: [1, 384] float32 (mean-pooled + L2-normalised by the model)
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }

        interpreter.runForMultipleInputsOutputs(
            arrayOf(inputIds, attentionMask, tokenTypeIds),
            mapOf(0 to output),
        )

        output[0]
    }

    /**
     * Releases the LiteRT interpreter from memory.
     * Safe to call multiple times. After calling [close], the interpreter will be
     * lazily re-initialised on the next [embed] call. This is intentional — the
     * nullable backing field pattern (rather than `by lazy`) allows clean release
     * and optional re-acquisition (e.g. after thermal recovery).
     */
    override fun close() {
        _interpreter?.close()
        _interpreter = null
    }
}
