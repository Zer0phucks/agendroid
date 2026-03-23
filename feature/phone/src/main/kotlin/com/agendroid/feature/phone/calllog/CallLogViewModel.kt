package com.agendroid.feature.phone.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.data.model.CallLogEntry
import com.agendroid.core.data.repository.CallLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CallLogUiState(
    val entries: List<CallLogEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CallLogViewModel @Inject constructor(
    callLogRepository: CallLogRepository,
) : ViewModel() {

    val uiState: StateFlow<CallLogUiState> = callLogRepository.getCallLog()
        .map { entries ->
            CallLogUiState(
                entries = entries.sortedByDescending(CallLogEntry::date),
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CallLogUiState(),
        )
}
