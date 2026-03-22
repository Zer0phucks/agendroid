package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * AI-generated summary of a contact's SMS conversation or call history.
 * Keyed by (contactKey, type) — one row per normalized phone/address key per type.
 * Upserted by AiCoreService after each significant exchange.
 */
@Entity(
    tableName = "conversation_summaries",
    primaryKeys = ["contact_key", "type"],
)
data class ConversationSummaryEntity(
    @ColumnInfo(name = "contact_key") val contactKey: String,
    /** "sms" or "call" */
    val type: String,
    val summary: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
