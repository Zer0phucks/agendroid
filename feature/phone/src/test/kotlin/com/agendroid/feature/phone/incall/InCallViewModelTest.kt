package com.agendroid.feature.phone.incall

import com.agendroid.core.data.model.CallLogEntry
import com.agendroid.core.data.repository.CallLogRepository
import com.agendroid.core.telephony.CallAutonomyMode
import com.agendroid.core.telephony.CallSessionRepository
import com.agendroid.core.telephony.CallTranscriptLine
import com.agendroid.core.telephony.TelephonyCoordinator
import com.agendroid.feature.phone.calllog.CallLogViewModel
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InCallViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `call log state loads from repository`() = runTest(dispatcher.scheduler) {
        val repository = FakeCallLogRepository(
            entries = flowOf(
                listOf(
                    CallLogEntry(
                        id = 1L,
                        rawNumber = "+15550000001",
                        numberKey = "+15550000001",
                        name = "Older",
                        date = 100L,
                        duration = 30L,
                        callType = 1,
                    ),
                    CallLogEntry(
                        id = 2L,
                        rawNumber = "+15550000002",
                        numberKey = "+15550000002",
                        name = "Newest",
                        date = 200L,
                        duration = 45L,
                        callType = 3,
                    ),
                ),
            ),
        )

        val viewModel = CallLogViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(2L, 1L), viewModel.uiState.value.entries.map { it.id })
    }

    @Test
    fun `active call transcript maps from call session repository`() = runTest(dispatcher.scheduler) {
        val sessionRepository = CallSessionRepository()
        val coordinator = mockk<TelephonyCoordinator>(relaxed = true)
        val viewModel = InCallViewModel(sessionRepository, coordinator)

        sessionRepository.startSession(
            callId = "call-1",
            number = "+15551234567",
            mode = CallAutonomyMode.SCREEN_ONLY,
        )
        sessionRepository.markDisclosureDelivered()
        sessionRepository.appendTranscript(
            CallTranscriptLine(
                speaker = CallTranscriptLine.Speaker.CALLER,
                text = "Hi, is this a good time?",
                timestampMs = 123L,
            ),
        )
        sessionRepository.setAiHandling(true)
        advanceUntilIdle()

        assertEquals("+15551234567", viewModel.uiState.value.number)
        assertEquals(CallAutonomyMode.SCREEN_ONLY, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.disclosureDelivered)
        assertTrue(viewModel.uiState.value.isAssistantSpeaking)
        assertEquals(
            listOf("Hi, is this a good time?"),
            viewModel.uiState.value.transcript.map { it.text },
        )
    }

    @Test
    fun `take over button calls telephony coordinator requestTakeover`() = runTest(dispatcher.scheduler) {
        val sessionRepository = CallSessionRepository()
        val coordinator = mockk<TelephonyCoordinator>(relaxed = true)
        val viewModel = InCallViewModel(sessionRepository, coordinator)

        viewModel.requestTakeover()

        verify(exactly = 1) { coordinator.requestTakeover() }
    }

    private class FakeCallLogRepository(
        private val entries: Flow<List<CallLogEntry>>,
    ) : CallLogRepository {
        override fun getCallLog(limit: Int): Flow<List<CallLogEntry>> = entries

        override suspend fun getCallsFromNumber(phoneNumber: String, limit: Int): List<CallLogEntry> =
            emptyList()
    }
}
