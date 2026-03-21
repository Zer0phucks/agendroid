package com.agendroid.core.data.dao

import androidx.room.*
import com.agendroid.core.data.entity.KnowledgeDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDocumentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(doc: KnowledgeDocumentEntity): Long

    @Update
    suspend fun update(doc: KnowledgeDocumentEntity)

    @Delete
    suspend fun delete(doc: KnowledgeDocumentEntity)

    @Query("SELECT * FROM knowledge_documents ORDER BY indexed_at DESC")
    fun getAll(): Flow<List<KnowledgeDocumentEntity>>

    @Query("SELECT * FROM knowledge_documents WHERE source_uri = :sourceUri LIMIT 1")
    suspend fun getBySourceUri(sourceUri: String): KnowledgeDocumentEntity?

    @Query("SELECT * FROM knowledge_documents WHERE id = :documentId LIMIT 1")
    suspend fun getById(documentId: Long): KnowledgeDocumentEntity?
}
