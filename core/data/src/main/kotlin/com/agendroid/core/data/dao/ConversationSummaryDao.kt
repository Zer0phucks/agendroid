package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.ConversationSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ConversationSummaryEntity)

    @Query("SELECT * FROM conversation_summaries WHERE contact_key = :contactKey")
    fun getForContactKey(contactKey: String): Flow<List<ConversationSummaryEntity>>

    @Query("SELECT * FROM conversation_summaries WHERE contact_key = :contactKey AND type = :type LIMIT 1")
    suspend fun get(contactKey: String, type: String): ConversationSummaryEntity?
}
