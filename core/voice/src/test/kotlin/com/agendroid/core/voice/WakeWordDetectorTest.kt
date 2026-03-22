package com.agendroid.core.voice

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class WakeWordDetectorTest {

    private fun mockContextNoAssets(): Context {
        val am = mockk<AssetManager> { every { open(any()) } throws FileNotFoundException() }
        return mockk { every { assets } returns am }
    }

    @Test
    fun `isModelAvailable returns false when assets missing`() {
        assertFalse(WakeWordDetector(mockContextNoAssets()).isModelAvailable())
    }

    @Test
    fun `start without load throws IllegalStateException`() {
        val detector = WakeWordDetector(mockContextNoAssets())
        assertThrows<IllegalStateException> {
            detector.start(TestScope()) {}
        }
    }

    @Test
    fun `load without model throws IllegalStateException`() {
        assertThrows<IllegalStateException> { WakeWordDetector(mockContextNoAssets()).load() }
    }

    @Test
    fun `close on unloaded detector does not throw`() {
        WakeWordDetector(mockContextNoAssets()).close()
    }

    @Test
    fun `stop on unstarted detector does not throw`() {
        WakeWordDetector(mockContextNoAssets()).stop()
    }

    @Test
    fun `isRunning is false before start`() {
        assertFalse(WakeWordDetector(mockContextNoAssets()).isRunning)
    }
}
