package com.agendroid.core.data.repository

import com.agendroid.core.data.dao.IndexedSourceDao
import com.agendroid.core.data.entity.IndexedSourceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for tracking file/URL sources that have been indexed into the knowledge base.
 */
@Singleton
class IndexedSourceRepository @Inject constructor(
    private val dao: IndexedSourceDao,
) {
    /** Emits all indexed sources ordered by [IndexedSourceEntity.indexedAt] descending. */
    val sourcesFlow: Flow<List<IndexedSourceEntity>> = dao.getAll()

    suspend fun getByUri(uri: String): IndexedSourceEntity? = dao.getByUri(uri)

    suspend fun insert(entity: IndexedSourceEntity): Long = dao.insert(entity)

    suspend fun upsert(entity: IndexedSourceEntity): Long {
        val existing = dao.getByUri(entity.uri)
        return if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity.copy(id = existing.id))
            existing.id
        }
    }

    suspend fun delete(entity: IndexedSourceEntity) = dao.delete(entity)

    suspend fun deleteByUri(uri: String) = dao.deleteByUri(uri)
}
