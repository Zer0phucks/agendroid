package com.agendroid.feature.sms.autonomy

import androidx.work.OneTimeWorkRequest
import com.agendroid.core.ai.AiServiceClient
import com.agendroid.core.ai.AiServiceInterface
import com.agendroid.core.data.dao.ContactPreferenceDao
import com.agendroid.core.data.entity.AppSettingsEntity
import com.agendroid.core.data.entity.PendingSmsReplyEntity
import com.agendroid.core.data.model.SmsMessage
import com.agendroid.core.data.repository.AppSettingsRepository
import com.agendroid.core.data.repository.PendingSmsReplyRepository
import com.agendroid.core.data.repository.SmsThreadRepository
import com.agendroid.core.data.util.PhoneNumberNormalizer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IncomingSmsProcessorTest {

    // -----------------------------------------------------------------------
    // Mocks
    // -----------------------------------------------------------------------
    private val smsThreadRepository: SmsThreadRepository = mockk()
    private val appSettingsRepository: AppSettingsRepository = mockk()
    private val pendingSmsReplyRepository: PendingSmsReplyRepository = mockk()
    private val aiServiceClient: AiServiceClient = mockk()
    private val aiService: AiServiceInterface = mockk()
    private val autonomyPolicy: SmsAutonomyPolicy = mockk()
    private val contactPreferenceDao: ContactPreferenceDao = mockk()
    private val phoneNumberNormalizer: PhoneNumberNormalizer = mockk()

    private val enqueuedRequests = mutableListOf<OneTimeWorkRequest>()
    private val workEnqueuer: WorkEnqueuer = WorkEnqueuer { req ->
        enqueuedRequests.add(req)
    }

    private lateinit var processor: IncomingSmsProcessor

    // -----------------------------------------------------------------------
    // Common test fixtures
    // -----------------------------------------------------------------------
    private val sender   = "+15551234567"
    private val body     = "Hey, are you free tomorrow?"
    private val threadId = 42L
    private val draft    = "Sure, I'm free at 3pm!"
    private val insertedId = 99L

    private val autoSettings = AppSettingsEntity(
        id = 1,
        smsAutonomyMode = "AUTO",
        callAutonomyMode = "SCREEN_ONLY",
        assistantEnabled = true,
        selectedModel = "gemma3",
    )
    private val semiSettings = autoSettings.copy(smsAutonomyMode = "SEMI")
    private val manualSettings = autoSettings.copy(smsAutonomyMode = "MANUAL")

    @BeforeEach
    fun setUp() {
        // Default: normalizer returns the sender as-is.
        every { phoneNumberNormalizer.normalize(any()) } answers { firstArg() }

        // Default: no contact override.
        coEvery { contactPreferenceDao.get(any()) } returns null

        // Default: no thread history.
        coEvery { smsThreadRepository.getMessages(any(), any()) } returns emptyList()

        // Default: AI returns a draft.
        coEvery { aiServiceClient.getService() } returns aiService
        coEvery { aiService.generateResponse(any(), any(), any(), any()) } returns draft

        // Default: insert returns a stable id.
        coEvery { pendingSmsReplyRepository.insert(any()) } returns insertedId

        processor = IncomingSmsProcessor(
            smsThreadRepository      = smsThreadRepository,
            appSettingsRepository    = appSettingsRepository,
            pendingSmsReplyRepository = pendingSmsReplyRepository,
            aiServiceClient          = aiServiceClient,
            autonomyPolicy           = autonomyPolicy,
            contactPreferenceDao     = contactPreferenceDao,
            phoneNumberNormalizer    = phoneNumberNormalizer,
            workEnqueuer             = workEnqueuer,
        )
    }

    // -----------------------------------------------------------------------
    // AUTO mode
    // -----------------------------------------------------------------------

    @Test
    fun `AUTO mode - draft persisted and WorkManager enqueued`() = runTest {
        every { appSettingsRepository.settingsFlow } returns flowOf(autoSettings)
        every { autonomyPolicy.decide(SmsAutonomyMode.AUTO, null) } returns SmsAutonomyDecision(
            mode = SmsAutonomyMode.AUTO,
            shouldScheduleSend = true,
            shouldPersistDraft = true,
            shouldNotify = true,
        )

        processor.process(sender, body, threadId)

        // Draft must have been persisted.
        coVerify(exactly = 1) { pendingSmsReplyRepository.insert(any()) }

        // WorkManager must have been given exactly one request.
        assert(enqueuedRequests.size == 1) {
            "Expected 1 enqueued WorkRequest but was ${enqueuedRequests.size}"
        }
    }

    @Test
    fun `AUTO mode - persisted entity has correct sender and draft text`() = runTest {
        every { appSettingsRepository.settingsFlow } returns flowOf(autoSettings)
        every { autonomyPolicy.decide(SmsAutonomyMode.AUTO, null) } returns SmsAutonomyDecision(
            mode = SmsAutonomyMode.AUTO,
            shouldScheduleSend = true,
            shouldPersistDraft = true,
            shouldNotify = true,
        )

        val entitySlot = slot<PendingSmsReplyEntity>()
        coEvery { pendingSmsReplyRepository.insert(capture(entitySlot)) } returns insertedId

        processor.process(sender, body, threadId)

        val entity = entitySlot.captured
        assert(entity.sender == sender)
        assert(entity.draftText == draft)
        assert(entity.status == "PENDING")
        assert(entity.scheduledSendAt > 0L)
    }

    // -----------------------------------------------------------------------
    // SEMI mode
    // -----------------------------------------------------------------------

    @Test
    fun `SEMI mode - draft persisted, NO WorkManager request enqueued`() = runTest {
        every { appSettingsRepository.settingsFlow } returns flowOf(semiSettings)
        every { autonomyPolicy.decide(SmsAutonomyMode.SEMI, null) } returns SmsAutonomyDecision(
            mode = SmsAutonomyMode.SEMI,
            shouldScheduleSend = false,
            shouldPersistDraft = true,
            shouldNotify = true,
        )

        processor.process(sender, body, threadId)

        // Draft persisted.
        coVerify(exactly = 1) { pendingSmsReplyRepository.insert(any()) }

        // NO work enqueued.
        assert(enqueuedRequests.isEmpty()) {
            "Expected 0 enqueued WorkRequests for SEMI but was ${enqueuedRequests.size}"
        }
    }

    @Test
    fun `SEMI mode - persisted entity has scheduledSendAt of 0`() = runTest {
        every { appSettingsRepository.settingsFlow } returns flowOf(semiSettings)
        every { autonomyPolicy.decide(SmsAutonomyMode.SEMI, null) } returns SmsAutonomyDecision(
            mode = SmsAutonomyMode.SEMI,
            shouldScheduleSend = false,
            shouldPersistDraft = true,
            shouldNotify = true,
        )

        val entitySlot = slot<PendingSmsReplyEntity>()
        coEvery { pendingSmsReplyRepository.insert(capture(entitySlot)) } returns insertedId

        processor.process(sender, body, threadId)

        assert(entitySlot.captured.scheduledSendAt == 0L) {
            "SEMI mode should set scheduledSendAt=0 but was ${entitySlot.captured.scheduledSendAt}"
        }
    }

    // -----------------------------------------------------------------------
    // MANUAL mode
    // -----------------------------------------------------------------------

    @Test
    fun `MANUAL mode - no draft persisted and no WorkManager request enqueued`() = runTest {
        every { appSettingsRepository.settingsFlow } returns flowOf(manualSettings)
        every { autonomyPolicy.decide(SmsAutonomyMode.MANUAL, null) } returns SmsAutonomyDecision(
            mode = SmsAutonomyMode.MANUAL,
            shouldScheduleSend = false,
            shouldPersistDraft = false,
            shouldNotify = true,
        )

        processor.process(sender, body, threadId)

        // No draft persisted.
        coVerify(exactly = 0) { pendingSmsReplyRepository.insert(any()) }

        // No work enqueued.
        assert(enqueuedRequests.isEmpty()) {
            "Expected 0 enqueued WorkRequests for MANUAL but was ${enqueuedRequests.size}"
        }
    }
}
