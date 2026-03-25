package com.agendroid.feature.assistant.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.voice.WakeWordDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AssistantOverlayViewModel @Inject constructor(
    private val wakeWordDetector: WakeWordDetector,
) : ViewModel() {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun onActivate() {
        if (wakeWordDetector.isRunning) return
        if (!wakeWordDetector.isModelAvailable()) {
            _isListening.value = false
            return
        }
        try {
            wakeWordDetector.load()
            wakeWordDetector.start(viewModelScope) {
                // keyword detected — handle as needed
            }
            _isListening.value = true
        } catch (_: Exception) {
            _isListening.value = false
        }
    }

    fun onDeactivate() {
        wakeWordDetector.stop()
        _isListening.value = false
    }

    override fun onCleared() {
        super.onCleared()
        wakeWordDetector.close()
    }
}
