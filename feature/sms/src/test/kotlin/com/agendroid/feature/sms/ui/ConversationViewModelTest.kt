package com.agendroid.feature.sms.ui

import com.agendroid.core.common.Result
import com.agendroid.core.data.entity.PendingSmsReplyEntity
import com.agendroid.core.data.model.SmsMessage
import com.agendroid.core.data.model.SmsThread
import com.agendroid.core.data.repository.PendingSmsReplyRepository
import com.agendroid.core.data.repository.SmsThreadRepository
import com.agendroid.feature.sms.ui.conversation.ConversationViewModel
import com.agendroid.feature.sms.ui.threads.SmsThreadsViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

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
    fun `threads screen emits latest threads ordered by date`() = runTest(dispatcher.scheduler) {
        val repository = FakeSmsThreadRepository(
            threads = flowOf(
                listOf(
                    SmsThread(
                        threadId = 1L,
                        participantKey = "+15550000001",
                        snippet = "Older thread",
                        date = 100L,
                        unreadCount = 0,
                        subscriptionId = null,
                    ),
                    SmsThread(
                        threadId = 2L,
                        participantKey = "+15550000002",
                        snippet = "Newest thread",
                        date = 300L,
                        unreadCount = 2,
                        subscriptionId = null,
                    ),
                    SmsThread(
                        threadId = 3L,
                        participantKey = "+15550000003",
                        snippet = "Middle thread",
                        date = 200L,
                        unreadCount = 1,
                        subscriptionId = null,
                    ),
                ),
            ),
        )

        val viewModel = SmsThreadsViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(2L, 3L, 1L), viewModel.uiState.value.threads.map { it.threadId })
    }

    @Test
    fun `conversation screen sends reply via repository and refreshes messages`() = runTest(dispatcher.scheduler) {
        val repository = FakeSmsThreadRepository(
            threads = flowOf(emptyList()),
            messagesByThread = mutableMapOf(
                THREAD_ID to mutableListOf(
                    SmsMessage(
                        id = 1L,
                        threadId = THREAD_ID,
                        rawAddress = PARTICIPANT,
                        addressKey = PARTICIPANT,
                        body = "Can you call me back?",
                        date = 100L,
                        type = 1,
                        read = true,
                        subscriptionId = null,
                    ),
                ),
            ),
        )
        val pendingRepository = mockk<PendingSmsReplyRepository>()
        every { pendingRepository.pendingRepliesFlow } returns MutableStateFlow(
            listOf(
                PendingSmsReplyEntity(
                    id = 8L,
                    threadId = THREAD_ID,
                    sender = PARTICIPANT,
                    draftText = "AI draft reply",
                    scheduledSendAt = 0L,
                    status = "PENDING",
                    createdAt = 1234L,
                ),
            ),
        )

        val viewModel = ConversationViewModel(
            threadId = THREAD_ID,
            participantAddress = PARTICIPANT,
            subscriptionId = null,
            smsThreadRepository = repository,
            pendingSmsReplyRepository = pendingRepository,
        )
        advanceUntilIdle()

        viewModel.onDraftChanged("I can call you in 10 minutes.")
        viewModel.sendReply()
        advanceUntilIdle()

        assertEquals(
            listOf("Can you call me back?", "I can call you in 10 minutes."),
            viewModel.uiState.value.messages.map { it.body },
        )
        assertEquals(listOf(PARTICIPANT to "I can call you in 10 minutes."), repository.sentMessages)
        assertEquals("", viewModel.uiState.value.draftText)
        assertEquals("AI draft reply", viewModel.uiState.value.pendingDraft?.draftText)
    }

    private class FakeSmsThreadRepository(
        private val threads: Flow<List<SmsThread>>,
        private val messagesByThread: MutableMap<Long, MutableList<SmsMessage>> = mutableMapOf(),
    ) : SmsThreadRepository {
        val sentMessages = mutableListOf<Pair<String, String>>()

        override fun getThreads(): Flow<List<SmsThread>> = threads

        override suspend fun getMessages(threadId: Long, limit: Int): List<SmsMessage> {
            return messagesByThread[threadId].orEmpty().sortedByDescending { it.date }
        }

        override suspend fun sendSms(to: String, body: String, subscriptionId: Int?): Result<Unit> {
            sentMessages += to to body
            val threadMessages = messagesByThread.getOrPut(THREAD_ID) { mutableListOf() }
            threadMessages += SmsMessage(
                id = threadMessages.size.toLong() + 1L,
                threadId = THREAD_ID,
                rawAddress = to,
                addressKey = to,
                body = body,
                date = (threadMessages.maxOfOrNull { it.date } ?: 0L) + 100L,
                type = 2,
                read = true,
                subscriptionId = subscriptionId,
            )
            return Result.Success(Unit)
        }
    }

    private companion object {
        const val THREAD_ID = 42L
        const val PARTICIPANT = "+15551234567"
    }
}
