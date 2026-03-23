package com.agendroid.core.data.repository

import com.agendroid.core.data.dao.PendingSmsReplyDao
import com.agendroid.core.data.entity.PendingSmsReplyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AI-drafted SMS replies that are awaiting automatic send or user approval.
 */
@Singleton
class PendingSmsReplyRepository @Inject constructor(
    private val dao: PendingSmsReplyDao,
) {
    /** Emits the list of replies whose [PendingSmsReplyEntity.status] is "PENDING". */
    val pendingRepliesFlow: Flow<List<PendingSmsReplyEntity>> = dao.getPending()

    suspend fun insert(entity: PendingSmsReplyEntity): Long = dao.insert(entity)

    suspend fun updateStatus(id: Long, status: String) = dao.updateStatus(id, status)
}
