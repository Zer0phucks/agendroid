package com.agendroid.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.ai.AiServiceClient
import com.agendroid.core.ai.ResourceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lightweight ViewModel that exposes [ResourceState] to [AgendroidNavHost] so it can show a
 * persistent degraded-state banner without coupling the nav host directly to [AiServiceClient].
 *
 * The [resourceState] flow defaults to [ResourceState.Normal] until the service binds and
 * emits its first value. Any bind or flow error silently resets to [ResourceState.Normal]
 * so the banner never incorrectly shows due to a transient error.
 */
@HiltViewModel
class ResourceStateViewModel @Inject constructor(
    private val aiServiceClient: AiServiceClient,
) : ViewModel() {

    private val _resourceState = MutableStateFlow<ResourceState>(ResourceState.Normal)
    val resourceState: StateFlow<ResourceState> = _resourceState.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val service = aiServiceClient.getService()
                    service.resourceState.collect { state ->
                        _resourceState.value = state
                    }
                    // collect() returned — service disconnected, reset and retry
                    _resourceState.value = ResourceState.Normal
                } catch (_: Exception) {
                    // Service not yet available or bind error — reset and retry
                    _resourceState.value = ResourceState.Normal
                }
                delay(5_000L) // wait before retrying
            }
        }
    }
}
