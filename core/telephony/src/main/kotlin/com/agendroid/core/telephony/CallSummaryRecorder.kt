package com.agendroid.core.telephony

import com.agendroid.core.data.dao.ConversationSummaryDao
import com.agendroid.core.data.entity.ConversationSummaryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallSummaryRecorder @Inject constructor(
    private val conversationSummaryDao: ConversationSummaryDao,
) {

    suspend fun record(session: CallSession) {
        val contactKey = session.number?.trim().orEmpty()
        if (contactKey.isBlank() || session.transcript.isEmpty()) return

        val summary = session.transcript
            .takeLast(6)
            .joinToString(separator = " ") { line ->
                "${line.speaker.name.lowercase()}: ${line.text}"
            }

        conversationSummaryDao.upsert(
            ConversationSummaryEntity(
                contactKey = contactKey,
                type = "call",
                summary = summary,
            ),
        )
    }
}
