package com.agendroid.core.data.model

data class SmsThread(
    val threadId: Long,
    /** Normalized phone/address key for contact matching and autonomy rules. */
    val participantKey: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val subscriptionId: Int?,
)
