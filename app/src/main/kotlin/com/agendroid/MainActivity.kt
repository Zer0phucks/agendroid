package com.agendroid

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.agendroid.navigation.AgendroidDestination
import com.agendroid.navigation.AgendroidNavHost
import com.agendroid.onboarding.OnboardingViewModel
import com.agendroid.onboarding.RoleSetupHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    @Inject
    lateinit var roleSetupHelper: RoleSetupHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Determine start destination based on current system state.
        // If both roles are already held and permissions were previously granted,
        // skip onboarding entirely and land on the SMS tab.
        val startDestination = resolveStartDestination()

        setContent {
            AgendroidNavHost(startDestination = startDestination)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh role state every time the activity comes back to the foreground
        // (e.g., after the user returns from the system role dialog).
        onboardingViewModel.refreshRoleState()
    }

    private fun resolveStartDestination(): String {
        val hasSmsRole = roleSetupHelper.isDefaultSmsApp(this)
        val hasDialerRole = roleSetupHelper.isDefaultDialer(this)
        val permissionsGranted = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
        ).all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return if (hasSmsRole && hasDialerRole && permissionsGranted) {
            AgendroidDestination.Sms.route
        } else {
            AgendroidDestination.Onboarding.route
        }
    }
}
