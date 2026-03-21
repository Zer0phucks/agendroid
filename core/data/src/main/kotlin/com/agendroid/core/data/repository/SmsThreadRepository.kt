package com.agendroid.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import com.agendroid.core.data.model.SmsMessage
import com.agendroid.core.data.model.SmsThread
import com.agendroid.core.common.Result
import com.agendroid.core.data.util.PhoneNumberNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface SmsThreadRepository {
    fun getThreads(): Flow<List<SmsThread>>
    suspend fun getMessages(threadId: Long, limit: Int = 50): List<SmsMessage>
    suspend fun sendSms(to: String, body: String, subscriptionId: Int? = null): Result<Unit>
}

@Singleton
class SmsThreadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: PhoneNumberNormalizer,
) : SmsThreadRepository {

    private fun smsManagerFor(subscriptionId: Int?): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subscriptionId != null) manager.createForSubscriptionId(subscriptionId) else manager
    }

    override fun getThreads(): Flow<List<SmsThread>> = flow {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC",
        )
        val threads = linkedMapOf<Long, SmsThread>()
        cursor?.use {
            val colThread  = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val colAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val colBody    = it.getColumnIndex(Telephony.Sms.BODY)
            val colDate    = it.getColumnIndex(Telephony.Sms.DATE)
            val colSubId   = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            while (it.moveToNext()) {
                val threadId = it.getLong(colThread)
                if (!threads.containsKey(threadId)) {
                    val rawAddress = it.getString(colAddress) ?: ""
                    threads[threadId] = SmsThread(
                        threadId = threadId,
                        participantKey = normalizer.normalize(rawAddress),
                        snippet = (it.getString(colBody) ?: "").take(80),
                        date = it.getLong(colDate),
                        unreadCount = 0,
                        subscriptionId = if (colSubId >= 0 && !it.isNull(colSubId)) it.getInt(colSubId) else null,
                    )
                }
            }
        }
        emit(threads.values.toList())
    }

    override suspend fun getMessages(threadId: Long, limit: Int): List<SmsMessage> {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        ) ?: return emptyList()

        return cursor.use {
            val messages = mutableListOf<SmsMessage>()
            while (it.moveToNext()) {
                messages += SmsMessage(
                    id = it.getLong(0),
                    threadId = it.getLong(1),
                    rawAddress = it.getString(2) ?: "",
                    addressKey = normalizer.normalize(it.getString(2)),
                    body = it.getString(3) ?: "",
                    date = it.getLong(4),
                    type = it.getInt(5),
                    read = it.getInt(6) == 1,
                    subscriptionId = if (!it.isNull(7)) it.getInt(7) else null,
                )
            }
            messages
        }
    }

    override suspend fun sendSms(to: String, body: String, subscriptionId: Int?): Result<Unit> {
        return try {
            smsManagerFor(subscriptionId)
                .sendTextMessage(to, null, body, null, null)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, to)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
                subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
            }
            val sentUri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            if (sentUri == null) {
                android.util.Log.w("SmsThreadRepo", "Failed to record sent SMS in provider (not default SMS app?)")
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
