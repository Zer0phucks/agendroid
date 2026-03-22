// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStore.kt
package com.agendroid.core.data.vector

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

private const val EMBEDDING_DIM = 384

/**
 * Wraps the sqlite-vec extension to store and query 384-dimensional float embeddings.
 *
 * Full implementation lives in :core:data (Plan 3). This stub is present in :core:ai
 * so RagOrchestrator can compile against the interface.
 */
@Singleton
class VectorStore @Inject constructor(
    private val context: Context,
    private val dbName: String = "vectors.db",
) {
    /** Stores or replaces the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun insert(chunkId: Long, embedding: FloatArray) {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
    }

    /**
     * Returns the [limit] most similar stored embeddings, ordered by ascending L2 distance.
     */
    fun query(embedding: FloatArray, limit: Int = 5): List<VectorResult> {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
        require(limit > 0) { "limit must be > 0" }
        return emptyList()
    }

    /** Removes the embedding for [chunkId]. */
    fun delete(chunkId: Long) {}

    /** Removes multiple embeddings. */
    fun deleteAll(chunkIds: List<Long>) {}

    /** Returns all indexed chunk IDs. */
    fun listChunkIds(): Set<Long> = emptySet()

    fun close() {}
}
