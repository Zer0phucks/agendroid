package com.agendroid.core.data.model

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val rawAddress: String,
    /** Normalized phone/address key for lookups and summaries. */
    val addressKey: String,
    val body: String,
    val date: Long,
    val type: Int,  // Telephony.Sms.MESSAGE_TYPE_INBOX / _SENT / etc.
    val read: Boolean,
    val subscriptionId: Int?,
)
