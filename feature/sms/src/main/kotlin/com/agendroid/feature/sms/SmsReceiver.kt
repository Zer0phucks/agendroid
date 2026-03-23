package com.agendroid.feature.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.agendroid.feature.sms.autonomy.SmsProcessorWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * BroadcastReceiver that intercepts incoming SMS messages.
 *
 * On receipt, the sender address and message body are extracted from the intent extras
 * and handed off to [SmsProcessorWorker] via WorkManager, keeping the receiver's
 * `onReceive()` non-blocking.
 *
 * Registered in AndroidManifest.xml with priority 999 for the
 * `android.provider.Telephony.SMS_RECEIVED` action.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages: Array<SmsMessage> =
            Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "SMS_RECEIVED intent contained no messages")
            return
        }

        // Combine multi-part messages from the same sender into one body string.
        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: ""
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        // Attempt to look up the thread ID from the SMS ContentProvider using the sender address.
        // Fall back to 0 if the lookup fails (e.g., the message hasn't been written yet).
        val threadId = resolveThreadId(context, sender)

        val inputData = Data.Builder()
            .putString(SmsProcessorWorker.KEY_SENDER, sender)
            .putString(SmsProcessorWorker.KEY_BODY, body)
            .putLong(SmsProcessorWorker.KEY_THREAD_ID, threadId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SmsProcessorWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "Enqueued SmsProcessorWorker for sender=$sender threadId=$threadId")
    }

    /**
     * Attempts to find the SMS thread ID for the given [sender] address by querying the
     * SMS ContentProvider.  Returns 0 if no matching thread is found.
     */
    private fun resolveThreadId(context: Context, sender: String): Long {
        if (sender.isBlank()) return 0L
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(sender),
                "${Telephony.Sms.DATE} DESC LIMIT 1",
            )
            cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve threadId for sender=$sender", e)
            0L
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
