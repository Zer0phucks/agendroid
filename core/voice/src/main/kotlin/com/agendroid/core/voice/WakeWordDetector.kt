package com.agendroid.core.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

private const val KWS_ENCODER_ASSET = "models/kws/encoder.int8.onnx"
private const val KWS_DECODER_ASSET = "models/kws/decoder.int8.onnx"
private const val KWS_JOINER_ASSET = "models/kws/joiner.int8.onnx"
private const val KWS_TOKENS_ASSET = "models/kws/tokens.txt"
private const val KEYWORDS_ASSET = "models/kws/keywords.txt"

class WakeWordDetector(
    private val context: Context,
    private val audioCapture: AudioCapture = AudioCapture(frameSize = EnergyVad.FRAME_80MS),
    private val vad: EnergyVad = EnergyVad(threshold = 0.005f),
) : Closeable {

    private var spotter: KeywordSpotter? = null
    private var kwsStream: OnlineStream? = null

    val isRunning: Boolean get() = audioCapture.isRunning

    fun isModelAvailable(): Boolean =
        listOf(KWS_ENCODER_ASSET, KWS_DECODER_ASSET, KWS_JOINER_ASSET, KWS_TOKENS_ASSET, KEYWORDS_ASSET).all { asset ->
            try {
                context.assets.open(asset).close()
                true
            } catch (_: Exception) {
                false
            }
        }

    fun load() {
        if (spotter != null) return
        check(isModelAvailable()) {
            "KWS model assets missing. Run the setup steps in Plan 5 to download them."
        }

        val transducerConfig = OnlineTransducerModelConfig().apply {
            encoder = KWS_ENCODER_ASSET
            decoder = KWS_DECODER_ASSET
            joiner = KWS_JOINER_ASSET
        }
        val modelConfig = OnlineModelConfig().apply {
            transducer = transducerConfig
            tokens = KWS_TOKENS_ASSET
            numThreads = 1
            debug = false
            provider = "cpu"
        }
        val config = KeywordSpotterConfig().apply {
            featConfig = FeatureConfig(sampleRate = AudioCapture.SAMPLE_RATE_HZ, featureDim = 80)
            this.modelConfig = modelConfig
            maxActivePaths = 4
            keywordsFile = KEYWORDS_ASSET
            keywordsScore = 1.0f
            keywordsThreshold = 0.25f
            numTrailingBlanks = 1
        }
        spotter = KeywordSpotter(context.assets, config)
    }

    fun start(scope: CoroutineScope, onDetected: () -> Unit) {
        val keywordSpotter = checkNotNull(spotter) { "Call load() before start()" }
        check(!isRunning) { "Already running. Call stop() first." }

        val stream = keywordSpotter.createStream()
        kwsStream = stream
        var cooldown = 0

        audioCapture.start(scope) frame@{ pcm ->
            if (cooldown > 0) {
                cooldown--
                return@frame
            }
            if (!vad.isSpeech(pcm)) return@frame

            val samples = FloatArray(pcm.size) { index -> pcm[index] / Short.MAX_VALUE.toFloat() }
            stream.acceptWaveform(samples, AudioCapture.SAMPLE_RATE_HZ)
            if (!keywordSpotter.isReady(stream)) return@frame

            keywordSpotter.decode(stream)
            val result = keywordSpotter.getResult(stream)
            if (result.keyword.isNotEmpty()) {
                cooldown = COOLDOWN_FRAMES
                keywordSpotter.reset(stream)
                onDetected()
            }
        }
    }

    fun stop() {
        audioCapture.stop()
        kwsStream?.release()
        kwsStream = null
    }

    override fun close() {
        stop()
        audioCapture.close()
        spotter?.release()
        spotter = null
    }

    companion object {
        private const val COOLDOWN_FRAMES = 25
    }
}
