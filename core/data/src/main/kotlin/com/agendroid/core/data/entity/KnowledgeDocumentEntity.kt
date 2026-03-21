package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for a document (or conversation source) that has been chunked
 * and indexed into the RAG pipeline.
 */
@Entity(
    tableName = "knowledge_documents",
    indices = [Index(value = ["source_uri"], unique = true)],
)
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "pdf" | "url" | "sms" | "call" | "note" | "contact" | "calendar" */
    @ColumnInfo(name = "source_type") val sourceType: String,
    /** Unique URI identifying the source (file path, URL, or synthetic URI for system data). */
    @ColumnInfo(name = "source_uri") val sourceUri: String,
    val title: String,
    @ColumnInfo(name = "chunk_count") val chunkCount: Int = 0,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long = System.currentTimeMillis(),
    /** SHA-256 of the source content — used to skip re-indexing unchanged documents. */
    val checksum: String = "",
)
