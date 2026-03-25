package com.agendroid.feature.assistant.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.data.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelSettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {
    val selectedModel: StateFlow<String> = appSettingsRepository.settingsFlow
        .map { it?.selectedModel ?: "gemma3" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "gemma3")

    fun onModelSelected(model: String) {
        viewModelScope.launch { appSettingsRepository.updateSelectedModel(model) }
    }
}
