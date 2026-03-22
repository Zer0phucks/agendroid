package com.agendroid.core.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperEngineInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun engine_loadsSuccessfully_whenModelPresent() = runTest {
        val engine = WhisperEngine(context)
        assumeTrue("Whisper model not present — skipping load test", engine.isModelAvailable())
        engine.load()
        engine.close()
    }

    @Test
    fun engine_transcribesBlankAudio_withoutCrash() = runTest {
        val engine = WhisperEngine(context)
        assumeTrue("Whisper model not present — skipping transcribe test", engine.isModelAvailable())
        engine.load()
        val result = engine.transcribe(ShortArray(32_000))
        assertNotNull(result)
        engine.close()
    }
}
