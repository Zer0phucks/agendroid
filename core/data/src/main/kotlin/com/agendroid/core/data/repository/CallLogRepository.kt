package com.agendroid.core.data.repository

import android.content.Context
import android.provider.CallLog
import com.agendroid.core.data.model.CallLogEntry
import com.agendroid.core.data.util.PhoneNumberNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface CallLogRepository {
    fun getCallLog(limit: Int = 100): Flow<List<CallLogEntry>>
    suspend fun getCallsFromNumber(phoneNumber: String, limit: Int = 20): List<CallLogEntry>
}

@Singleton
class CallLogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val normalizer: PhoneNumberNormalizer,
) : CallLogRepository {

    override fun getCallLog(limit: Int): Flow<List<CallLogEntry>> = flow {
        emit(query(limit = limit))
    }

    override suspend fun getCallsFromNumber(phoneNumber: String, limit: Int): List<CallLogEntry> {
        val target = normalizer.normalize(phoneNumber)
        return query(limit = maxOf(limit * 5, 50))
            .filter { it.numberKey == target }
            .take(limit)
    }

    private fun query(limit: Int): List<CallLogEntry> {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        ) ?: return emptyList()

        return cursor.use {
            val entries = mutableListOf<CallLogEntry>()
            while (it.moveToNext()) {
                val rawNumber = it.getString(1) ?: ""
                entries += CallLogEntry(
                    id = it.getLong(0),
                    rawNumber = rawNumber,
                    numberKey = normalizer.normalize(rawNumber),
                    name = it.getString(2),
                    date = it.getLong(3),
                    duration = it.getLong(4),
                    callType = it.getInt(5),
                )
            }
            entries
        }
    }
}
