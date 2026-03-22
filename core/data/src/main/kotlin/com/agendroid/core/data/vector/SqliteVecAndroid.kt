// core/data/src/main/kotlin/com/agendroid/core/data/vector/SqliteVecAndroid.kt
package com.agendroid.core.data.vector

/**
 * Loads the sqlite-vec JNI library and registers the vec0 extension with
 * Android's SQLite via sqlite3_auto_extension.
 *
 * Must be called before opening any SQLiteDatabase that uses vec0 virtual tables.
 * Safe to call multiple times (sqlite3_auto_extension is idempotent for the same function).
 */
object SqliteVecAndroid {

    init {
        System.loadLibrary("sqlite_vec_android")
    }

    @JvmStatic
    external fun nativeInit()

    /**
     * Registers sqlite-vec with Android's SQLite.
     * Call this once at app startup (or before first VectorStore use).
     */
    fun init() {
        nativeInit()
    }
}
