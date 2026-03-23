package com.agendroid.feature.phone.dialer

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DialerUiState(
    val phoneNumber: String = "",
    val pendingCallIntent: Intent? = null,
)

@HiltViewModel
class DialerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DialerUiState())
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    fun appendDigit(digit: String) {
        _uiState.update { it.copy(phoneNumber = it.phoneNumber + digit) }
    }

    fun removeDigit() {
        _uiState.update { state ->
            state.copy(phoneNumber = state.phoneNumber.dropLast(1))
        }
    }

    fun prepareCallIntent() {
        val number = _uiState.value.phoneNumber.trim()
        if (number.isBlank()) return
        _uiState.update {
            it.copy(
                pendingCallIntent = Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:$number"),
                ),
            )
        }
    }

    fun consumePendingCallIntent() {
        _uiState.update { it.copy(pendingCallIntent = null) }
    }
}
