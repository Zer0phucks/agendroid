package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.PendingSmsReplyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSmsReplyDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PendingSmsReplyEntity): Long

    @Query("UPDATE pending_sms_replies SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT * FROM pending_sms_replies WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPending(): Flow<List<PendingSmsReplyEntity>>
}
