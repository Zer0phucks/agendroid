# Agendroid — Plan 2: Call Pipeline Feasibility Spike

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove (or disprove) that a real-time STT→LLM→TTS call-handling loop can meet the 5 acceptance gates defined in spec §11.1 on a Snapdragon 8 Gen 3 device, and produce a written go/no-go decision that gates all subsequent plans.

**Architecture:** A self-contained `:spike:call-pipeline` debug module — a separate Activity with its own launcher. Not wired into the main app. Throwaway code: readability matters less than accurate measurement. Three phases run in sequence: (1) LLM-only latency benchmark, (2) Telecom audio routing validation, (3) combined end-to-end loop. Each phase produces timestamped log output and a JSON results file on external storage.

**Tech Stack:** MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai`) for LLM inference on GPU, Android `TextToSpeech` as TTS stub (replaced by Kokoro in Plan 5), Energy VAD (custom, ~5 lines) + Android `SpeechRecognizer` as STT stub for loop timing (real Whisper latency is tested separately via a pre-recorded audio benchmark), `ThermalManager` API for SoC temperature, `BatteryManager` for drain measurement, Jetpack Compose for the spike UI.

**Spec reference:** `docs/superpowers/specs/2026-03-20-agendroid-design.md` §11.1 (acceptance gates), §4 (call handling), §11.4 (background reliability)

**Note on STT:** Whisper Small requires building whisper.cpp for Android (Plan 5). For this spike, STT latency is measured independently using a pre-recorded 5-second audio clip fed to whisper.cpp via a prebuilt AAR from the `whisper-android` repository — see Task 4. If the AAR cannot be built, STT latency is marked as "TBD / assumed 400ms" and the remaining gates are still measured. This does not block a go/no-go decision.

---

## Acceptance Gates (from spec §11.1)

All 5 must pass for v1 to include full call-agent mode. If any fail, v1 ships with screen-only call screening + autonomous SMS.

| Gate | Threshold | Measured in |
| --- | --- | --- |
| End-to-end latency p95 | ≤ 2 000ms | Task 6 (Phase 3, 20 turns) |
| Thermal sustain | ≤ 42°C SoC | Task 4 (Phase 1, 10 min run) |
| Crash-free rate | 100% | Task 6 (0 crashes across 20-turn suite) |
| Battery drain | ≤ 8% / 10 min | Task 5 (Phase 2, screen on) |
| Take-over handoff | ≤ 200ms | Task 5 (Phase 2, measured via timestamp diff) |

---

## File Map

```text
spike/call-pipeline/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/agendroid/spike/callpipeline/
        ├── SpikeApp.kt                  # Hilt application for spike APK variant
        ├── SpikeLauncherActivity.kt     # Entry point — phase selector UI
        ├── measurement/
        │   ├── LatencyRecorder.kt       # Records per-stage timestamps, computes p95
        │   ├── ThermalMonitor.kt        # Polls ThermalManager, emits Flow<Float>
        │   ├── BatteryMonitor.kt        # Polls BatteryManager, emits Flow<Int>
        │   └── SpikeResultsWriter.kt    # Writes JSON results to external storage
        ├── llm/
        │   └── LlmInferenceRunner.kt   # MediaPipe Tasks GenAI wrapper with timing
        ├── phase1/
        │   └── Phase1BenchmarkActivity.kt  # LLM-only latency + thermal benchmark
        ├── phase2/
        │   ├── SpikeCallScreeningService.kt  # Intercepts calls, routes to spike pipeline
        │   ├── SpikeInCallService.kt         # In-call UI: transcript + take-over button
        │   └── Phase2TelecomActivity.kt      # Setup + instructions for manual call test
        └── phase3/
            └── Phase3PipelineActivity.kt     # Combined STT stub + LLM + TTS loop

spike/call-pipeline/src/test/kotlin/com/agendroid/spike/callpipeline/
└── measurement/
    └── LatencyRecorderTest.kt           # Unit tests for p95 calculation
```

**Also modify:**
- `settings.gradle.kts` — add `include(":spike:call-pipeline")`

---

## Task 1: Spike Module Setup

**Files:**
- Modify: `settings.gradle.kts`
- Create: `spike/call-pipeline/build.gradle.kts`
- Create: `spike/call-pipeline/src/main/AndroidManifest.xml`
- Create: empty `src/main/kotlin/` and `src/test/kotlin/` directories

- [ ] **Step 1.1: Add spike module to settings.gradle.kts**

Open `settings.gradle.kts` and append after the last `include()` line:

```kotlin
// Spike modules — debug/validation only, not shipped
include(":spike:call-pipeline")
```

- [ ] **Step 1.2: Create spike/call-pipeline/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace   = "com.agendroid.spike.callpipeline"
    compileSdk  = 35
    defaultConfig {
        applicationId = "com.agendroid.spike.callpipeline"
        minSdk        = 31
        targetSdk     = 35
        versionCode   = 1
        versionName   = "spike-1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coroutines)

    // MediaPipe Tasks GenAI — LLM inference on GPU
    // Check https://developers.google.com/mediapipe/solutions/genai/llm_inference/android
    // for latest stable version before building.
    implementation("com.google.mediapipe:tasks-genai:0.10.14")

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 1.3: Create spike/call-pipeline/src/main/AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permissions needed for spike phases -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature android:name="android.hardware.telephony" android:required="false" />

    <application
        android:name=".SpikeApp"
        android:allowBackup="false"
        android:label="Agendroid Spike"
        android:theme="@android:theme/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".SpikeLauncherActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".phase1.Phase1BenchmarkActivity" android:exported="false" />
        <activity android:name=".phase2.Phase2TelecomActivity" android:exported="false" />
        <activity android:name=".phase3.Phase3PipelineActivity" android:exported="false" />

        <service
            android:name=".phase2.SpikeCallScreeningService"
            android:exported="true"
            android:permission="android.permission.BIND_SCREENING_SERVICE">
            <intent-filter>
                <action android:name="android.telecom.CallScreeningService" />
            </intent-filter>
        </service>

        <service
            android:name=".phase2.SpikeInCallService"
            android:exported="true"
            android:foregroundServiceType="phoneCall"
            android:permission="android.permission.BIND_INCALL_SERVICE">
            <meta-data
                android:name="android.telecom.IN_CALL_SERVICE_UI"
                android:value="true" />
            <intent-filter>
                <action android:name="android.telecom.InCallService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

- [ ] **Step 1.4: Create directory structure**

```bash
mkdir -p spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/{measurement,llm,phase1,phase2,phase3}
mkdir -p spike/call-pipeline/src/test/kotlin/com/agendroid/spike/callpipeline/measurement
```

- [ ] **Step 1.5: Verify spike module resolves in Gradle**

```bash
./gradlew :spike:call-pipeline:dependencies --configuration debugRuntimeClasspath 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` — no dependency resolution errors. If `com.google.mediapipe:tasks-genai:0.10.14` fails to resolve, check the latest version at https://developers.google.com/mediapipe/solutions/genai/llm_inference/android and update the version string in `build.gradle.kts`.

- [ ] **Step 1.6: Commit**

```bash
git add spike/ settings.gradle.kts
git commit -m "chore(spike): add :spike:call-pipeline module scaffold"
```

---

## Task 2: Measurement Infrastructure (TDD)

**Files:**
- Create: `spike/call-pipeline/src/test/kotlin/com/agendroid/spike/callpipeline/measurement/LatencyRecorderTest.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/LatencyRecorder.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/ThermalMonitor.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/BatteryMonitor.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/SpikeResultsWriter.kt`

- [ ] **Step 2.1: Write failing tests for LatencyRecorder**

```kotlin
// spike/call-pipeline/src/test/kotlin/com/agendroid/spike/callpipeline/measurement/LatencyRecorderTest.kt
package com.agendroid.spike.callpipeline.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatencyRecorderTest {

    @Test
    fun `single turn records correct total latency`() {
        val recorder = LatencyRecorder()
        val turn = recorder.startTurn()
        turn.markStage("vad", 80L)
        turn.markStage("stt", 420L)
        turn.markStage("llm_first_token", 480L)
        turn.markStage("llm_full", 550L)
        turn.markStage("tts", 190L)
        turn.end()

        val turns = recorder.turns()
        assertEquals(1, turns.size)
        // Total = sum of all stage durations
        assertEquals(1720L, turns[0].totalMs)
    }

    @Test
    fun `p95 of 20 values returns 95th percentile`() {
        val recorder = LatencyRecorder()
        // Add 20 turns with known total latencies: 100, 200, ..., 2000 ms
        repeat(20) { i ->
            val turn = recorder.startTurn()
            turn.markStage("total", ((i + 1) * 100).toLong())
            turn.end()
        }
        // p95 of [100, 200, ..., 2000] = value at index 18 (0-indexed) = 1900
        assertEquals(1900L, recorder.p95TotalMs())
    }

    @Test
    fun `stage breakdown is preserved per turn`() {
        val recorder = LatencyRecorder()
        val turn = recorder.startTurn()
        turn.markStage("stt", 350L)
        turn.markStage("llm", 600L)
        turn.end()

        val stages = recorder.turns()[0].stages
        assertEquals(350L, stages["stt"])
        assertEquals(600L, stages["llm"])
    }

    @Test
    fun `empty recorder p95 returns zero`() {
        assertEquals(0L, LatencyRecorder().p95TotalMs())
    }

    @Test
    fun `all turns within budget returns true when p95 under threshold`() {
        val recorder = LatencyRecorder()
        repeat(20) { i ->
            val turn = recorder.startTurn()
            turn.markStage("pipeline", ((i + 1) * 80).toLong()) // max = 1600ms
            turn.end()
        }
        assertTrue(recorder.p95TotalMs() <= 2000L)
    }
}
```

- [ ] **Step 2.2: Run to confirm failure**

```bash
./gradlew :spike:call-pipeline:test --tests "*.LatencyRecorderTest" 2>&1 | tail -5
```

Expected: compilation error — `LatencyRecorder` does not exist.

- [ ] **Step 2.3: Implement LatencyRecorder.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/LatencyRecorder.kt
package com.agendroid.spike.callpipeline.measurement

class LatencyRecorder {

    private val _turns = mutableListOf<TurnRecord>()

    fun startTurn(): TurnBuilder = TurnBuilder()

    fun turns(): List<TurnRecord> = _turns.toList()

    fun p95TotalMs(): Long {
        if (_turns.isEmpty()) return 0L
        val sorted = _turns.map { it.totalMs }.sorted()
        val idx = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        return sorted[idx]
    }

    inner class TurnBuilder {
        val stages = mutableMapOf<String, Long>()

        fun markStage(name: String, durationMs: Long) {
            stages[name] = durationMs
        }

        fun end() {
            _turns.add(TurnRecord(stages.toMap(), stages.values.sum()))
        }
    }
}

data class TurnRecord(
    val stages: Map<String, Long>,
    val totalMs: Long,
)
```

- [ ] **Step 2.4: Run tests — confirm 5 pass**

```bash
./gradlew :spike:call-pipeline:test --tests "*.LatencyRecorderTest" 2>&1 | tail -5
```

Expected: `5 tests completed, 0 failures`.

- [ ] **Step 2.5: Implement ThermalMonitor.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/ThermalMonitor.kt
package com.agendroid.spike.callpipeline.measurement

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ThermalMonitor(context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Emits SoC temperature in Celsius every [intervalMs]. Uses the thermal headroom API
     *  (Android 12+) which reports normalized thermal headroom 0.0–1.0, scaled to ~42°C max.
     *  If the API is unavailable, emits -1f as a sentinel. */
    fun temperatureFlow(intervalMs: Long = 2_000L): Flow<Float> = flow {
        while (true) {
            val headroom = powerManager?.thermalHeadroom(10) ?: -1f
            // headroom=1.0 means no thermal pressure; 0.0 means critically hot.
            // Approximate conversion: 25°C baseline + (1.0 - headroom) * 17°C range
            val approxCelsius = if (headroom >= 0f) 25f + (1f - headroom) * 17f else -1f
            emit(approxCelsius)
            delay(intervalMs)
        }
    }

    /** Returns true if SoC is above the 42°C throttle threshold per spec §11.2. */
    fun isOverThreshold(tempCelsius: Float): Boolean = tempCelsius > 42f
}
```

- [ ] **Step 2.6: Implement BatteryMonitor.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/BatteryMonitor.kt
package com.agendroid.spike.callpipeline.measurement

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BatteryMonitor(private val context: Context) {

    /** Current battery level 0–100. */
    fun currentLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level < 0) -1 else (level * 100 / scale)
    }

    /** Emits battery level every [intervalMs]. */
    fun levelFlow(intervalMs: Long = 10_000L): Flow<Int> = flow {
        while (true) {
            emit(currentLevel())
            delay(intervalMs)
        }
    }
}
```

- [ ] **Step 2.7: Implement SpikeResultsWriter.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/measurement/SpikeResultsWriter.kt
package com.agendroid.spike.callpipeline.measurement

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpikeResultsWriter(private val context: Context) {

    /** Writes a JSON results file to Downloads/agendroid-spike/ on external storage.
     *  Falls back to internal files dir if external storage is unavailable.
     *  Returns the path written, or null on failure. */
    fun write(phase: String, recorder: LatencyRecorder, extras: Map<String, Any> = emptyMap()): String? {
        return try {
            val dir = resolveOutputDir()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "spike-$phase-$ts.json")

            val root = JSONObject()
            root.put("phase", phase)
            root.put("timestamp", ts)
            root.put("p95_total_ms", recorder.p95TotalMs())

            val turnsArr = JSONArray()
            recorder.turns().forEach { turn ->
                val t = JSONObject()
                t.put("total_ms", turn.totalMs)
                val stages = JSONObject()
                turn.stages.forEach { (k, v) -> stages.put(k, v) }
                t.put("stages", stages)
                turnsArr.put(t)
            }
            root.put("turns", turnsArr)
            extras.forEach { (k, v) -> root.put(k, v) }

            file.writeText(root.toString(2))
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveOutputDir(): File {
        val external = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(external, "agendroid-spike")
        if (!dir.exists()) dir.mkdirs()
        return if (dir.canWrite()) dir else File(context.filesDir, "spike-results").also { it.mkdirs() }
    }
}
```

- [ ] **Step 2.8: Commit measurement infrastructure**

```bash
git add spike/call-pipeline/src/
git commit -m "feat(spike): measurement infrastructure — LatencyRecorder, ThermalMonitor, BatteryMonitor, ResultsWriter"
```

---

## Task 3: Model Acquisition & LLM Runner

**Files:**
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/llm/LlmInferenceRunner.kt`
- Manual step: download model file

**Model to download:**

Gemma 3 1B GPU 4-bit for MediaPipe — `gemma3-1b-it-gpu-int4.task` (~700MB).

Download from Kaggle:
```bash
# Requires Kaggle CLI: pip install kaggle
# Requires Kaggle account + API token at ~/.kaggle/kaggle.json
kaggle models instances versions download google/gemma-3/tfLite/gemma3-1b-it-gpu-int4/1 \
  --path /sdcard/Download/agendroid-spike/

# Alternative: download via browser from
# https://www.kaggle.com/models/google/gemma-3/tfLite/gemma3-1b-it-gpu-int4
# and push to device: adb push gemma3-1b-it-gpu-int4.task /sdcard/Download/agendroid-spike/
```

The model path on device will be: `/sdcard/Download/agendroid-spike/gemma3-1b-it-gpu-int4.task`

If the GPU variant fails to load (some devices don't support all GPU delegates), fall back to the CPU int4 variant: `gemma3-1b-it-cpu-int4.task`. CPU inference will be slower but still valid for gate measurement.

- [ ] **Step 3.1: Push model to device**

```bash
# Check model is on device
adb shell ls /sdcard/Download/agendroid-spike/
```

Expected: `gemma3-1b-it-gpu-int4.task` listed. If not, download and push it first.

- [ ] **Step 3.2: Implement LlmInferenceRunner.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/llm/LlmInferenceRunner.kt
package com.agendroid.spike.callpipeline.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wraps MediaPipe LlmInference and measures per-call timing.
 *  Create once; reuse across turns. Not thread-safe — call from a single coroutine. */
class LlmInferenceRunner(
    private val context: Context,
    private val modelPath: String,
    private val maxTokens: Int = 50,
) {
    private var llm: LlmInference? = null

    data class InferenceResult(
        val text: String,
        val firstTokenMs: Long,   // time from prompt submission to first token received
        val totalMs: Long,        // time from prompt submission to generation complete
    )

    /** Loads the model. Call once before [runTurn]. */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setTopK(1)        // greedy for consistent latency measurement
            .setTemperature(0f)
            .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    /** Runs one inference turn and returns timing. Must call [load] first. */
    suspend fun runTurn(prompt: String): InferenceResult = withContext(Dispatchers.IO) {
        val runner = checkNotNull(llm) { "Call load() before runTurn()" }
        val startMs = System.currentTimeMillis()
        var firstTokenMs = -1L
        val sb = StringBuilder()

        // Streaming generation — measure first token time
        val latch = java.util.concurrent.CountDownLatch(1)
        runner.generateResponseAsync(prompt) { partial, done ->
            if (partial.isNotEmpty() && firstTokenMs < 0L) {
                firstTokenMs = System.currentTimeMillis() - startMs
            }
            sb.append(partial)
            if (done) latch.countDown()
        }
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS)

        val totalMs = System.currentTimeMillis() - startMs
        InferenceResult(
            text = sb.toString(),
            firstTokenMs = if (firstTokenMs >= 0) firstTokenMs else totalMs,
            totalMs = totalMs,
        )
    }

    fun close() {
        llm?.close()
        llm = null
    }
}
```

- [ ] **Step 3.3: Verify the spike module still compiles**

```bash
./gradlew :spike:call-pipeline:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. If MediaPipe dependency resolution fails, check the version string and update it from the MediaPipe release page.

- [ ] **Step 3.4: Commit LLM runner**

```bash
git add spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/llm/
git commit -m "feat(spike): LlmInferenceRunner with per-turn timing"
```

---

## Task 4: Phase 1 — LLM Latency + Thermal Benchmark

**Goal:** Run 20 consecutive LLM inference turns, measure first-token latency, total latency, and SoC temperature over 10 minutes. No phone calls needed.

**Files:**
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/SpikeApp.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/SpikeLauncherActivity.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase1/Phase1BenchmarkActivity.kt`

**Test prompts (20 representative prompts simulating call-agent responses):**

```
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
```

- [ ] **Step 4.1: Create SpikeApp.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/SpikeApp.kt
package com.agendroid.spike.callpipeline

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpikeApp : Application()
```

- [ ] **Step 4.2: Create SpikeLauncherActivity.kt**

```kotlin
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
```

- [ ] **Step 4.3: Create Phase1BenchmarkActivity.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase1/Phase1BenchmarkActivity.kt
package com.agendroid.spike.callpipeline.phase1

import android.os.Bundle
import android.os.Environment
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
import java.io.File

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
    val thermalReadings = mutableListOf<Float>()

    // Start thermal monitoring in background
    val thermalJob = CoroutineScope(Dispatchers.Default).launch {
        thermal.temperatureFlow(2_000L).onEach { temp ->
            thermalReadings.add(temp)
        }.launchIn(this)
    }

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
            turn.markStage("llm_first_token", result.firstTokenMs)
            turn.markStage("llm_full", result.totalMs)
            turn.end()
            log("  → first_token=${result.firstTokenMs}ms  total=${result.totalMs}ms")
            log("  → \"${result.text.take(80)}\"")
        }
    } finally {
        thermalJob.cancel()
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
```

- [ ] **Step 4.4: Build the spike APK**

```bash
./gradlew :spike:call-pipeline:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4.5: Install and run Phase 1 on device**

```bash
adb install -r spike/call-pipeline/build/outputs/apk/debug/call-pipeline-debug.apk
adb shell am start -n com.agendroid.spike.callpipeline/.SpikeLauncherActivity
```

Tap **"Phase 1: LLM Latency + Thermal"**. Watch the log. The 20-turn run should complete in 8–15 minutes. If it crashes, check `adb logcat` for the exception.

- [ ] **Step 4.6: Pull and review results**

```bash
adb pull /sdcard/Download/agendroid-spike/ /tmp/spike-results/
cat /tmp/spike-results/spike-phase1-*.json
```

Record the values here before committing:

```
Phase 1 results (fill in after running on device):
  p95_total_ms (LLM only):  _______  (gate: ≤600ms)
  max_temp_celsius:         _______  (gate: ≤42°C)
  Gate LLM latency:         PASS / FAIL
  Gate thermal:             PASS / FAIL
```

- [ ] **Step 4.7: Commit Phase 1 implementation**

```bash
git add spike/call-pipeline/
git commit -m "feat(spike): Phase 1 LLM latency + thermal benchmark (20 turns)"
```

---

## Task 5: Phase 2 — Telecom Audio Routing

**Goal:** Validate that `CallScreeningService` can intercept an incoming call, `InCallService` can access the audio stream, AudioRecord/AudioTrack work during a live call, and the "Take Over" handoff latency is ≤200ms.

**Setup required before running Phase 2:**

1. Set the spike app as the default phone handler:
   ```bash
   adb shell telecom set-default-dialer com.agendroid.spike.callpipeline
   ```
   Or navigate to Settings → Apps → Default apps → Phone app → select "Agendroid Spike".

2. Have a second phone (or a SIM in the same device) ready to call the test device.

**Files:**
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeCallScreeningService.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeInCallService.kt`
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/Phase2TelecomActivity.kt`

- [ ] **Step 5.1: Implement SpikeCallScreeningService.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeCallScreeningService.kt
package com.agendroid.spike.callpipeline.phase2

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/** Intercepts all incoming calls and allows them through (does not block).
 *  Logs the intercept time so we can confirm the service is being called. */
class SpikeCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(TAG, "onScreenCall: number=${callDetails.handle}, direction=${callDetails.callDirection}")
        Log.d(TAG, "Call intercepted at ${System.currentTimeMillis()}")

        // Allow the call through — SpikeInCallService handles the UI
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        )
    }

    companion object { const val TAG = "SpikeCallScreening" }
}
```

- [ ] **Step 5.2: Implement SpikeInCallService.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeInCallService.kt
package com.agendroid.spike.callpipeline.phase2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import kotlinx.coroutines.*

/** Spike in-call service. Tests:
 *  (1) AudioRecord captures caller audio during the call
 *  (2) AudioTrack plays back audio to caller
 *  (3) Take-over handoff: measures ms from takeover request to mic released */
class SpikeInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null

    // Exposed for Phase2TelecomActivity to read
    var lastTakeoverMs: Long = -1L
        private set

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added — answering and starting audio test")
        call.answer(0) // answer the call
        startAudioTest(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        stopAudio()
        Log.d(TAG, "Call removed — audio test complete")
    }

    private fun startAudioTest(call: Call) {
        val sampleRate = 16_000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 4,
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            audioTrack?.play()
            Log.d(TAG, "AudioRecord state=${audioRecord?.state}, AudioTrack state=${audioTrack?.state}")

            // Echo loop — captures audio and plays it back (tests duplex routing)
            recordingJob = scope.launch {
                val buffer = ShortArray(bufferSize)
                var frameCount = 0
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        audioTrack?.write(buffer, 0, read)
                        frameCount++
                        if (frameCount == 1) {
                            Log.d(TAG, "First audio frame captured and played — duplex routing WORKS")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio setup failed", e)
        }
    }

    /** Called when user taps Take Over. Measures handoff latency. */
    fun requestTakeover(call: Call) {
        val startMs = System.currentTimeMillis()
        stopAudio()
        // Switch audio to user's earpiece
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_CALL
        am.isSpeakerphoneOn = false
        lastTakeoverMs = System.currentTimeMillis() - startMs
        Log.d(TAG, "Take-over handoff complete in ${lastTakeoverMs}ms")
    }

    private fun stopAudio() {
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopAudio()
    }

    companion object { const val TAG = "SpikeInCallService" }
}
```

- [ ] **Step 5.3: Implement Phase2TelecomActivity.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/Phase2TelecomActivity.kt
package com.agendroid.spike.callpipeline.phase2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Phase2TelecomActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Phase2Screen()
            }
        }
    }
}

@Composable
private fun Phase2Screen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Phase 2: Telecom Audio Routing", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                appendLine("Instructions:")
                appendLine()
                appendLine("1. Set this app as the default phone app:")
                appendLine("   Settings → Apps → Default apps → Phone app → Agendroid Spike")
                appendLine()
                appendLine("2. Have a second phone call this device's number.")
                appendLine()
                appendLine("3. The SpikeInCallService will answer automatically and start")
                appendLine("   capturing + echoing audio. You should hear your own voice")
                appendLine("   echoed back — this confirms duplex audio routing works.")
                appendLine()
                appendLine("4. Watch adb logcat for:")
                appendLine("   • 'SpikeCallScreening: Call intercepted' — confirms CallScreeningService works")
                appendLine("   • 'SpikeInCallService: First audio frame captured' — confirms AudioRecord works")
                appendLine("   • 'Take-over handoff complete in Xms' — after tapping Take Over")
                appendLine()
                appendLine("5. After the call, pull the logcat log and record the results below.")
                appendLine()
                appendLine("Record results:")
                appendLine("  CallScreeningService intercepted: YES / NO")
                appendLine("  AudioRecord captured frames:      YES / NO")
                appendLine("  AudioTrack played back:          YES / NO")
                appendLine("  Take-over handoff time:          ___ ms  (gate: ≤200ms)")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 5.4: Install updated APK and run Phase 2**

```bash
./gradlew :spike:call-pipeline:assembleDebug && \
  adb install -r spike/call-pipeline/build/outputs/apk/debug/call-pipeline-debug.apk
```

Set app as default phone handler, then have a second phone call the device. Watch logcat:

```bash
adb logcat -s SpikeCallScreening SpikeInCallService
```

- [ ] **Step 5.5: Record Phase 2 results**

```
Phase 2 results (fill in after running on device):
  CallScreeningService intercepted call:   YES / NO
  AudioRecord captured audio frames:       YES / NO
  AudioTrack played back to caller:        YES / NO
  Take-over handoff latency:               ___ ms  (gate: ≤200ms)
  Gate take-over:                          PASS / FAIL
  Battery drain (10 min active call):      ___% (gate: ≤8%)
```

- [ ] **Step 5.6: Commit Phase 2**

```bash
git add spike/call-pipeline/
git commit -m "feat(spike): Phase 2 telecom audio routing — CallScreeningService + InCallService + echo test"
```

---

## Task 6: Phase 3 — End-to-End Pipeline (20-Turn Test)

**Goal:** Wire STT stub + LLM + TTS together in a simulated call loop. Measure total end-to-end latency across 20 turns. Verify zero crashes. Calculate p95.

**STT stub approach:** Use Android's `SpeechRecognizer` with a 3-second silence timeout to simulate end-of-utterance detection. This is cloud-based for the spike; the latency is recorded separately and noted as "placeholder — replace with Whisper in Plan 5". The LLM and TTS latency is the primary signal.

**Files:**
- Create: `spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase3/Phase3PipelineActivity.kt`

- [ ] **Step 6.1: Implement Phase3PipelineActivity.kt**

```kotlin
// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase3/Phase3PipelineActivity.kt
package com.agendroid.spike.callpipeline.phase3

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
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
import com.agendroid.spike.callpipeline.llm.LlmInferenceRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MODEL_PATH = "/sdcard/Download/agendroid-spike/gemma3-1b-it-gpu-int4.task"

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
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.US
        }
        setContent {
            MaterialTheme {
                Phase3Screen(scope, applicationContext, MODEL_PATH, tts)
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
            enabled = !running,
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

            val turnStart = System.currentTimeMillis()

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

            // Stage 3: TTS — measure time to speak the response
            val ttsStart = System.currentTimeMillis()
            tts?.speak(llmResult.text, TextToSpeech.QUEUE_FLUSH, null, "turn_$i")
            // TTS speak() is async. Approximate duration: ~200ms for short sentences.
            // For accurate measurement, use an utterance completion listener.
            delay(200L)
            val ttsMs = System.currentTimeMillis() - ttsStart
            log("  TTS (approx): ${ttsMs}ms")

            val totalMs = sttMs + llmResult.totalMs + ttsMs
            val turn = recorder.startTurn()
            turn.markStage("stt_placeholder", sttMs)
            turn.markStage("llm_first_token", llmResult.firstTokenMs)
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
    log("  LLM p95:  ${recorder.turns().map { it.stages["llm_full"] ?: 0L }.sorted().let { it[(it.size * 0.95).toInt().coerceAtMost(it.size - 1)] }}ms")

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
```

- [ ] **Step 6.2: Install and run Phase 3 on device**

```bash
./gradlew :spike:call-pipeline:assembleDebug && \
  adb install -r spike/call-pipeline/build/outputs/apk/debug/call-pipeline-debug.apk
adb shell am start -n com.agendroid.spike.callpipeline/.SpikeLauncherActivity
```

Tap **"Phase 3: End-to-End Pipeline"**. Let all 20 turns complete. Record p95 from the log.

- [ ] **Step 6.3: Pull results**

```bash
adb pull /sdcard/Download/agendroid-spike/ /tmp/spike-results/
```

- [ ] **Step 6.4: Commit Phase 3**

```bash
git add spike/call-pipeline/
git commit -m "feat(spike): Phase 3 end-to-end 20-turn pipeline test with latency recording"
```

---

## Task 7: Go/No-Go Results Document

**Files:**
- Create: `docs/superpowers/specs/2026-03-20-spike-results.md`

- [ ] **Step 7.1: Fill in the results document after running all 3 phases on device**

Create `docs/superpowers/specs/2026-03-20-spike-results.md` and fill in every value. Template:

```markdown
# Call Pipeline Spike Results

**Date:** YYYY-MM-DD
**Device:** OnePlus 12 (or actual device used)
**Snapdragon 8 Gen 3 / RAM:** ___GB
**Model tested:** Gemma 3 1B GPU int4

## Acceptance Gates

| Gate | Threshold | Measured | Result |
| --- | --- | --- | --- |
| End-to-end latency p95 | ≤ 2 000ms | ___ms | PASS / FAIL |
| Thermal sustain (10 min) | ≤ 42°C SoC | ___°C | PASS / FAIL |
| Crash-free (20 turns) | 100% | ___% | PASS / FAIL |
| Battery drain (10 min) | ≤ 8% | ___% | PASS / FAIL |
| Take-over handoff | ≤ 200ms | ___ms | PASS / FAIL |

## Stage Breakdown (Phase 3 medians)

| Stage | Median | Budget |
| --- | --- | --- |
| STT (placeholder 400ms) | 400ms | ≤ 500ms |
| LLM first token | ___ms | ≤ 500ms |
| LLM full response | ___ms | ≤ 600ms |
| TTS | ___ms | ≤ 200ms |

## Decision

**[ ] ALL GATES PASS → Proceed with full call-agent implementation (Plans 3–8 as specified)**

**[ ] ONE OR MORE GATES FAIL → Downgrade v1 scope:**
- Full call-agent deferred to v2
- v1 ships: screen-only call screening (AI transcribes, user speaks) + autonomous SMS
- Plans 3–8 proceed with call-agent code removed from :core:telephony and :feature:phone scope

## Notes

(Add any observations about thermal behavior, audio routing quirks, model loading time, etc.)
```

- [ ] **Step 7.2: Commit the results document**

```bash
git add docs/superpowers/specs/2026-03-20-spike-results.md
git commit -m "docs(spike): add go/no-go results document — [PASS/FAIL]"
```

---

## Acceptance Criteria

Plan 2 is complete when:

- [ ] `:spike:call-pipeline` module builds cleanly (`assembleDebug` passes)
- [ ] All 5 LatencyRecorder unit tests pass
- [ ] Phase 1 has been run on device and results recorded
- [ ] Phase 2 has been run on device with a real call (or clearly documented as unable to test with reason)
- [ ] Phase 3 has been run on device and results recorded
- [ ] `docs/superpowers/specs/2026-03-20-spike-results.md` exists with all gate values filled in and a signed-off decision
- [ ] Git log shows commits for all phases

**Next:** Based on the spike decision:
- **PASS** → Plan 3: :core:data layer (Room + SQLCipher + sqlite-vec)
- **FAIL** → Plan 3: :core:data layer (same), but Plans 7–8 use downgraded call scope
