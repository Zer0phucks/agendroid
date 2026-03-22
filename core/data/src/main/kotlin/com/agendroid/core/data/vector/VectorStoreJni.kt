// core/data/src/main/kotlin/com/agendroid/core/data/vector/VectorStoreJni.kt
package com.agendroid.core.data.vector

/**
 * JNI bridge to the bundled sqlite3 + sqlite-vec native library.
 * All operations run against an isolated SQLite instance that is NOT Android's
 * system SQLite — this avoids all linker namespace issues on Android 12+.
 */
internal object VectorStoreJni {

    init {
        System.loadLibrary("sqlite_vec_android")
    }

    /** Opens (or creates) the database at [path]. Returns a native handle or 0 on failure. */
    @JvmStatic external fun nativeOpen(path: String): Long

    /** Closes the database. */
    @JvmStatic external fun nativeClose(handle: Long)

    /** Executes a SQL statement with no results. Returns SQLITE_OK (0) on success. */
    @JvmStatic external fun nativeExec(handle: Long, sql: String): Int

    /** Inserts or replaces the embedding for [chunkId]. */
    @JvmStatic external fun nativeInsert(handle: Long, chunkId: Long, embedding: FloatArray): Int

    /** Deletes the embedding for [chunkId]. */
    @JvmStatic external fun nativeDelete(handle: Long, chunkId: Long): Int

    /**
     * Runs a KNN query and returns a flat LongArray:
     * [chunkId0, distBits0, chunkId1, distBits1, ...]
     * where distBits is Float.toRawBits() of the L2 distance.
     */
    @JvmStatic external fun nativeQuery(handle: Long, embedding: FloatArray, limit: Int): LongArray

    /** Returns all chunk IDs currently stored in the vector table. */
    @JvmStatic external fun nativeListIds(handle: Long): LongArray
}
