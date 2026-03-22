package com.agendroid.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agendroid.core.data.entity.ChunkEntity

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>): List<Long>

    /** Fetch chunks by their IDs (used after VectorStore returns top-k chunk IDs). */
    @Query("SELECT * FROM chunks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE document_id = :documentId")
    suspend fun getByDocumentId(documentId: Long): List<ChunkEntity>

    /** Internal: used by KnowledgeIndexRepository to keep VectorStore in sync with Room. */
    @Query("SELECT id FROM chunks WHERE document_id = :documentId")
    suspend fun getIdsByDocumentId(documentId: Long): List<Long>

    /** Internal: used by KnowledgeIndexRepository.pruneOrphanVectors(). */
    @Query("SELECT id FROM chunks")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM chunks WHERE document_id = :documentId")
    suspend fun deleteByDocumentId(documentId: Long)

    @Query("SELECT COUNT(*) FROM chunks WHERE document_id = :documentId")
    suspend fun countByDocumentId(documentId: Long): Int
}
