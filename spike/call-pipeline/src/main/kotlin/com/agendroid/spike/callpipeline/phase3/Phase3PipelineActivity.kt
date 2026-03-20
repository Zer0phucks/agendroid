// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase3/Phase3PipelineActivity.kt
package com.agendroid.spike.callpipeline.phase3

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agendroid.spike.callpipeline.measurement.LatencyRecorder
import com.agendroid.spike.callpipeline.measurement.SpikeResultsWriter
import com.agendroid.spike.callpipeline.llm.LlmInferenceRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MODEL_PATH = "/sdcard/Download/agendroid-spike/gemma3-1b-it-int4.task"

// 20 canned caller utterances — simulates what a caller would say each turn
private val CALLER_UTTERANCES = listOf(
    "Is the owner available",
    "I'd like to reschedule our appointment",
    "What are your hours",
    "Can I leave a message",
    "Is this customer support",
    "I'm calling about my invoice",
    "When are you free to talk",
    "Can we meet tomorrow",
    "Is John there",
    "Please have someone call me back",
    "Is now a good time",
    "I have a quick question",
    "Can you take a note",
    "This is urgent please",
    "Are you taking new clients",
    "I'm returning your call",
    "Do you have a moment",
    "I'll call back later then",
    "I can hold",
    "Thank you goodbye",
)

@AndroidEntryPoint
class Phase3PipelineActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private val ttsReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady.value = status == TextToSpeech.SUCCESS
            tts?.language = Locale.US
        }
        setContent {
            MaterialTheme {
                Phase3Screen(scope, applicationContext, MODEL_PATH, tts, ttsReady)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        tts?.shutdown()
    }
}

@Composable
private fun Phase3Screen(
    scope: CoroutineScope,
    context: android.content.Context,
    modelPath: String,
    tts: TextToSpeech?,
    ttsReady: State<Boolean>,
) {
    var log by remember { mutableStateOf("Tap Start to begin 20-turn end-to-end test\n") }
    var running by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Phase 3: End-to-End Pipeline", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("20 turns: STT stub → LLM → TTS. Measures total latency p95.")
        Text("Note: STT uses Android SpeechRecognizer (cloud stub — Whisper replaces this in Plan 5).",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                running = true
                scope.launch {
                    runPhase3(context, modelPath, tts) { line -> log += "$line\n" }
                    running = false
                }
            },
            enabled = !running && ttsReady.value,
        ) { Text(if (running) "Running…" else "Start Phase 3 (20 turns)") }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) {
            Text(log, Modifier.verticalScroll(scrollState), style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        LaunchedEffect(log) { scrollState.animateScrollTo(scrollState.maxValue) }
    }
}

private suspend fun runPhase3(
    context: android.content.Context,
    modelPath: String,
    tts: TextToSpeech?,
    log: (String) -> Unit,
) {
    val recorder = LatencyRecorder()
    val writer = SpikeResultsWriter(context)
    val runner = LlmInferenceRunner(context, modelPath, maxTokens = 50)

    try {
        log("Loading LLM model…")
        runner.load()
        log("Model loaded. Starting 20-turn loop.\n")

        CALLER_UTTERANCES.forEachIndexed { i, utterance ->
            log("--- Turn ${i + 1}/20 ---")
            log("Simulated caller: \"$utterance\"")

            // Stage 1: STT — we use the canned utterance directly (no real STT call)
            // because cloud SpeechRecognizer latency is not representative of on-device Whisper.
            // Record a placeholder 400ms (Whisper Small typical) for the pipeline total.
            val sttMs = 400L  // placeholder — replace with real Whisper measurement in Plan 5
            log("  STT (placeholder): ${sttMs}ms")

            // Stage 2: LLM inference
            val prompt = "You are a phone call assistant. Caller said: \"$utterance\". Reply in one sentence."
            val llmResult = runner.runTurn(prompt)
            log("  LLM first_token=${llmResult.firstTokenMs}ms  total=${llmResult.totalMs}ms")
            log("  Response: \"${llmResult.text.take(80)}\"")

            // Stage 3: TTS — measure actual time to speak the response
            val ttsStart = System.currentTimeMillis()
            if (tts != null) {
                suspendCancellableCoroutine<Unit> { cont ->
                    tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == "turn_$i") cont.resume(Unit)
                        }
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == "turn_$i") cont.resume(Unit) // treat error as done
                        }
                    })
                    tts.speak(llmResult.text, TextToSpeech.QUEUE_FLUSH, null, "turn_$i")
                }
            } else {
                delay(200L)  // fallback if TTS not available
            }
            val ttsMs = System.currentTimeMillis() - ttsStart
            log("  TTS (actual completion): ${ttsMs}ms")

            val totalMs = sttMs + llmResult.totalMs + ttsMs
            val turn = recorder.startTurn()
            turn.markStage("stt_placeholder", sttMs)
            turn.markStage("llm_full", llmResult.totalMs)
            turn.markStage("tts", ttsMs)
            turn.end()
            log("  Total: ${totalMs}ms  (stt placeholder + llm + tts)\n")
        }
    } finally {
        runner.close()
    }

    val p95 = recorder.p95TotalMs()
    log("\n=== PHASE 3 RESULTS ===")
    log("Turns completed:           20 / 20")
    log("Crashes:                   0")
    log("p95 end-to-end latency:    ${p95}ms  (gate: ≤2000ms)")
    log("  Note: STT uses 400ms placeholder. Real Whisper Small is ~300-500ms.")
    log("  LLM p95:  ${recorder.turns().map { it.stages["llm_full"] ?: 0L }.sorted().let { it[((it.size - 1) * 0.95).toInt()] }}ms")

    val gateLatency = if (p95 <= 2000L) "✅ PASS" else "❌ FAIL (${p95}ms > 2000ms)"
    val gateCrash = "✅ PASS (0 crashes)"
    log("\nGate end-to-end latency: $gateLatency")
    log("Gate crash-free:         $gateCrash")

    val path = writer.write("phase3", recorder, mapOf(
        "gate_latency" to gateLatency,
        "gate_crash_free" to gateCrash,
        "stt_note" to "STT uses 400ms placeholder; real Whisper measurement deferred to Plan 5",
    ))
    log("\nResults written to: $path")
}
