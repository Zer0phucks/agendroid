package com.agendroid.core.data.model

data class CallLogEntry(
    val id: Long,
    val rawNumber: String,
    val numberKey: String,
    val name: String?,
    val date: Long,
    val duration: Long,
    /** CallLog.Calls.INCOMING_TYPE / OUTGOING_TYPE / MISSED_TYPE */
    val callType: Int,
)
