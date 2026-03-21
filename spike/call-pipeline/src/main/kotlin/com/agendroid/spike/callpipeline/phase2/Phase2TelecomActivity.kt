// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/Phase2TelecomActivity.kt
package com.agendroid.spike.callpipeline.phase2

import android.app.role.RoleManager
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendroid.spike.callpipeline.measurement.BatteryMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint
class Phase2TelecomActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roleManager = getSystemService(RoleManager::class.java)
        setContent {
            MaterialTheme {
                Phase2Screen(scope, applicationContext, roleManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

@Composable
private fun Phase2Screen(
    scope: CoroutineScope,
    context: android.content.Context,
    roleManager: RoleManager,
) {
    var takeoverMs by remember { mutableStateOf<Long?>( null) }
    var log by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableStateOf(-1) }
    var isDefaultDialer by remember {
        mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_DIALER))
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultDialer = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    LaunchedEffect(Unit) {
        val monitor = BatteryMonitor(context)
        while (true) {
            batteryLevel = monitor.currentLevel()
            delay(10_000L)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Phase 2: Telecom Audio Routing", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (batteryLevel >= 0) {
            Text(
                "Current battery: $batteryLevel%  (record before + after 10-min call — gate: ≤8% drain)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (isDefaultDialer) {
            Text("✅ Default phone app: SET", style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(
                onClick = {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    roleLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set as Default Phone App")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                appendLine("Instructions:")
                appendLine()
                appendLine("1. Tap button above to set this app as the default phone app.")
                appendLine()
                appendLine("2. Before making the test call, note the battery % shown above.")
                appendLine("   After a 10-minute call, check the % again. Gate: ≤8% drain.")
                appendLine()
                appendLine("3. Have a second phone call this device's number.")
                appendLine()
                appendLine("4. The SpikeInCallService will answer automatically and start")
                appendLine("   capturing + echoing audio. You should hear your own voice")
                appendLine("   echoed back — this confirms duplex audio routing works.")
                appendLine()
                appendLine("5. Watch adb logcat for:")
                appendLine("   • 'SpikeCallScreening: Call intercepted' — confirms CallScreeningService works")
                appendLine("   • 'SpikeInCallService: First audio frame captured' — confirms AudioRecord works")
                appendLine("   NOTE: The 'Measure Takeover' button below is a lower-bound estimate.")
                appendLine("   For the authoritative ≤200ms gate, read the logcat output from a live call.")
                appendLine()
                appendLine("6. Tap 'Measure Takeover' to benchmark the AI→user handoff time.")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    val ms = measureTakeoverHandoff(context)
                    takeoverMs = ms
                    log = "Takeover handoff: ${ms}ms  (gate: ≤200ms)  ${if (ms <= 200) "✅ PASS" else "❌ FAIL"}"
                    Log.d("Phase2", "Take-over handoff complete in ${ms}ms")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Measure Takeover (gate: ≤200ms)")
        }
        if (log.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(log, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                appendLine("Record results:")
                appendLine("  CallScreeningService intercepted: YES / NO")
                appendLine("  AudioRecord captured frames:      YES / NO")
                appendLine("  AudioTrack played back:           YES / NO")
                appendLine("  Take-over handoff time:           ${takeoverMs ?: "___"} ms  (gate: ≤200ms)")
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Measures the time to stop audio capture and switch AudioManager mode to IN_CALL.
 *  This simulates the AI→user handoff without requiring a live call.
 *  A live call measurement can be done via adb logcat from SpikeInCallService.
 *
 * NOTE: Uses AudioSource.MIC in normal audio mode (not VOICE_COMMUNICATION in telephony mode).
 * This measures a lower-bound estimate only. For the authoritative gate measurement,
 * use a live call and read 'Take-over handoff complete in Xms' from logcat (SpikeInCallService). */
private suspend fun measureTakeoverHandoff(context: android.content.Context): Long = withContext(Dispatchers.IO) {
    val sampleRate = 16_000
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )

    // Start a brief AudioRecord session to simulate what SpikeInCallService does
    val audioRecord = try {
        AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 4,
        ).also { it.startRecording() }
    } catch (e: Exception) {
        Log.w("Phase2", "AudioRecord failed (expected without RECORD_AUDIO grant at runtime): $e")
        null
    }

    val startMs = System.currentTimeMillis()

    // Measure: stop recording + switch audio mode
    audioRecord?.stop()
    audioRecord?.release()

    val am = context.getSystemService(AudioManager::class.java)
    am.mode = AudioManager.MODE_IN_CALL
    am.isSpeakerphoneOn = false

    val elapsed = System.currentTimeMillis() - startMs

    // Reset audio mode
    am.mode = AudioManager.MODE_NORMAL

    elapsed
}
