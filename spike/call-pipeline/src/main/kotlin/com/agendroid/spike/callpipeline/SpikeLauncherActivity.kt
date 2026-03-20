// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/SpikeLauncherActivity.kt
package com.agendroid.spike.callpipeline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendroid.spike.callpipeline.phase1.Phase1BenchmarkActivity
import com.agendroid.spike.callpipeline.phase2.Phase2TelecomActivity
import com.agendroid.spike.callpipeline.phase3.Phase3PipelineActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SpikeLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SpikeLauncherScreen(
                    onPhase1 = { startActivity(Intent(this, Phase1BenchmarkActivity::class.java)) },
                    onPhase2 = { startActivity(Intent(this, Phase2TelecomActivity::class.java)) },
                    onPhase3 = { startActivity(Intent(this, Phase3PipelineActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
private fun SpikeLauncherScreen(onPhase1: () -> Unit, onPhase2: () -> Unit, onPhase3: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Agendroid Call Pipeline Spike", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPhase1, Modifier.fillMaxWidth()) {
            Text("Phase 1: LLM Latency + Thermal (no call needed)")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onPhase2, Modifier.fillMaxWidth()) {
            Text("Phase 2: Telecom Audio Routing (make a test call first)")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onPhase3, Modifier.fillMaxWidth()) {
            Text("Phase 3: End-to-End Pipeline (20-turn test)")
        }
    }
}
