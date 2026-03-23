package com.agendroid.feature.phone.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.telephony.CallAutonomyMode
import com.agendroid.core.telephony.CallSession
import com.agendroid.core.telephony.CallSessionRepository
import com.agendroid.core.telephony.CallTranscriptLine
import com.agendroid.core.telephony.TelephonyCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class InCallUiState(
    val number: String? = null,
    val mode: CallAutonomyMode = CallAutonomyMode.PASS_THROUGH,
    val disclosureDelivered: Boolean = false,
    val transcript: List<CallTranscriptLine> = emptyList(),
    val isAssistantSpeaking: Boolean = false,
    val isTakeoverRequested: Boolean = false,
)

@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callSessionRepository: CallSessionRepository,
    private val telephonyCoordinator: TelephonyCoordinator,
) : ViewModel() {

    val uiState: StateFlow<InCallUiState> = callSessionRepository.activeSession
        .map(::mapSession)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = InCallUiState(),
        )

    fun requestTakeover() {
        telephonyCoordinator.requestTakeover()
    }

    private fun mapSession(session: CallSession?): InCallUiState {
        if (session == null) return InCallUiState()
        return InCallUiState(
            number = session.number,
            mode = session.mode,
            disclosureDelivered = session.isDisclosureDelivered,
            transcript = session.transcript,
            isAssistantSpeaking = session.isAiHandling && !session.isTakeoverRequested,
            isTakeoverRequested = session.isTakeoverRequested,
        )
    }
}
