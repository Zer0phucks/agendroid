// core/data/src/main/kotlin/com/agendroid/core/data/repository/KnowledgeIndexRepository.kt
package com.agendroid.core.data.repository

import androidx.room.withTransaction
import com.agendroid.core.common.Result
import com.agendroid.core.data.db.AppDatabase
import com.agendroid.core.data.entity.ChunkEntity
import com.agendroid.core.data.vector.VectorStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONLY write path allowed to mutate both AppDatabase (Room) and VectorStore together.
 *
 * All mutations keep the two stores consistent:
 * - Room chunks table is the source of truth for which chunk IDs exist.
 * - VectorStore mirrors those IDs with their float embeddings.
 *
 * Call all methods from Dispatchers.IO.
 */
interface KnowledgeIndexRepository {

    /**
     * Atomically replaces all chunks (and their vectors) for [documentId].
     *
     * 1. Deletes existing Room rows for [documentId] and their vectors.
     * 2. Inserts [chunks] into Room and retrieves their auto-generated IDs.
     * 3. Inserts the corresponding [embeddings] into VectorStore.
     * On VectorStore failure, compensates by deleting the newly inserted vectors.
     *
     * @param chunks must be the same size as [embeddings]; each index corresponds to a chunk.
     * @param embeddings list of 384-dimensional float arrays parallel to [chunks].
     * @return [Result.Success] with the list of new Room chunk IDs, or [Result.Failure].
     */
    suspend fun replaceDocumentChunks(
        documentId: Long,
        chunks: List<ChunkEntity>,
        embeddings: List<FloatArray>,
    ): Result<List<Long>>

    /**
     * Deletes all chunks (Room rows + vectors) and the parent document record for [documentId].
     *
     * @return [Result.Success] with Unit, or [Result.Failure].
     */
    suspend fun deleteDocumentIndex(documentId: Long): Result<Unit>

    /**
     * Removes any vectors whose chunk ID has no corresponding row in Room's chunks table.
     * Safe to call periodically after partial failures or crashes.
     *
     * @return [Result.Success] with the count of orphans deleted, or [Result.Failure].
     */
    suspend fun pruneOrphanVectors(): Result<Int>
}

@Singleton
class KnowledgeIndexRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val vectorStore: VectorStore,
) : KnowledgeIndexRepository {

    override suspend fun replaceDocumentChunks(
        documentId: Long,
        chunks: List<ChunkEntity>,
        embeddings: List<FloatArray>,
    ): Result<List<Long>> {
        require(chunks.size == embeddings.size) {
            "chunks.size (${chunks.size}) must equal embeddings.size (${embeddings.size})"
        }

        return try {
            // 1. Room transaction: fetch existing IDs, delete existing chunks, insert new ones.
            val (existingIds, newIds) = db.withTransaction {
                val existing = db.chunkDao().getIdsByDocumentId(documentId)
                db.chunkDao().deleteByDocumentId(documentId)
                val inserted = db.chunkDao().insertAll(chunks)
                Pair(existing, inserted)
            }

            // 2. Remove old vectors (non-fatal: orphans will be cleaned by pruneOrphanVectors).
            try {
                vectorStore.deleteAll(existingIds)
            } catch (t: Throwable) {
                // Old vectors become orphans — pruneOrphanVectors() will clean them up.
                // Do not fail the operation; new chunk inserts proceed below.
                android.util.Log.w("KnowledgeIndexRepo", "Failed to delete old vectors; orphans will be pruned", t)
            }

            // 3. Insert new vectors; compensate if any insertion fails.
            val insertedIds = mutableListOf<Long>()
            try {
                newIds.zip(embeddings).forEach { (id, embedding) ->
                    vectorStore.insert(id, embedding)
                    insertedIds.add(id)
                }
            } catch (insertError: Throwable) {
                // Roll back Room rows
                db.withTransaction { db.chunkDao().deleteByDocumentId(documentId) }
                // Roll back any vectors that were inserted; swallow failure (will be pruned later)
                try {
                    vectorStore.deleteAll(insertedIds)
                } catch (compensationError: Throwable) {
                    android.util.Log.w("KnowledgeIndexRepo", "Compensation deleteAll failed; orphans will be pruned", compensationError)
                }
                throw insertError  // re-throw original so outer catch wraps it in Result.Failure
            }

            Result.Success(newIds)
        } catch (t: Throwable) {
            Result.Failure(t)
        }
    }

    override suspend fun deleteDocumentIndex(documentId: Long): Result<Unit> {
        return try {
            // Collect chunk IDs before deleting so we can remove their vectors.
            val chunkIds = db.chunkDao().getIdsByDocumentId(documentId)

            // Delete chunks (and optionally the document) in a transaction.
            db.withTransaction {
                db.chunkDao().deleteByDocumentId(documentId)
                val doc = db.knowledgeDocumentDao().getById(documentId)
                if (doc != null) {
                    db.knowledgeDocumentDao().delete(doc)
                }
            }

            // Remove vectors after the Room transaction succeeds (non-fatal: orphans pruned later).
            try {
                vectorStore.deleteAll(chunkIds)
            } catch (t: Throwable) {
                android.util.Log.w("KnowledgeIndexRepo", "Vector deleteAll failed after Room commit; orphans will be pruned", t)
            }
            return Result.Success(Unit)
        } catch (t: Throwable) {
            Result.Failure(t)
        }
    }

    override suspend fun pruneOrphanVectors(): Result<Int> {
        return try {
            val roomIds = db.chunkDao().getAllIds().toHashSet()
            val vectorIds = vectorStore.listChunkIds()
            val orphans = vectorIds - roomIds
            vectorStore.deleteAll(orphans.toList())
            Result.Success(orphans.size)
        } catch (t: Throwable) {
            Result.Failure(t)
        }
    }
}
