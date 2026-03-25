package com.agendroid.feature.assistant.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.data.entity.AppSettingsEntity
import com.agendroid.core.data.entity.IndexedSourceEntity
import com.agendroid.core.data.repository.AppSettingsRepository
import com.agendroid.core.data.repository.IndexedSourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutonomySettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val indexedSourceRepository: IndexedSourceRepository,
) : ViewModel() {

    val settingsState: StateFlow<AppSettingsEntity?> = appSettingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sourcesState: StateFlow<List<IndexedSourceEntity>> = indexedSourceRepository.sourcesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onSmsMode(mode: String) {
        viewModelScope.launch {
            appSettingsRepository.updateSmsMode(mode)
        }
    }

    fun onCallMode(mode: String) {
        viewModelScope.launch {
            appSettingsRepository.updateCallMode(mode)
        }
    }

    fun onModelSelected(model: String) {
        viewModelScope.launch {
            appSettingsRepository.updateSelectedModel(model)
        }
    }
}
