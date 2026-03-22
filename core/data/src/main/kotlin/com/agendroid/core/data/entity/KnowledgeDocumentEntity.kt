package com.agendroid.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stub — full implementation in :core:data (Plan 3).
 */
@Entity(tableName = "knowledge_documents")
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String = "",
    val sourceUri: String = "",
)
