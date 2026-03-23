package com.agendroid.feature.sms.autonomy

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that wraps [IncomingSmsProcessor] so that the
 * [com.agendroid.feature.sms.SmsReceiver] BroadcastReceiver can hand off
 * processing to an off-main-thread, Hilt-injectable context.
 *
 * Input data keys: [KEY_SENDER], [KEY_BODY], [KEY_THREAD_ID].
 */
@HiltWorker
class SmsProcessorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: IncomingSmsProcessor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sender   = inputData.getString(KEY_SENDER)   ?: return Result.failure()
        val body     = inputData.getString(KEY_BODY)     ?: return Result.failure()
        val threadId = inputData.getLong(KEY_THREAD_ID, 0L)

        return try {
            processor.process(sender, body, threadId)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SmsProcessorWorker", "Failed to process incoming SMS", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_SENDER    = "sender"
        const val KEY_BODY      = "body"
        const val KEY_THREAD_ID = "thread_id"
    }
}
