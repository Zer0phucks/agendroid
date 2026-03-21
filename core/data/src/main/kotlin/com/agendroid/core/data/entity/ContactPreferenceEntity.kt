package com.agendroid.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-contact override of global AI autonomy settings.
 * Global defaults apply when no row exists for a normalized contact key.
 */
@Entity(tableName = "contact_preferences")
data class ContactPreferenceEntity(
    @PrimaryKey @ColumnInfo(name = "contact_key") val contactKey: String,
    /** "auto" | "semi" | "manual" */
    @ColumnInfo(name = "sms_autonomy") val smsAutonomy: String = "semi",
    /** "agent" | "screen" | "passthrough" */
    @ColumnInfo(name = "call_handling") val callHandling: String = "screen",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
