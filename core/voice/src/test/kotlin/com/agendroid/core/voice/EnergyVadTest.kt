package com.agendroid.core.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.PI
import kotlin.math.sin

class EnergyVadTest {

    private val vad = EnergyVad()

    @Test
    fun `silence returns false`() {
        val silence = ShortArray(EnergyVad.FRAME_80MS) { 0 }
        assertFalse(vad.isSpeech(silence))
    }

    @Test
    fun `full-scale tone returns true`() {
        val tone = ShortArray(EnergyVad.FRAME_80MS) { i ->
            (Short.MAX_VALUE * sin(2 * PI * 1000 * i / 16000.0)).toInt().toShort()
        }
        assertTrue(vad.isSpeech(tone))
    }

    @Test
    fun `below-threshold noise returns false`() {
        val noise = ShortArray(EnergyVad.FRAME_80MS) { ((Math.random() * 200) - 100).toInt().toShort() }
        assertFalse(vad.isSpeech(noise))
    }

    @Test
    fun `empty samples returns false`() {
        assertFalse(vad.isSpeech(ShortArray(0)))
    }

    @Test
    fun `high custom threshold rejects quiet signal`() {
        val strictVad = EnergyVad(threshold = 0.5f)
        val quiet = ShortArray(EnergyVad.FRAME_80MS) { 300 }
        assertFalse(strictVad.isSpeech(quiet))
    }

    @Test
    fun `zero threshold always detects non-silent signal`() {
        val sensitiveVad = EnergyVad(threshold = 0.0f)
        val veryQuiet = ShortArray(EnergyVad.FRAME_80MS) { 1 }
        assertTrue(sensitiveVad.isSpeech(veryQuiet))
    }

    @Test
    fun `threshold of 1 never detects below max amplitude`() {
        val neverVad = EnergyVad(threshold = 1.0f)
        val almostMax = ShortArray(EnergyVad.FRAME_80MS) { (Short.MAX_VALUE - 1).toShort() }
        assertFalse(neverVad.isSpeech(almostMax))
    }

    @Test
    fun `negative threshold throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { EnergyVad(-0.01f) }
    }

    @Test
    fun `threshold above 1 throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { EnergyVad(1.01f) }
    }

    @Test
    fun `frame size constants match 16 kHz`() {
        assertEquals(1280, EnergyVad.FRAME_80MS)
        assertEquals(2560, EnergyVad.FRAME_160MS)
        assertEquals(5120, EnergyVad.FRAME_320MS)
    }

    @Test
    fun `default threshold is 0_02`() {
        assertEquals(0.02f, EnergyVad.DEFAULT_THRESHOLD)
    }
}
