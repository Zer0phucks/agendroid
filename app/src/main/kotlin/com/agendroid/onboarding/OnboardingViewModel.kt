package com.agendroid.onboarding

import android.Manifest
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agendroid.core.data.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val hasSmsRole: Boolean = false,
    val hasDialerRole: Boolean = false,
    val permissionsGranted: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
) {
    /**
     * Onboarding is considered complete when the app has both system roles,
     * the required runtime permissions, and battery optimization is handled.
     */
    val isComplete: Boolean
        get() = hasSmsRole && hasDialerRole && permissionsGranted && batteryOptimizationExempt
}

/** Side-effect events emitted to the UI layer. */
sealed interface OnboardingEvent {
    data object NavigateToMain : OnboardingEvent
}

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_CALL_LOG,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: AppSettingsRepository,
    private val roleSetupHelper: RoleSetupHelper,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Refresh role state from system on init
        refreshRoleState()
    }

    /** Refresh role state by querying the system. */
    fun refreshRoleState() {
        val ctx = getApplication<Application>()
        _uiState.update { state ->
            state.copy(
                hasSmsRole = roleSetupHelper.isDefaultSmsApp(ctx),
                hasDialerRole = roleSetupHelper.isDefaultDialer(ctx),
            )
        }
    }

    /**
     * Called after the runtime permission request returns. [granted] maps
     * permission name → whether it was granted.
     */
    fun onPermissionsResult(granted: Map<String, Boolean>) {
        val allGranted = REQUIRED_PERMISSIONS.all { granted[it] == true }
        _uiState.update { it.copy(permissionsGranted = allGranted) }
    }

    /** Called when the SMS role request activity returns successfully. */
    fun onSmsRoleGranted() {
        _uiState.update { it.copy(hasSmsRole = true) }
    }

    /** Called when the dialer role request activity returns successfully. */
    fun onDialerRoleGranted() {
        _uiState.update { it.copy(hasDialerRole = true) }
    }

    /** Called when the user acknowledges the battery optimization step. */
    fun onBatteryOptimizationAcknowledged() {
        _uiState.update { it.copy(batteryOptimizationExempt = true) }
    }

    /** Called from the "You're all set" Continue button. Sends the navigation event. */
    fun onOnboardingComplete() {
        viewModelScope.launch {
            _events.send(OnboardingEvent.NavigateToMain)
        }
    }
}
