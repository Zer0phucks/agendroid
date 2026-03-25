package com.agendroid.core.telephony

data class CallSession(
    val callId: String,
    val number: String?,
    val mode: CallAutonomyMode,
    val transcript: List<CallTranscriptLine> = emptyList(),
    val isDisclosureDelivered: Boolean = false,
    val isAiHandling: Boolean = false,
    val isTakeoverRequested: Boolean = false,
)
