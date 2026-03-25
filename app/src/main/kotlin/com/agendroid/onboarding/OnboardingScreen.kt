package com.agendroid.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun Context.findActivity(): Activity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No Activity found in context chain")
}

/**
 * Multi-step onboarding screen.
 *
 * Steps (in order):
 * 1. Default SMS role
 * 2. Default Dialer role
 * 3. Runtime permissions
 * 4. Battery optimization exemption
 * 5. Done
 *
 * The screen advances through steps automatically as each requirement is met.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    // Launcher for multiple permission request
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = viewModel::onPermissionsResult,
    )

    // Launcher for SMS role request
    val smsRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onSmsRoleGranted()
            } else {
                // Re-check system state — user may have granted it another way
                viewModel.refreshRoleState()
            }
        },
    )

    // Launcher for Dialer role request
    val dialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onDialerRoleGranted()
            } else {
                viewModel.refreshRoleState()
            }
        },
    )

    // Determine current step
    val step = when {
        !uiState.hasSmsRole -> OnboardingStep.SmsRole
        !uiState.hasDialerRole -> OnboardingStep.DialerRole
        !uiState.permissionsGranted -> OnboardingStep.Permissions
        !uiState.batteryOptimizationExempt -> OnboardingStep.Battery
        else -> OnboardingStep.Done
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                OnboardingStep.SmsRole -> SmsRoleStep(
                    onRequest = {
                        val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                        smsRoleLauncher.launch(intent)
                    },
                )

                OnboardingStep.DialerRole -> DialerRoleStep(
                    onRequest = {
                        val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                        dialerRoleLauncher.launch(intent)
                    },
                )

                OnboardingStep.Permissions -> PermissionsStep(
                    onRequest = { permissionsLauncher.launch(REQUIRED_PERMISSIONS) },
                )

                OnboardingStep.Battery -> BatteryStep(
                    onRequest = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                        // Mark as acknowledged — user can choose to skip
                        viewModel.onBatteryOptimizationAcknowledged()
                    },
                    onSkip = { viewModel.onBatteryOptimizationAcknowledged() },
                )

                OnboardingStep.Done -> DoneStep(
                    onContinue = {
                        onOnboardingComplete()
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step sub-composables
// ---------------------------------------------------------------------------

private enum class OnboardingStep {
    SmsRole, DialerRole, Permissions, Battery, Done
}

@Composable
private fun SmsRoleStep(onRequest: () -> Unit) {
    StepContent(
        title = "Set as Default SMS App",
        body = "Agendroid needs to be your default SMS app so it can read, send, " +
            "and manage your messages on your behalf.",
        primaryLabel = "Set as Default SMS App",
        onPrimary = onRequest,
    )
}

@Composable
private fun DialerRoleStep(onRequest: () -> Unit) {
    StepContent(
        title = "Set as Default Phone App",
        body = "Agendroid needs to be your default phone app so it can screen " +
            "calls and assist with call management.",
        primaryLabel = "Set as Default Phone App",
        onPrimary = onRequest,
    )
}

@Composable
private fun PermissionsStep(onRequest: () -> Unit) {
    StepContent(
        title = "Grant Permissions",
        body = "Agendroid needs access to your microphone, contacts, call log, " +
            "and SMS messages to work as your AI assistant.",
        primaryLabel = "Grant Permissions",
        onPrimary = onRequest,
    )
}

@Composable
private fun BatteryStep(onRequest: () -> Unit, onSkip: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Disable Battery Optimization",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "For reliable background processing (auto-replies, call screening) " +
                "please exempt Agendroid from battery optimization.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onRequest) {
            Text("Open Battery Settings")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSkip) {
            Text("Skip")
        }
    }
}

@Composable
private fun DoneStep(onContinue: () -> Unit) {
    StepContent(
        title = "You're All Set!",
        body = "Agendroid is ready. Tap Continue to get started.",
        primaryLabel = "Continue",
        onPrimary = onContinue,
    )
}

@Composable
private fun StepContent(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onPrimary,
    ) {
        Text(primaryLabel)
    }
}
