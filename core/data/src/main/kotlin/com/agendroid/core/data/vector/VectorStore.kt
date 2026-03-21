// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStore.kt
package com.agendroid.core.data.vector

import android.content.Context
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

private const val EMBEDDING_DIM = 384

/**
 * Wraps the sqlite-vec extension to store and query 384-dimensional float embeddings.
 *
 * The database file lives in app-private storage (`context.getDatabasePath(dbName)`).
 * Only embedding vectors are stored here — the matching text is in Room's AppDatabase
 * under `chunks.chunk_text`, joined by chunk_id.
 *
 * Thread safety: sqlite-vec operations are blocking I/O. Call from Dispatchers.IO.
 */
@Singleton
class VectorStore @Inject constructor(
    private val context: Context,
    private val dbName: String = "vectors.db",
) {
    private val db: SQLiteDatabase by lazy { openDatabase() }

    private fun openDatabase(): SQLiteDatabase {
        // Register the sqlite-vec extension with Android's SQLite.
        // This is idempotent — safe to call multiple times.
        SqliteVecAndroid.init()

        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS chunks_vec USING vec0(
                chunk_id INTEGER PRIMARY KEY,
                embedding FLOAT[$EMBEDDING_DIM]
            )
        """.trimIndent())
        return db
    }

    /** Stores or replaces the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun insert(chunkId: Long, embedding: FloatArray) {
        require(embedding.size == EMBEDDING_DIM) {
            "Expected $EMBEDDING_DIM floats, got ${embedding.size}"
        }
        db.execSQL(
            "INSERT OR REPLACE INTO chunks_vec (chunk_id, embedding) VALUES (?, ?)",
            arrayOf(chunkId, embedding.toVecBytes()),
        )
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
        val results = mutableListOf<VectorResult>()
        val cursor = db.rawQueryWithFactory(
            { _, driver, editTable, query ->
                query.bindBlob(1, embedding.toVecBytes())
                SQLiteCursor(driver, editTable, query)
            },
            "SELECT chunk_id, distance FROM chunks_vec WHERE embedding MATCH ? AND k = $limit",
            emptyArray(),
            "",
        )
        cursor.use {
            while (it.moveToNext()) {
                results += VectorResult(
                    chunkId = it.getLong(0),
                    distance = it.getFloat(1),
                )
            }
        }
        return results
    }

    /** Removes the embedding for [chunkId]. Call from Dispatchers.IO. */
    fun delete(chunkId: Long) {
        db.execSQL("DELETE FROM chunks_vec WHERE chunk_id = ?", arrayOf(chunkId))
    }

    /** Removes multiple embeddings; used by KnowledgeIndexRepository compensation paths. */
    fun deleteAll(chunkIds: List<Long>) {
        if (chunkIds.isEmpty()) return
        db.beginTransaction()
        try {
            chunkIds.forEach { delete(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Returns all indexed chunk IDs. Used to prune stale vectors after partial failures. */
    fun listChunkIds(): Set<Long> {
        val ids = linkedSetOf<Long>()
        db.rawQuery("SELECT chunk_id FROM chunks_vec", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getLong(0)
            }
        }
        return ids
    }

    fun close() {
        if (db.isOpen) db.close()
    }

    private fun FloatArray.toVecBytes(): ByteArray {
        val buf = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        forEach { buf.putFloat(it) }
        return buf.array()
    }
}
