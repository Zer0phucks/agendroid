package com.agendroid.core.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KokoroEngineInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun engine_loadsSuccessfully_whenModelPresent() = runTest {
        val engine = KokoroEngine(context)
        assumeTrue("Kokoro model not present — skipping", engine.isModelAvailable())
        engine.load()
        engine.close()
    }

    @Test
    fun engine_synthesizesShortText_returnsNonEmptyPcm() = runTest {
        val engine = KokoroEngine(context)
        assumeTrue("Kokoro model not present — skipping", engine.isModelAvailable())
        engine.load()
        val pcm = engine.synthesize("Hello.")
        assertTrue("Expected non-empty PCM output", pcm.isNotEmpty())
        engine.close()
    }
}
