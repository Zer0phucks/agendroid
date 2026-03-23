package com.agendroid.feature.sms.autonomy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Displays system notifications for AI-drafted SMS replies.
 *
 * - SEMI mode: "Send" + "Cancel" actions (user must approve before the message is sent).
 * - AUTO mode: Countdown message with a single "Cancel" action (message will auto-send after the
 *   cancel window elapses).
 */
class SmsDraftNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    init {
        createChannel()
    }

    // ------------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------------

    /**
     * Show a notification for an AI-drafted reply in **SEMI** mode (requires user approval).
     *
     * @param replyId   The [PendingSmsReplyEntity] id — used in the pending-intent extras.
     * @param sender    Display name or phone number of the original sender.
     * @param draftText The AI-generated draft text to preview.
     */
    fun showDraftNotification(replyId: Long, sender: String, draftText: String) {
        showSemiModeNotification(replyId, sender, draftText)
    }

    /**
     * Show a notification for an AI-drafted reply in **AUTO** mode (will send automatically).
     *
     * Displays a countdown message and a single "Cancel" action.
     */
    fun showAutoModeNotification(replyId: Long, sender: String, draftText: String) {
        val cancelIntent = buildCancelIntent(replyId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Auto-reply to $sender")
            .setContentText("Sending in ${SmsAutonomyWorker.CANCEL_WINDOW_MS / 1000}s: $draftText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Sending in ${SmsAutonomyWorker.CANCEL_WINDOW_MS / 1000}s:\n\n$draftText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelIntent,
            )
            .build()

        notificationManager.notify(notificationId(replyId), notification)
    }

    fun dismissNotification(replyId: Long) {
        notificationManager.cancel(notificationId(replyId))
    }

    // ------------------------------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------------------------------

    private fun showSemiModeNotification(replyId: Long, sender: String, draftText: String) {
        val cancelIntent = buildCancelIntent(replyId)
        val sendIntent   = buildSendIntent(replyId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Draft reply to $sender")
            .setContentText(draftText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(draftText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Send",
                sendIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelIntent,
            )
            .build()

        notificationManager.notify(notificationId(replyId), notification)
    }

    private fun buildCancelIntent(replyId: Long): PendingIntent {
        val intent = Intent(ACTION_CANCEL_REPLY).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REPLY_ID, replyId)
        }
        return PendingIntent.getBroadcast(
            context,
            replyId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildSendIntent(replyId: Long): PendingIntent {
        val intent = Intent(ACTION_SEND_REPLY).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REPLY_ID, replyId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Use a distinct request code to avoid colliding with the cancel intent.
            (replyId + Int.MAX_VALUE / 2).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SMS Draft Replies",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for AI-drafted SMS replies awaiting send or approval."
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationId(replyId: Long): Int = replyId.toInt()

    companion object {
        const val CHANNEL_ID = "sms_draft_replies"

        /** Broadcast action sent when the user taps "Cancel" on a draft notification. */
        const val ACTION_CANCEL_REPLY = "com.agendroid.feature.sms.ACTION_CANCEL_REPLY"

        /** Broadcast action sent when the user taps "Send" on a SEMI-mode draft notification. */
        const val ACTION_SEND_REPLY = "com.agendroid.feature.sms.ACTION_SEND_REPLY"

        const val EXTRA_REPLY_ID = "extra_reply_id"
    }
}
