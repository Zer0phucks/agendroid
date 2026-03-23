package com.agendroid.core.telephony

import com.agendroid.core.ai.AiServiceInterface
import com.agendroid.core.ai.ResourceState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelephonyCoordinatorTest {

    @Test
    fun `screen only mode transcribes without generating ai reply`() = runTest {
        val repository = CallSessionRepository()
        val speechRecognizer = mockk<TelephonyCoordinator.SpeechRecognizer>()
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>(relaxed = true)
        val aiProvider = mockk<TelephonyCoordinator.AiProvider>()
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)

        coEvery { speechRecognizer.load() } returns Unit
        coEvery { speechRecognizer.transcribe(any()) } returns "Hello there"

        val coordinator = TelephonyCoordinator(
            repository = repository,
            aiProvider = aiProvider,
            speechRecognizer = speechRecognizer,
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
        )

        coordinator.startSession("call-1", "5551234", CallAutonomyMode.SCREEN_ONLY)
        val response = coordinator.handleCallerAudio(shortArrayOf(1, 2, 3))

        assertEquals(null, response)
        assertEquals(listOf("Hello there"), repository.activeSession.value?.transcript?.map { it.text })
        coVerify(exactly = 0) { aiProvider.get() }
        coVerify(exactly = 0) { speechSynthesizer.synthesize(any()) }
        verify(exactly = 0) { audioBridge.playAssistantAudio(any()) }
    }

    @Test
    fun `full agent mode transcribes generates and speaks reply`() = runTest {
        val repository = CallSessionRepository()
        val speechRecognizer = mockk<TelephonyCoordinator.SpeechRecognizer>()
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>()
        val aiProvider = mockk<TelephonyCoordinator.AiProvider>()
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)
        val aiService = fakeAiService("Hi back")

        coEvery { speechRecognizer.load() } returns Unit
        coEvery { speechRecognizer.transcribe(any()) } returns "Hello there"
        coEvery { speechSynthesizer.load() } returns Unit
        coEvery { speechSynthesizer.synthesize("Hi back") } returns floatArrayOf(0.1f, 0.2f)
        coEvery { aiProvider.get() } returns aiService

        val coordinator = TelephonyCoordinator(
            repository = repository,
            aiProvider = aiProvider,
            speechRecognizer = speechRecognizer,
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
        )

        coordinator.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)
        val response = coordinator.handleCallerAudio(shortArrayOf(1, 2, 3))

        assertEquals("Hi back", response)
        assertEquals(listOf("Hello there", "Hi back"), repository.activeSession.value?.transcript?.map { it.text })
        coVerify { aiProvider.get() }
        coVerify { speechSynthesizer.synthesize("Hi back") }
        verify { audioBridge.playAssistantAudio(floatArrayOf(0.1f, 0.2f)) }
    }

    @Test
    fun `request takeover stops ai speaking and marks session user controlled`() = runTest {
        val repository = CallSessionRepository()
        val coordinator = TelephonyCoordinator(
            repository = repository,
            aiProvider = mockk(),
            speechRecognizer = mockk(relaxed = true),
            speechSynthesizer = mockk(relaxed = true),
            audioBridge = mockk(relaxed = true),
        )

        coordinator.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)
        repository.setAiHandling(true)

        coordinator.requestTakeover()

        assertTrue(repository.activeSession.value?.isTakeoverRequested == true)
        assertFalse(repository.activeSession.value?.isAiHandling == true)
        verify { coordinator.audioBridge.stopAssistantAudio() }
    }

    @Test
    fun `empty stt result increments consecutive failure counter`() = runTest {
        val repository = CallSessionRepository()
        val speechRecognizer = mockk<TelephonyCoordinator.SpeechRecognizer>()

        coEvery { speechRecognizer.load() } returns Unit
        coEvery { speechRecognizer.transcribe(any()) } returns ""

        val coordinator = TelephonyCoordinator(
            repository = repository,
            aiProvider = mockk(),
            speechRecognizer = speechRecognizer,
            speechSynthesizer = mockk(relaxed = true),
            audioBridge = mockk(relaxed = true),
        )

        coordinator.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)
        coordinator.handleCallerAudio(shortArrayOf(1))

        assertEquals(1, coordinator.consecutiveEmptyTranscripts)
    }

    @Test
    fun `end session releases ai and voice resources`() = runTest {
        val repository = CallSessionRepository()
        val speechRecognizer = mockk<TelephonyCoordinator.SpeechRecognizer>(relaxed = true)
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>(relaxed = true)
        val aiProvider = mockk<TelephonyCoordinator.AiProvider>(relaxed = true)
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)

        val coordinator = TelephonyCoordinator(
            repository = repository,
            aiProvider = aiProvider,
            speechRecognizer = speechRecognizer,
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
        )

        coordinator.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)
        coordinator.endSession()

        assertEquals(null, repository.activeSession.value)
        verify { speechRecognizer.close() }
        verify { speechSynthesizer.close() }
        verify { aiProvider.unbind() }
        verify { audioBridge.stopAssistantAudio() }
        verify { audioBridge.close() }
    }

    @Test
    fun `full agent failure clears ai handling state`() = runTest {
        val repository = CallSessionRepository()
        val speechRecognizer = mockk<TelephonyCoordinator.SpeechRecognizer>()
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>()
        val aiProvider = mockk<TelephonyCoordinator.AiProvider>()
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)
        val aiService = object : AiServiceInterface {
            override fun isModelAvailable(): Boolean = true
            override val resourceState = emptyFlow<ResourceState>()

            override suspend fun generateResponse(
                userQuery: String,
                contactFilter: String?,
                conversationHistory: List<String>,
                onToken: (partial: String, done: Boolean) -> Unit,
            ): String = error("boom")
        }

        coEvery { speechRecognizer.load() } returns Unit
        coEvery { speechRecognizer.transcribe(any()) } returns "Hello there"
        coEvery { speechSynthesizer.load() } returns Unit
        coEvery { aiProvider.get() } returns aiService

        val coordinator = TelephonyCoordinator(
            repository = repository,
            aiProvider = aiProvider,
            speechRecognizer = speechRecognizer,
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
        )

        coordinator.startSession("call-1", "5551234", CallAutonomyMode.FULL_AGENT)

        val failure = try {
            coordinator.handleCallerAudio(shortArrayOf(1, 2, 3))
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertTrue(failure is IllegalStateException)
        assertFalse(repository.activeSession.value?.isAiHandling == true)
    }

    private fun fakeAiService(response: String): AiServiceInterface = object : AiServiceInterface {
        override fun isModelAvailable(): Boolean = true
        override val resourceState = emptyFlow<ResourceState>()

        override suspend fun generateResponse(
            userQuery: String,
            contactFilter: String?,
            conversationHistory: List<String>,
            onToken: (partial: String, done: Boolean) -> Unit,
        ): String {
            onToken(response, true)
            return response
        }
    }
}
