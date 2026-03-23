package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agendroid.core.data.entity.IndexedSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexedSourceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: IndexedSourceEntity): Long

    @Update
    suspend fun update(entity: IndexedSourceEntity)

    @Delete
    suspend fun delete(entity: IndexedSourceEntity)

    @Query("SELECT * FROM indexed_sources WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): IndexedSourceEntity?

    @Query("DELETE FROM indexed_sources WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT * FROM indexed_sources ORDER BY indexedAt DESC")
    fun getAll(): Flow<List<IndexedSourceEntity>>
}
