package com.agendroid.feature.sms.autonomy

import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.agendroid.core.ai.AiServiceClient
import com.agendroid.core.data.dao.ContactPreferenceDao
import com.agendroid.core.data.entity.PendingSmsReplyEntity
import com.agendroid.core.data.repository.AppSettingsRepository
import com.agendroid.core.data.repository.PendingSmsReplyRepository
import com.agendroid.core.data.repository.SmsThreadRepository
import com.agendroid.core.data.util.PhoneNumberNormalizer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Lightweight interface over [WorkManager.enqueue] so that [IncomingSmsProcessor] can be
 * tested without a real Android context or WorkManager singleton.
 */
fun interface WorkEnqueuer {
    fun enqueue(request: OneTimeWorkRequest)
}

/**
 * Orchestrates the full incoming-SMS autonomy pipeline:
 *
 * 1. Normalize the sender's phone number.
 * 2. Look up any per-contact SMS-autonomy override.
 * 3. Read the global SMS-autonomy mode from [AppSettingsRepository].
 * 4. Ask the AI service for a draft reply.
 * 5. Run [SmsAutonomyPolicy.decide] to obtain a concrete [SmsAutonomyDecision].
 * 6. Persist the draft and/or schedule [SmsAutonomyWorker] via [WorkEnqueuer] if AUTO.
 */
class IncomingSmsProcessor @Inject constructor(
    private val smsThreadRepository: SmsThreadRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val pendingSmsReplyRepository: PendingSmsReplyRepository,
    private val aiServiceClient: AiServiceClient,
    private val autonomyPolicy: SmsAutonomyPolicy,
    private val contactPreferenceDao: ContactPreferenceDao,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val workEnqueuer: WorkEnqueuer,
) {

    /**
     * Processes an incoming SMS from [sender] with the given [body], associated with [threadId].
     *
     * Must be called from a coroutine; safe on [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun process(sender: String, body: String, threadId: Long) {
        // 1. Normalize sender address for consistent lookup keys.
        val normalizedSender = phoneNumberNormalizer.normalize(sender)

        // 2. Check for a per-contact override.
        val contactPref = contactPreferenceDao.get(normalizedSender)
        val contactOverride: SmsAutonomyMode? = contactPref?.smsAutonomy?.let { raw ->
            runCatching { SmsAutonomyMode.valueOf(raw.uppercase()) }.getOrNull()
        }

        // 3. Global SMS autonomy mode.
        val settings = appSettingsRepository.settingsFlow.first()
        val globalModeRaw = settings?.smsAutonomyMode ?: "SEMI"
        val globalMode = runCatching { SmsAutonomyMode.valueOf(globalModeRaw) }
            .getOrDefault(SmsAutonomyMode.SEMI)

        // 4. Ask AI for a draft reply — use recent message history as context.
        val recentMessages = smsThreadRepository.getMessages(threadId, limit = 10)
        val historyLines = recentMessages
            .sortedBy { it.date }
            .joinToString("\n") { msg ->
                val role = if (msg.type == android.provider.Telephony.Sms.MESSAGE_TYPE_SENT) "Me" else sender
                "$role: ${msg.body}"
            }
        val prompt = buildString {
            if (historyLines.isNotBlank()) {
                append("Recent conversation:\n")
                append(historyLines)
                append("\n\n")
            }
            append("New message from $sender: $body\n\n")
            append("Please write a brief, natural reply to this SMS message.")
        }

        val aiService = aiServiceClient.getService()
        val draftText = aiService.generateResponse(
            userQuery = prompt,
            contactFilter = normalizedSender,
        )

        // 5. Determine what to do.
        val decision = autonomyPolicy.decide(globalMode, contactOverride)

        // 6. Act on the decision.
        if (!decision.shouldPersistDraft) {
            // MANUAL mode — nothing to store or schedule; notification handled elsewhere.
            return
        }

        val now = System.currentTimeMillis()
        val scheduledSendAt = if (decision.shouldScheduleSend) {
            now + SmsAutonomyWorker.CANCEL_WINDOW_MS
        } else {
            0L // SEMI mode — requires explicit approval
        }

        val entity = PendingSmsReplyEntity(
            threadId = threadId,
            sender = normalizedSender,
            draftText = draftText,
            scheduledSendAt = scheduledSendAt,
            status = "PENDING",
            createdAt = now,
        )
        val replyId = pendingSmsReplyRepository.insert(entity)

        if (decision.shouldScheduleSend) {
            val inputData = Data.Builder()
                .putLong(SmsAutonomyWorker.KEY_PENDING_REPLY_ID, replyId)
                .putLong(SmsAutonomyWorker.KEY_THREAD_ID, threadId)
                .putString(SmsAutonomyWorker.KEY_SENDER, normalizedSender)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SmsAutonomyWorker>()
                .setInitialDelay(SmsAutonomyWorker.CANCEL_WINDOW_MS, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .build()

            workEnqueuer.enqueue(workRequest)
        }
    }
}
