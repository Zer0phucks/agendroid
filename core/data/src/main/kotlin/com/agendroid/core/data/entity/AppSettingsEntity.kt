package com.agendroid.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Global AI assistant settings stored as a singleton row (id = 1).
 * Values are string-encoded enums to allow future extensibility.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    /** "AUTO" | "SEMI" | "MANUAL" */
    val smsAutonomyMode: String,
    /** "FULL_AGENT" | "SCREEN_ONLY" | "PASS_THROUGH" */
    val callAutonomyMode: String,
    val assistantEnabled: Boolean,
    val selectedModel: String,
)
