package com.agendroid.core.voice

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class WhisperEngineTest {

    private fun mockContextWithAssets(encoderPresent: Boolean): Context {
        val assetManager = mockk<AssetManager> {
            if (encoderPresent) {
                every { open("models/whisper/encoder.int8.onnx") } returns "x".byteInputStream()
                every { open("models/whisper/decoder.int8.onnx") } returns "x".byteInputStream()
                every { open("models/whisper/tokens.txt") } returns "x".byteInputStream()
            } else {
                every { open(any()) } throws FileNotFoundException()
            }
        }
        return mockk { every { assets } returns assetManager }
    }

    @Test
    fun `isModelAvailable returns false when assets missing`() {
        assertFalse(WhisperEngine(mockContextWithAssets(false)).isModelAvailable())
    }

    @Test
    fun `isModelAvailable returns true when all assets present`() {
        assertTrue(WhisperEngine(mockContextWithAssets(true)).isModelAvailable())
    }

    @Test
    fun `transcribe without load throws IllegalStateException`() = runTest {
        val engine = WhisperEngine(mockk())
        assertThrows<IllegalStateException> { engine.transcribe(ShortArray(16_000)) }
    }

    @Test
    fun `load without model throws IllegalStateException`() = runTest {
        val engine = WhisperEngine(mockContextWithAssets(false))
        assertThrows<IllegalStateException> { engine.load() }
    }

    @Test
    fun `close on unloaded engine does not throw`() {
        WhisperEngine(mockk()).close()
    }
}
