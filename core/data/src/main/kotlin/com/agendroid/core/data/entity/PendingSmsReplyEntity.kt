package com.agendroid.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An AI-drafted SMS reply awaiting either automatic send or user approval.
 *
 * [scheduledSendAt] = 0 means the reply is in SEMI mode and requires manual approval.
 * [status]: "PENDING" | "SENT" | "CANCELLED" | "EXPIRED"
 */
@Entity(tableName = "pending_sms_replies")
data class PendingSmsReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val sender: String,
    val draftText: String,
    /** epoch ms; 0 = SEMI mode (manual approval) */
    val scheduledSendAt: Long,
    /** "PENDING" | "SENT" | "CANCELLED" | "EXPIRED" */
    val status: String,
    val createdAt: Long,
)
