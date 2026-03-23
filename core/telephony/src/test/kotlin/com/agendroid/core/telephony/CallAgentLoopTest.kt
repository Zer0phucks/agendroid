package com.agendroid.core.telephony

import com.agendroid.core.ai.AiServiceInterface
import com.agendroid.core.ai.ResourceState
import com.agendroid.core.data.dao.ConversationSummaryDao
import com.agendroid.core.data.entity.ConversationSummaryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallAgentLoopTest {

    @Test
    fun `disclosure prompt is always spoken first`() = runTest {
        val repository = CallSessionRepository()
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>()
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)

        coEvery { speechSynthesizer.synthesize(any()) } returns floatArrayOf(0.1f, 0.2f)

        val loop = CallAgentLoop(
            repository = repository,
            aiProvider = mockk(),
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
            transferPhraseMatcher = CallTransferPhraseMatcher(),
            summaryRecorder = CallSummaryRecorder(FakeConversationSummaryDao()),
        )

        repository.startSession("call-1", "+15551234567", CallAutonomyMode.FULL_AGENT)
        loop.start(checkNotNull(repository.activeSession.value))

        assertEquals(
            CallDisclosurePrompt.forOwner(),
            repository.activeSession.value?.transcript?.firstOrNull()?.text,
        )
        assertTrue(repository.activeSession.value?.isDisclosureDelivered == true)
        coVerify(exactly = 1) { speechSynthesizer.synthesize(CallDisclosurePrompt.forOwner()) }
        verify(exactly = 1) { audioBridge.playAssistantAudio(any()) }
    }

    @Test
    fun `caller saying human triggers handoff`() = runTest {
        val repository = CallSessionRepository()
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>()
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)

        coEvery { speechSynthesizer.synthesize(any()) } returns floatArrayOf(0.1f)

        val loop = CallAgentLoop(
            repository = repository,
            aiProvider = mockk(),
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
            transferPhraseMatcher = CallTransferPhraseMatcher(),
            summaryRecorder = CallSummaryRecorder(FakeConversationSummaryDao()),
        )

        repository.startSession("call-1", "+15551234567", CallAutonomyMode.FULL_AGENT)
        loop.start(checkNotNull(repository.activeSession.value))

        val result = loop.handleCallerTranscript(
            session = checkNotNull(repository.activeSession.value),
            transcript = "I want a human please",
        )

        assertEquals(CallAgentLoop.Result.TakeoverRequested, result)
        assertTrue(repository.activeSession.value?.isTakeoverRequested == true)
        verify { audioBridge.stopAssistantAudio() }
    }

    @Test
    fun `three consecutive empty stt turns triggers handoff`() = runTest {
        val repository = CallSessionRepository()
        val speechSynthesizer = mockk<TelephonyCoordinator.SpeechSynthesizer>()
        val audioBridge = mockk<CallAudioBridge>(relaxed = true)

        coEvery { speechSynthesizer.synthesize(any()) } returns floatArrayOf(0.1f)

        val loop = CallAgentLoop(
            repository = repository,
            aiProvider = mockk(),
            speechSynthesizer = speechSynthesizer,
            audioBridge = audioBridge,
            transferPhraseMatcher = CallTransferPhraseMatcher(),
            summaryRecorder = CallSummaryRecorder(FakeConversationSummaryDao()),
        )

        repository.startSession("call-1", "+15551234567", CallAutonomyMode.FULL_AGENT)
        loop.start(checkNotNull(repository.activeSession.value))

        assertEquals(CallAgentLoop.Result.NoReply, loop.handleCallerTranscript(checkNotNull(repository.activeSession.value), ""))
        assertEquals(CallAgentLoop.Result.NoReply, loop.handleCallerTranscript(checkNotNull(repository.activeSession.value), "   "))
        assertEquals(
            CallAgentLoop.Result.TakeoverRequested,
            loop.handleCallerTranscript(checkNotNull(repository.activeSession.value), ""),
        )
        assertEquals(3, loop.consecutiveEmptyTurns)
        assertTrue(repository.activeSession.value?.isTakeoverRequested == true)
    }

    @Test
    fun `post call summary is persisted`() = runTest {
        val repository = CallSessionRepository()
        val summaryDao = FakeConversationSummaryDao()
        val loop = CallAgentLoop(
            repository = repository,
            aiProvider = FakeAiProvider("Hello back"),
            speechSynthesizer = FakeSpeechSynthesizer(),
            audioBridge = mockk(relaxed = true),
            transferPhraseMatcher = CallTransferPhraseMatcher(),
            summaryRecorder = CallSummaryRecorder(summaryDao),
        )

        repository.startSession("call-1", "+15551234567", CallAutonomyMode.FULL_AGENT)
        loop.start(checkNotNull(repository.activeSession.value))
        loop.handleCallerTranscript(checkNotNull(repository.activeSession.value), "Need to reschedule")
        loop.finish(repository.activeSession.value)

        val summary = summaryDao.lastUpserted
        requireNotNull(summary)
        assertEquals("+15551234567", summary.contactKey)
        assertEquals("call", summary.type)
        assertTrue(summary.summary.contains("Need to reschedule"))
    }

    private class FakeSpeechSynthesizer : TelephonyCoordinator.SpeechSynthesizer {
        override suspend fun load() = Unit
        override suspend fun synthesize(text: String): FloatArray = floatArrayOf(0.1f)
    }

    private class FakeAiProvider(
        private val response: String,
    ) : TelephonyCoordinator.AiProvider {
        override suspend fun get(): AiServiceInterface = object : AiServiceInterface {
            override fun isModelAvailable(): Boolean = true
            override val resourceState: Flow<ResourceState> = emptyFlow()

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

    private class FakeConversationSummaryDao : ConversationSummaryDao {
        var lastUpserted: ConversationSummaryEntity? = null

        override suspend fun upsert(summary: ConversationSummaryEntity) {
            lastUpserted = summary
        }

        override fun getForContactKey(contactKey: String): Flow<List<ConversationSummaryEntity>> =
            flowOf(listOfNotNull(lastUpserted).filter { it.contactKey == contactKey })

        override suspend fun get(contactKey: String, type: String): ConversationSummaryEntity? =
            lastUpserted?.takeIf { it.contactKey == contactKey && it.type == type }
    }
}
