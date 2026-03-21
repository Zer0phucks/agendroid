// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeDialerActivity.kt
package com.agendroid.spike.callpipeline.phase2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stub dialer activity — exists only so this app is eligible for the ROLE_DIALER role. */
class SpikeDialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Agendroid Spike Dialer", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Stub — spike testing only", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { finish() }) { Text("Back") }
                    }
                }
            }
        }
    }
}
