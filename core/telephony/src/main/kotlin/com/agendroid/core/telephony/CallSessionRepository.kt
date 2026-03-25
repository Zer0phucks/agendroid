package com.agendroid.core.telephony

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallSessionRepository @Inject constructor() {

    private val mutableActiveSession = MutableStateFlow<CallSession?>(null)
    val activeSession: StateFlow<CallSession?> = mutableActiveSession.asStateFlow()

    fun startSession(
        callId: String,
        number: String?,
        mode: CallAutonomyMode,
    ) {
        mutableActiveSession.value = CallSession(
            callId = callId,
            number = number,
            mode = mode,
        )
    }

    fun appendTranscript(line: CallTranscriptLine) {
        val session = mutableActiveSession.value ?: return
        mutableActiveSession.value = session.copy(transcript = session.transcript + line)
    }

    fun setAiHandling(isAiHandling: Boolean) {
        val session = mutableActiveSession.value ?: return
        mutableActiveSession.value = session.copy(isAiHandling = isAiHandling)
    }

    fun markDisclosureDelivered() {
        val session = mutableActiveSession.value ?: return
        mutableActiveSession.value = session.copy(isDisclosureDelivered = true)
    }

    fun requestTakeover() {
        val session = mutableActiveSession.value ?: return
        mutableActiveSession.value = session.copy(
            isAiHandling = false,
            isTakeoverRequested = true,
        )
    }

    fun clearSession() {
        mutableActiveSession.value = null
    }
}
