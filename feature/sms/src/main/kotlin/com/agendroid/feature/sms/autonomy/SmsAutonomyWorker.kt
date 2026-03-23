package com.agendroid.feature.sms.autonomy

import android.content.Context
import android.telephony.SmsManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agendroid.core.data.repository.PendingSmsReplyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker that fires after [CANCEL_WINDOW_MS] to automatically send
 * an AI-drafted SMS reply.
 *
 * Before sending, it re-checks that the reply is still in "PENDING" status —
 * a user cancel action will have updated the status to "CANCELLED", preventing send.
 */
@HiltWorker
class SmsAutonomyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingSmsReplyRepository: PendingSmsReplyRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val replyId = inputData.getLong(KEY_PENDING_REPLY_ID, -1L)
        val sender  = inputData.getString(KEY_SENDER) ?: return Result.failure()

        if (replyId < 0L) return Result.failure()

        // Load the entity from the pending-replies flow (filter by id).
        val pendingList = pendingSmsReplyRepository.pendingRepliesFlow.first()
        val entity = pendingList.firstOrNull { it.id == replyId }
            ?: return Result.failure() // Already sent or cancelled.

        if (entity.status != "PENDING") {
            // User cancelled or another process already handled it.
            return Result.success()
        }

        return try {
            val smsManager = applicationContext.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(sender, null, entity.draftText, null, null)
            pendingSmsReplyRepository.updateStatus(replyId, "SENT")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SmsAutonomyWorker", "Failed to send SMS for reply $replyId", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_PENDING_REPLY_ID = "pending_reply_id"
        const val KEY_THREAD_ID        = "thread_id"
        const val KEY_SENDER           = "sender"

        /** Users have this window to cancel an AUTO reply before it is sent. */
        const val CANCEL_WINDOW_MS = 10_000L
    }
}
