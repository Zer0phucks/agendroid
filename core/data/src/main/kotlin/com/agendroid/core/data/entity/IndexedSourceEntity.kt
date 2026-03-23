package com.agendroid.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks files and URLs that have been (or are pending) indexing into the knowledge base.
 *
 * [sourceType]: "FILE" | "URL"
 * [indexedAt]: epoch ms; 0 = pending
 * [status]: "PENDING" | "INDEXED" | "FAILED"
 */
@Entity(tableName = "indexed_sources")
data class IndexedSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "FILE" | "URL" */
    val sourceType: String,
    val uri: String,
    val title: String,
    /** epoch ms; 0 = pending */
    val indexedAt: Long,
    /** "PENDING" | "INDEXED" | "FAILED" */
    val status: String,
)
