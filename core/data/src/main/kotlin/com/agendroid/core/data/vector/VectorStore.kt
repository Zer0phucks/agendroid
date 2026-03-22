// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStore.kt
package com.agendroid.core.data.vector

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

private const val EMBEDDING_DIM = 384

/**
 * Wraps the sqlite-vec extension (bundled sqlite3 build) to store and query
 * 384-dimensional float embeddings.
 *
 * Uses an isolated sqlite3 binary (NOT Android's system SQLite) to avoid
 * linker namespace restrictions on Android 12+. The database file lives in
 * app-private storage. Only float embeddings are stored here — the matching
 * chunk text lives in Room's AppDatabase.
 *
 * Thread safety: all operations are blocking. Call from Dispatchers.IO.
 */
@Singleton
class VectorStore @Inject constructor(
    private val context: Context,
    private val dbName: String = "vectors.db",
) {
    private val dbHandle: Long by lazy { openDatabase() }

    private fun openDatabase(): Long {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val handle = VectorStoreJni.nativeOpen(file.absolutePath)
        check(handle != 0L) { "Failed to open VectorStore database at ${file.absolutePath}" }
        val rc = VectorStoreJni.nativeExec(handle, """
            CREATE VIRTUAL TABLE IF NOT EXISTS chunks_vec USING vec0(
                chunk_id INTEGER PRIMARY KEY,
                embedding FLOAT[$EMBEDDING_DIM]
            )
        """.trimIndent())
        check(rc == 0) { "Failed to create chunks_vec virtual table (rc=$rc)" }
        return handle
    }

    /** Stores or replaces the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun insert(chunkId: Long, embedding: FloatArray) {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
        val rc = VectorStoreJni.nativeInsert(dbHandle, chunkId, embedding)
        check(rc == 0) { "insert failed (rc=$rc)" }
    }

    /**
     * Returns the [limit] most similar stored embeddings, ordered by ascending L2 distance.
     * Returns an empty list if the store is empty.
     * Call from Dispatchers.IO.
     */
    fun query(embedding: FloatArray, limit: Int = 5): List<VectorResult> {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
        require(limit > 0) { "limit must be > 0" }
        val flat = VectorStoreJni.nativeQuery(dbHandle, embedding, limit)
        val count = flat.size / 2
        return (0 until count).map { i ->
            VectorResult(
                chunkId = flat[i * 2],
                distance = Float.fromBits(flat[i * 2 + 1].toInt()),
            )
        }
    }

    /** Removes the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun delete(chunkId: Long) {
        VectorStoreJni.nativeDelete(dbHandle, chunkId)
    }

    /** Removes multiple embeddings; used by KnowledgeIndexRepository compensation paths. */
    fun deleteAll(chunkIds: List<Long>) {
        chunkIds.forEach { delete(it) }
    }

    /** Returns all indexed chunk IDs. Used to prune stale vectors after partial failures. */
    fun listChunkIds(): Set<Long> {
        return VectorStoreJni.nativeListIds(dbHandle).toHashSet()
    }

    /**
     * Closes the underlying sqlite3 database handle.
     *
     * **Thread safety:** callers must ensure that no concurrent [insert], [query], [delete],
     * [deleteAll], or [listChunkIds] calls are in flight before invoking [close]. Calling
     * [close] while another thread is executing a JNI operation will result in a use-after-free
     * native crash.
     */
    fun close() {
        val h = dbHandle
        if (h != 0L) VectorStoreJni.nativeClose(h)
    }
}
