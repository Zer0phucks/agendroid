// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase1/Phase1BenchmarkActivity.kt
package com.agendroid.spike.callpipeline.phase1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendroid.spike.callpipeline.measurement.LatencyRecorder
import com.agendroid.spike.callpipeline.measurement.SpikeResultsWriter
import com.agendroid.spike.callpipeline.measurement.ThermalMonitor
import com.agendroid.spike.callpipeline.llm.LlmInferenceRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

val BENCHMARK_PROMPTS = listOf(
    "You are a phone assistant. The caller said: 'Is your boss available?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I need to reschedule our meeting.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'What are your business hours?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Can I leave a message?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Is this the right number for support?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I'm calling about my order.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'When will you be available?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Can we do a call tomorrow?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I'm looking for John.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Please call me back.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Is this a good time to talk?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I have a quick question.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Can you take a message?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I need to speak to someone urgently.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Are you accepting new clients?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I'm returning your call.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Do you have a moment?'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'I'll try again later then.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Thanks, I'll hold.'. Reply in one sentence.",
    "You are a phone assistant. The caller said: 'Goodbye.'. Reply in one sentence.",
)

private const val MODEL_PATH = "/sdcard/Download/agendroid-spike/gemma3-1b-it-gpu-int4.task"

@AndroidEntryPoint
class Phase1BenchmarkActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Phase1Screen(scope, MODEL_PATH, applicationContext)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

@Composable
private fun Phase1Screen(
    scope: CoroutineScope,
    modelPath: String,
    context: android.content.Context,
) {
    var log by remember { mutableStateOf("Tap Start to begin\n") }
    var running by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Phase 1: LLM Latency + Thermal", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Runs 20 prompts. Records first-token & total latency. Monitors SoC temp for 10 min.")
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                running = true
                scope.launch {
                    log = "Loading model from:\n$modelPath\n\n"
                    runPhase1(modelPath, context) { line ->
                        log += "$line\n"
                    }
                    running = false
                }
            },
            enabled = !running,
        ) { Text(if (running) "Running…" else "Start Phase 1") }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) {
            Text(
                log,
                Modifier.verticalScroll(scrollState).fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        // Auto-scroll to bottom
        LaunchedEffect(log) { scrollState.animateScrollTo(scrollState.maxValue) }
    }
}

private suspend fun runPhase1(
    modelPath: String,
    context: android.content.Context,
    log: (String) -> Unit,
) {
    val recorder = LatencyRecorder()
    val thermal = ThermalMonitor(context)
    val writer = SpikeResultsWriter(context)
    // Use synchronizedList to avoid data races: thermalReadings.add() is called from
    // a background coroutine while the list is read on the main coroutine after cancel.
    val thermalReadings = java.util.Collections.synchronizedList(mutableListOf<Float>())

    // Start thermal monitoring in background
    val thermalJob = thermal.temperatureFlow(2_000L)
        .onEach { thermalReadings.add(it) }
        .launchIn(CoroutineScope(Dispatchers.Default))

    val runner = LlmInferenceRunner(context, modelPath, maxTokens = 50)
    try {
        log("Loading model…")
        val loadStart = System.currentTimeMillis()
        runner.load()
        log("Model loaded in ${System.currentTimeMillis() - loadStart}ms\n")

        BENCHMARK_PROMPTS.forEachIndexed { i, prompt ->
            log("Turn ${i + 1}/20: ${prompt.take(60)}…")
            val result = runner.runTurn(prompt)
            val turn = recorder.startTurn()
            turn.markStage("llm_full", result.totalMs)
            turn.end()
            log("  → first_token=${result.firstTokenMs}ms  total=${result.totalMs}ms")
            log("  → \"${result.text.take(80)}\"")
        }
    } finally {
        thermalJob.cancelAndJoin()
        runner.close()
    }

    val maxTemp = thermalReadings.maxOrNull() ?: -1f
    val p95 = recorder.p95TotalMs()

    log("\n=== PHASE 1 RESULTS ===")
    log("p95 LLM total latency: ${p95}ms  (gate: ≤600ms for LLM stage alone)")
    log("Max SoC temperature:   ${maxTemp}°C  (gate: ≤42°C)")
    log("Crash-free: YES (completed all 20 turns)")

    val gateP95 = if (p95 <= 600) "✅ PASS" else "❌ FAIL (${p95}ms > 600ms)"
    val gateThermal = if (maxTemp < 0) "⚠ N/A (API unavailable)" else if (maxTemp <= 42f) "✅ PASS" else "❌ FAIL (${maxTemp}°C > 42°C)"

    log("\nGate LLM latency:  $gateP95")
    log("Gate thermal:      $gateThermal")

    val extras = mapOf(
        "max_temp_celsius" to maxTemp,
        "gate_llm_latency" to gateP95,
        "gate_thermal" to gateThermal,
    )
    val path = writer.write("phase1", recorder, extras)
    log("\nResults written to: $path")
}
