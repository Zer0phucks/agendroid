package com.agendroid.spike.callpipeline.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatencyRecorderTest {

    @Test
    fun `single turn records correct total latency`() {
        val recorder = LatencyRecorder()
        val turn = recorder.startTurn()
        turn.markStage("vad", 80L)
        turn.markStage("stt", 420L)
        turn.markStage("llm_first_token", 480L)
        turn.markStage("llm_full", 550L)
        turn.markStage("tts", 190L)
        turn.end()

        val turns = recorder.turns()
        assertEquals(1, turns.size)
        // Total = sum of all stage durations
        assertEquals(1720L, turns[0].totalMs)
    }

    @Test
    fun `p95 of 20 values returns 95th percentile`() {
        val recorder = LatencyRecorder()
        // Add 20 turns with known total latencies: 100, 200, ..., 2000 ms
        repeat(20) { i ->
            val turn = recorder.startTurn()
            turn.markStage("total", ((i + 1) * 100).toLong())
            turn.end()
        }
        // p95 of [100, 200, ..., 2000] = value at index 18 (0-indexed) = 1900
        assertEquals(1900L, recorder.p95TotalMs())
    }

    @Test
    fun `stage breakdown is preserved per turn`() {
        val recorder = LatencyRecorder()
        val turn = recorder.startTurn()
        turn.markStage("stt", 350L)
        turn.markStage("llm", 600L)
        turn.end()

        val stages = recorder.turns()[0].stages
        assertEquals(350L, stages["stt"])
        assertEquals(600L, stages["llm"])
    }

    @Test
    fun `empty recorder p95 returns zero`() {
        assertEquals(0L, LatencyRecorder().p95TotalMs())
    }

    @Test
    fun `all turns within budget returns true when p95 under threshold`() {
        val recorder = LatencyRecorder()
        repeat(20) { i ->
            val turn = recorder.startTurn()
            turn.markStage("pipeline", ((i + 1) * 80).toLong()) // max = 1600ms
            turn.end()
        }
        assertTrue(recorder.p95TotalMs() <= 2000L)
    }
}
