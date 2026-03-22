package com.agendroid.core.voice

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class KokoroEngineTest {

    private fun mockContextNoAssets(): Context {
        val am = mockk<AssetManager> { every { open(any()) } throws FileNotFoundException() }
        return mockk { every { assets } returns am }
    }

    @Test
    fun `isModelAvailable returns false when assets missing`() {
        assertFalse(KokoroEngine(mockContextNoAssets()).isModelAvailable())
    }

    @Test
    fun `synthesize without load throws IllegalStateException`() = runTest {
        assertThrows<IllegalStateException> { KokoroEngine(mockContextNoAssets()).synthesize("hi") }
    }

    @Test
    fun `synthesize empty text throws IllegalArgumentException`() = runTest {
        val engine = KokoroEngine(mockContextNoAssets())
        assertThrows<IllegalArgumentException> { engine.synthesize("") }
    }

    @Test
    fun `close on unloaded engine does not throw`() {
        KokoroEngine(mockk()).close()
    }
}
