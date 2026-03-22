package com.agendroid.core.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

private const val MODEL_ASSET = "models/kokoro/model.onnx"
private const val VOICES_ASSET = "models/kokoro/voices.bin"
private const val TOKENS_ASSET = "models/kokoro/tokens.txt"
private const val LEXICON_ASSET = "models/kokoro/lexicon-us-en.txt"
private const val DATA_DIR = "models/kokoro/espeak-ng-data"

const val KOKORO_SAMPLE_RATE = 24_000

class KokoroEngine(private val context: Context) : Closeable {

    private var tts: OfflineTts? = null

    fun isModelAvailable(): Boolean = listOf(MODEL_ASSET, VOICES_ASSET, TOKENS_ASSET).all { asset ->
        try {
            context.assets.open(asset).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (tts != null) return@withContext
        check(isModelAvailable()) {
            "Kokoro model assets missing. Run the setup steps in Plan 5 to download them."
        }
        val copiedDataDir = ensureDataDirCopied()

        val kokoroConfig = OfflineTtsKokoroModelConfig().apply {
            model = MODEL_ASSET
            voices = VOICES_ASSET
            tokens = TOKENS_ASSET
            dataDir = copiedDataDir
            lexicon = LEXICON_ASSET
            lang = "en-us"
            dictDir = copiedDataDir
            lengthScale = 1.0f
        }
        val modelConfig = OfflineTtsModelConfig().apply {
            kokoro = kokoroConfig
            numThreads = 2
            debug = false
            provider = "cpu"
        }
        val config = OfflineTtsConfig().apply {
            model = modelConfig
        }
        tts = OfflineTts(context.assets, config)
    }

    suspend fun synthesize(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f,
    ): FloatArray = withContext(Dispatchers.IO) {
        require(text.isNotEmpty()) { "Cannot synthesise empty text" }
        val engine = checkNotNull(tts) { "Call load() before synthesize()" }
        engine.generate(text, speakerId, speed).samples
    }

    override fun close() {
        tts?.release()
        tts = null
    }

    private fun ensureDataDirCopied(): String {
        val rootDir = context.getExternalFilesDir(null) ?: context.filesDir
        val targetDir = File(rootDir, DATA_DIR)
        copyAssetDirectoryIfNeeded(DATA_DIR, targetDir)
        return targetDir.absolutePath
    }

    private fun copyAssetDirectoryIfNeeded(assetPath: String, target: File) {
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            copyAssetFileIfNeeded(assetPath, target)
            return
        }

        if (!target.exists()) {
            target.mkdirs()
        }

        entries.forEach { child ->
            val childAssetPath = "$assetPath/$child"
            copyAssetDirectoryIfNeeded(childAssetPath, File(target, child))
        }
    }

    private fun copyAssetFileIfNeeded(assetPath: String, target: File) {
        if (target.exists() && target.length() > 0L) return

        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }
}
