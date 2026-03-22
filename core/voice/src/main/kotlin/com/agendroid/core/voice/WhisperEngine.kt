package com.agendroid.core.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

private const val ENCODER_ASSET = "models/whisper/encoder.int8.onnx"
private const val DECODER_ASSET = "models/whisper/decoder.int8.onnx"
private const val TOKENS_ASSET = "models/whisper/tokens.txt"

class WhisperEngine(private val context: Context) : Closeable {

    private var recognizer: OfflineRecognizer? = null

    fun isModelAvailable(): Boolean = listOf(ENCODER_ASSET, DECODER_ASSET, TOKENS_ASSET).all { asset ->
        try {
            context.assets.open(asset).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (recognizer != null) return@withContext
        check(isModelAvailable()) {
            "Whisper model assets missing. Run the setup steps in Plan 5 to download them."
        }

        val whisperConfig = OfflineWhisperModelConfig().apply {
            encoder = ENCODER_ASSET
            decoder = DECODER_ASSET
            language = "en"
            task = "transcribe"
            tailPaddings = -1
        }
        val offlineModelConfig = OfflineModelConfig().apply {
            whisper = whisperConfig
            tokens = TOKENS_ASSET
            numThreads = 2
            debug = false
            provider = "cpu"
        }
        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig(sampleRate = AudioCapture.SAMPLE_RATE_HZ, featureDim = 80)
            modelConfig = offlineModelConfig
            decodingMethod = "greedy_search"
        }
        recognizer = OfflineRecognizer(context.assets, config)
    }

    suspend fun transcribe(pcm: ShortArray): String = withContext(Dispatchers.IO) {
        val engine = checkNotNull(recognizer) { "Call load() before transcribe()" }
        val samples = FloatArray(pcm.size) { index -> pcm[index] / Short.MAX_VALUE.toFloat() }
        val stream = engine.createStream()
        try {
            stream.acceptWaveform(samples, AudioCapture.SAMPLE_RATE_HZ)
            engine.decode(stream)
            engine.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer?.release()
        recognizer = null
    }
}
