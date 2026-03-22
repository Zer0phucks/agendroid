package com.agendroid.core.telephony

data class CallTranscriptLine(
    val speaker: Speaker,
    val text: String,
    val timestampMs: Long,
) {
    enum class Speaker {
        CALLER,
        ASSISTANT,
        USER,
        SYSTEM,
    }
}
