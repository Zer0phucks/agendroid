package com.agendroid.core.telephony

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallSessionRepositoryTest {

    @Test
    fun `starting a session exposes caller number and mode`() {
        val repository = CallSessionRepository()

        repository.startSession(
            callId = "call-1",
            number = "5551234",
            mode = CallAutonomyMode.SCREEN_ONLY,
        )

        val session = repository.activeSession.value
        requireNotNull(session)
        assertEquals("call-1", session.callId)
        assertEquals("5551234", session.number)
        assertEquals(CallAutonomyMode.SCREEN_ONLY, session.mode)
    }

    @Test
    fun `appending transcript lines preserves order`() {
        val repository = CallSessionRepository()
        repository.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)

        repository.appendTranscript(
            CallTranscriptLine(
                speaker = CallTranscriptLine.Speaker.CALLER,
                text = "Hello",
                timestampMs = 1L,
            ),
        )
        repository.appendTranscript(
            CallTranscriptLine(
                speaker = CallTranscriptLine.Speaker.ASSISTANT,
                text = "Hi there",
                timestampMs = 2L,
            ),
        )

        val transcript = requireNotNull(repository.activeSession.value).transcript
        assertEquals(listOf("Hello", "Hi there"), transcript.map { it.text })
    }

    @Test
    fun `requestTakeover flips session to user controlled`() {
        val repository = CallSessionRepository()
        repository.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)
        repository.setAiHandling(true)

        repository.requestTakeover()

        val session = requireNotNull(repository.activeSession.value)
        assertTrue(session.isTakeoverRequested)
        assertFalse(session.isAiHandling)
    }

    @Test
    fun `clearing session resets state to idle`() {
        val repository = CallSessionRepository()
        repository.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)

        repository.clearSession()

        assertNull(repository.activeSession.value)
    }
}
