package com.agendroid.feature.sms.ui.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.data.model.SmsThread
import com.agendroid.core.data.repository.SmsThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SmsThreadsUiState(
    val threads: List<SmsThread> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SmsThreadsViewModel @Inject constructor(
    smsThreadRepository: SmsThreadRepository,
) : ViewModel() {

    val uiState: StateFlow<SmsThreadsUiState> = smsThreadRepository.getThreads()
        .map { threads ->
            SmsThreadsUiState(
                threads = threads.sortedByDescending(SmsThread::date),
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SmsThreadsUiState(),
        )
}
