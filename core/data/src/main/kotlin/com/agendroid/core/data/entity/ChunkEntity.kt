package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A RAG text chunk from a document or conversation source.
 * The float embedding for this chunk is stored in VectorStore keyed by [id].
 */
@Entity(
    tableName = "chunks",
    indices = [Index("document_id"), Index("contact_filter")],
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    /** "sms" | "call" | "note" | "contact" | "doc" | "calendar" */
    @ColumnInfo(name = "source_type") val sourceType: String,
    /** Normalized phone number for contact-scoped retrieval; null for global chunks. */
    @ColumnInfo(name = "contact_filter") val contactFilter: String?,
    @ColumnInfo(name = "chunk_text") val chunkText: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
)
