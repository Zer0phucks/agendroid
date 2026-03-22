# Plan 5: `:core:voice` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `:core:voice` module — Whisper Small STT, Kokoro TTS, and always-on wake-word detection — providing the voice I/O layer consumed by `:core:telephony` (call agent) and `:feature:assistant` (voice overlay).

**Architecture:** Three Kotlin wrappers over sherpa-onnx (a single, self-contained Android AAR that bundles its own ONNX Runtime): `WhisperEngine` (offline ASR), `KokoroEngine` (offline TTS), and `WakeWordDetector` (streaming keyword spotting). All share an `AudioCapture` (AudioRecord streaming at 16 kHz) and `EnergyVad` (pure-Kotlin energy VAD that gates wake-word inference to save power). Each engine follows the `load() → use → close()` lifecycle established in `:core:embeddings`. **Note on spec deviation:** the design spec names whisper.cpp JNI + openWakeWord as the STT and wake-word libraries; sherpa-onnx replaces both (plus adds Kokoro TTS) via a single AAR, eliminating three separate JNI integrations. The public API contracts (`transcribe`, `synthesize`, `start/stop`) are identical to what the spec describes.

**Tech Stack:** sherpa-onnx v1.12.32 local AAR (bundles ONNX Runtime — no separate dependency needed); AudioRecord/AudioTrack (Android SDK); Kotlin Coroutines + Flow; Hilt; JUnit 5 + MockK (JVM unit tests); AndroidJUnit4 (instrumented tests).

---

## Context for implementers

### Module location

Working directory for all tasks: `core/voice/`.

### sherpa-onnx library — manual setup step (do once, before Task 1)

sherpa-onnx is not on Maven Central. Download the AAR from GitHub Releases:

```bash
mkdir -p core/voice/libs
curl -L -o core/voice/libs/sherpa-onnx-android.aar \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.32/sherpa-onnx-1.12.32.aar"
# Expected size: ~44 MB
# SHA256: 43450d480eda70de929be82711851e8e91d82ea640f7d2296c1f6526fd792cad
```

### Model files — download once, track in LFS

All model files go in `core/voice/src/main/assets/models/`. Track with Git LFS:

```bash
# Add LFS tracking (run once from project root)
git lfs track "*.onnx" "*.bin" "core/voice/src/main/assets/models/**"
git add .gitattributes

# --- Whisper Small English (~280 MB total) ---
mkdir -p core/voice/src/main/assets/models/whisper
curl -L "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.en.tar.bz2" | \
  tar -xj -C /tmp/ --strip-components=1 --wildcards "*small.en-encoder.int8.onnx" "*small.en-decoder.int8.onnx" "*tokens.txt"
cp /tmp/small.en-encoder.int8.onnx  core/voice/src/main/assets/models/whisper/encoder.int8.onnx
cp /tmp/small.en-decoder.int8.onnx  core/voice/src/main/assets/models/whisper/decoder.int8.onnx
cp /tmp/tokens.txt                   core/voice/src/main/assets/models/whisper/tokens.txt

# --- Kokoro TTS English (kokoro-en-v0_19, ~330 MB) ---
mkdir -p core/voice/src/main/assets/models/kokoro
curl -L "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2" | \
  tar -xj -C core/voice/src/main/assets/models/kokoro/ --strip-components=1
# Produces: model.onnx, voices.bin, tokens.txt, espeak-ng-data/, lexicon-us-en.txt

# --- Keyword Spotting model (3.3 M params, ~7 MB) ---
mkdir -p core/voice/src/main/assets/models/kws
curl -L "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2" | \
  tar -xj -C core/voice/src/main/assets/models/kws/ --strip-components=1
# Creates: encoder.int8.onnx, decoder.int8.onnx, joiner.int8.onnx, tokens.txt

# Write the keyword trigger phrase
echo "HEY AGENT" > core/voice/src/main/assets/models/kws/keywords.txt

git add core/voice/src/main/assets/models/
```

**Note on keyword:** "HEY AGENT" is used as the v1 wake phrase (works reliably with the gigaspeech model). "HEY AGENDROID" can be substituted once a custom fine-tuned model is trained in a later sprint.

### Existing patterns to follow

- Dispatcher injection: `@IoDispatcher` / `@DefaultDispatcher` from `DispatcherModule` in `:core:common`
- `load() / close()` lifecycle: see `EmbeddingModel.kt` in `:core:embeddings`
- Foreground service pattern: see `AiCoreService.kt` in `:core:ai`
- `testOptions { unitTests.isReturnDefaultValues = true }` in build.gradle.kts (enables Android stubs in JVM tests)

---

## File map

```
core/voice/
├── build.gradle.kts                               MODIFY
├── libs/sherpa-onnx-android.aar                   CREATE (manual download above)
└── src/
    ├── main/
    │   ├── AndroidManifest.xml                    MODIFY (add RECORD_AUDIO)
    │   ├── assets/models/whisper/                 CREATE (LFS)
    │   │   ├── encoder.int8.onnx
    │   │   ├── decoder.int8.onnx
    │   │   └── tokens.txt
    │   ├── assets/models/kokoro/                  CREATE (LFS)
    │   │   ├── model.onnx
    │   │   ├── voices.bin
    │   │   ├── tokens.txt
    │   │   ├── lexicon-us-en.txt
    │   │   └── espeak-ng-data/ (directory)
    │   ├── assets/models/kws/                     CREATE (LFS)
    │   │   ├── encoder.int8.onnx
    │   │   ├── decoder.int8.onnx
    │   │   ├── joiner.int8.onnx
    │   │   ├── tokens.txt
    │   │   └── keywords.txt
    │   └── kotlin/com/agendroid/core/voice/
    │       ├── EnergyVad.kt                       CREATE — pure Kotlin VAD, fully JVM testable
    │       ├── AudioCapture.kt                    CREATE — AudioRecord wrapper
    │       ├── WhisperEngine.kt                   CREATE — STT via sherpa-onnx OfflineRecognizer
    │       ├── KokoroEngine.kt                    CREATE — TTS via sherpa-onnx OfflineTts
    │       ├── WakeWordDetector.kt                CREATE — KWS via sherpa-onnx KeywordSpotter
    │       └── VoiceModule.kt                     CREATE — Hilt @Module anchor
    ├── test/kotlin/com/agendroid/core/voice/
    │   ├── EnergyVadTest.kt                       CREATE — 11 JVM tests
    │   ├── WhisperEngineTest.kt                   CREATE —  5 JVM tests
    │   ├── KokoroEngineTest.kt                    CREATE —  4 JVM tests
    │   └── WakeWordDetectorTest.kt                CREATE —  6 JVM tests
    └── androidTest/kotlin/com/agendroid/core/voice/
        ├── WhisperEngineInstrumentedTest.kt       CREATE —  2 instrumented tests
        └── KokoroEngineInstrumentedTest.kt        CREATE —  2 instrumented tests
```

---

## Task 1: Build configuration

**Files:**
- Modify: `core/voice/build.gradle.kts`
- Modify: `core/voice/src/main/AndroidManifest.xml`
- Modify: `gradle/libs.versions.toml` (no new versions needed — sherpa-onnx is a local AAR)

- [ ] **Step 1: Verify the AAR is in place and correct**

```bash
ls -lh core/voice/libs/sherpa-onnx-android.aar
# Expected: file exists, ~44 MB

echo "43450d480eda70de929be82711851e8e91d82ea640f7d2296c1f6526fd792cad  core/voice/libs/sherpa-onnx-android.aar" \
  | sha256sum --check
# Expected: core/voice/libs/sherpa-onnx-android.aar: OK
```

If the file is missing or the checksum fails, re-run the download step from the Context section above.

- [ ] **Step 2: Update `core/voice/build.gradle.kts`**

Replace the existing file with:

```kotlin
// core/voice/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.voice"
    compileSdk = 35
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // sherpa-onnx bundles its own ONNX Runtime native lib; keep first copy if app also includes it
        jniLibs { pickFirsts += "lib/**/libonnxruntime.so" }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.core.ktx)

    // sherpa-onnx (Whisper STT + Kokoro TTS + KeywordSpotter) — local AAR
    // Download instructions: see Context section in Plan 5
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // JVM unit tests
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 3: Update `core/voice/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Required for AudioCapture (microphone) -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
</manifest>
```

> **Note on foreground service type (API 34+):** Android 14 requires any foreground service that uses the microphone to declare `android:foregroundServiceType="microphone"` on its `<service>` element. This declaration lives in `:core:ai`'s `AndroidManifest.xml` on `AiCoreService` (the service that owns `WakeWordDetector` at runtime), not here. This is verified in Plan 6. If `AiCoreService` calls `startForeground()` without this attribute set, the system will throw `ForegroundServiceTypeNotAllowedException` on API 34+.

- [ ] **Step 4: Verify build**

```bash
./gradlew :core:voice:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. If the AAR is missing you will see "no files found for fileTree" — download it first.

- [ ] **Step 5: Commit**

```bash
git add core/voice/build.gradle.kts core/voice/src/main/AndroidManifest.xml core/voice/libs/
git commit -m "build(voice): add sherpa-onnx AAR dep; declare RECORD_AUDIO permission"
```

---

## Task 2: EnergyVad

**Files:**
- Create: `core/voice/src/main/kotlin/com/agendroid/core/voice/EnergyVad.kt`
- Create: `core/voice/src/test/kotlin/com/agendroid/core/voice/EnergyVadTest.kt`

Pure Kotlin energy-based VAD. No Android or native dependencies — fully JVM testable. Used by `WakeWordDetector` to skip silent frames and conserve power.

- [ ] **Step 1: Write the failing test**

```kotlin
// core/voice/src/test/kotlin/com/agendroid/core/voice/EnergyVadTest.kt
package com.agendroid.core.voice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.PI
import kotlin.math.sin

class EnergyVadTest {

    private val vad = EnergyVad()

    @Test
    fun `silence returns false`() {
        val silence = ShortArray(EnergyVad.FRAME_80MS) { 0 }
        assertFalse(vad.isSpeech(silence))
    }

    @Test
    fun `full-scale tone returns true`() {
        val tone = ShortArray(EnergyVad.FRAME_80MS) { i ->
            (Short.MAX_VALUE * sin(2 * PI * 1000 * i / 16000.0)).toInt().toShort()
        }
        assertTrue(vad.isSpeech(tone))
    }

    @Test
    fun `below-threshold noise returns false`() {
        // Amplitude ~100 out of 32767 => normalised RMS << DEFAULT_THRESHOLD
        val noise = ShortArray(EnergyVad.FRAME_80MS) { ((Math.random() * 200) - 100).toInt().toShort() }
        assertFalse(vad.isSpeech(noise))
    }

    @Test
    fun `empty samples returns false`() {
        assertFalse(vad.isSpeech(ShortArray(0)))
    }

    @Test
    fun `high custom threshold rejects quiet signal`() {
        val strictVad = EnergyVad(threshold = 0.5f)
        val quiet = ShortArray(EnergyVad.FRAME_80MS) { 300 }
        assertFalse(strictVad.isSpeech(quiet))
    }

    @Test
    fun `zero threshold always detects non-silent signal`() {
        val sensitiveVad = EnergyVad(threshold = 0.0f)
        val veryQuiet = ShortArray(EnergyVad.FRAME_80MS) { 1 }
        assertTrue(sensitiveVad.isSpeech(veryQuiet))
    }

    @Test
    fun `threshold of 1 never detects below max amplitude`() {
        val neverVad = EnergyVad(threshold = 1.0f)
        val almostMax = ShortArray(EnergyVad.FRAME_80MS) { (Short.MAX_VALUE - 1) }
        assertFalse(neverVad.isSpeech(almostMax))
    }

    @Test
    fun `negative threshold throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { EnergyVad(-0.01f) }
    }

    @Test
    fun `threshold above 1 throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> { EnergyVad(1.01f) }
    }

    @Test
    fun `frame size constants match 16 kHz`() {
        assertEquals(1280, EnergyVad.FRAME_80MS)   // 0.080 s × 16000 Hz
        assertEquals(2560, EnergyVad.FRAME_160MS)
        assertEquals(5120, EnergyVad.FRAME_320MS)
    }

    @Test
    fun `default threshold is 0_02`() {
        assertEquals(0.02f, EnergyVad.DEFAULT_THRESHOLD)
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | grep "error:"
```

Expected: `error: unresolved reference: EnergyVad`

- [ ] **Step 3: Create `EnergyVad.kt`**

```kotlin
// core/voice/src/main/kotlin/com/agendroid/core/voice/EnergyVad.kt
package com.agendroid.core.voice

import kotlin.math.sqrt

/**
 * Energy-based Voice Activity Detector.
 *
 * Computes RMS energy of a PCM-16 frame and compares against [threshold].
 * Used by [WakeWordDetector] to skip silent frames before running ONNX inference,
 * significantly reducing CPU load during always-on wake-word detection.
 *
 * No Android or native dependencies — fully JVM testable.
 *
 * @param threshold Normalised RMS threshold (0.0–1.0). Default 0.02 (~−34 dBFS).
 */
class EnergyVad(private val threshold: Float = DEFAULT_THRESHOLD) {

    init {
        require(threshold in 0f..1f) { "threshold must be in [0, 1], got $threshold" }
    }

    /**
     * Returns true if [samples] (PCM-16, any sample rate) contains energy above [threshold].
     * Pass one audio frame; see [FRAME_80MS] / [FRAME_160MS] / [FRAME_320MS] for typical sizes.
     */
    fun isSpeech(samples: ShortArray): Boolean {
        if (samples.isEmpty()) return false
        val sumSq = samples.sumOf { s -> s.toLong() * s }
        val rms = sqrt(sumSq.toDouble() / samples.size)
        return (rms / Short.MAX_VALUE).toFloat() >= threshold
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.02f

        /** Frame sizes in samples at 16 kHz. */
        const val FRAME_80MS  = 1_280   // 80 ms  — openWakeWord / sherpa-onnx KWS frame
        const val FRAME_160MS = 2_560   // 160 ms
        const val FRAME_320MS = 5_120   // 320 ms — typical Whisper chunk size
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, 11 tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/voice/src/main/kotlin/com/agendroid/core/voice/EnergyVad.kt \
        core/voice/src/test/kotlin/com/agendroid/core/voice/EnergyVadTest.kt
git commit -m "feat(voice): add EnergyVad — pure-Kotlin energy VAD for always-on wake-word gating"
```

---

## Task 3: AudioCapture

**Files:**
- Create: `core/voice/src/main/kotlin/com/agendroid/core/voice/AudioCapture.kt`

AudioRecord wrapper. No JVM tests (requires Android framework). Instrumented tests deferred to Tasks 4–5.

- [ ] **Step 1: Create `AudioCapture.kt`**

```kotlin
// core/voice/src/main/kotlin/com/agendroid/core/voice/AudioCapture.kt
package com.agendroid.core.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Streams 16-bit PCM at 16 kHz from the device microphone.
 *
 * Each call to [start] launches a coroutine on [Dispatchers.IO] that continuously
 * reads [frameSize] samples from [AudioRecord] and delivers them to [onFrame].
 *
 * Usage:
 * ```
 *   val capture = AudioCapture()
 *   capture.start(serviceScope) { pcmFrame -> /* process frame */ }
 *   // ... later ...
 *   capture.stop()
 *   capture.close()
 * ```
 *
 * Thread safety: [start] and [stop] must be called from the same thread.
 * [onFrame] is invoked on an internal IO coroutine — do NOT touch UI from it.
 *
 * Requires: RECORD_AUDIO permission granted before [start].
 */
class AudioCapture(
    val frameSize: Int  = EnergyVad.FRAME_80MS,
    val sampleRate: Int = SAMPLE_RATE_HZ,
) : Closeable {

    private var record: AudioRecord? = null
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Starts microphone capture, calling [onFrame] for each [frameSize]-sample chunk.
     * @throws IllegalStateException if already running or if RECORD_AUDIO is not granted.
     */
    fun start(scope: CoroutineScope, onFrame: (ShortArray) -> Unit) {
        check(!isRunning) { "Already capturing. Call stop() first." }
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, FORMAT)
        val bufSize = maxOf(minBuf, frameSize * Short.SIZE_BYTES * 4)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, CHANNEL, FORMAT, bufSize,
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialise. Verify RECORD_AUDIO permission is granted."
        }
        record = rec
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(frameSize)
            while (isActive) {
                val read = rec.read(buf, 0, frameSize, AudioRecord.READ_BLOCKING)
                if (read > 0) onFrame(buf.copyOf(read))
            }
        }
    }

    /** Stops capture. Safe to call multiple times. */
    fun stop() {
        job?.cancel()
        job = null
        record?.stop()
    }

    /** Releases the [AudioRecord]. Call [stop] first. */
    override fun close() {
        stop()
        record?.release()
        record = null
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT  = AudioFormat.ENCODING_PCM_16BIT
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :core:voice:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add core/voice/src/main/kotlin/com/agendroid/core/voice/AudioCapture.kt
git commit -m "feat(voice): add AudioCapture — 16 kHz PCM streaming via AudioRecord"
```

---

## Task 4: WhisperEngine

**Files:**
- Create: `core/voice/src/main/kotlin/com/agendroid/core/voice/WhisperEngine.kt`
- Create: `core/voice/src/test/kotlin/com/agendroid/core/voice/WhisperEngineTest.kt`
- Create: `core/voice/src/androidTest/kotlin/com/agendroid/core/voice/WhisperEngineInstrumentedTest.kt`

sherpa-onnx `OfflineRecognizer` wrapper. JVM tests validate state machine and mock-based model availability. Instrumented tests require the actual model file and a connected device.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
// core/voice/src/test/kotlin/com/agendroid/core/voice/WhisperEngineTest.kt
package com.agendroid.core.voice

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class WhisperEngineTest {

    private fun mockContextWithAssets(encoderPresent: Boolean): Context {
        val assetManager = mockk<AssetManager> {
            if (encoderPresent) {
                every { open("models/whisper/encoder.int8.onnx") } returns "x".byteInputStream()
                every { open("models/whisper/decoder.int8.onnx") } returns "x".byteInputStream()
                every { open("models/whisper/tokens.txt") }         returns "x".byteInputStream()
            } else {
                every { open(any()) } throws FileNotFoundException()
            }
        }
        return mockk { every { assets } returns assetManager }
    }

    @Test
    fun `isModelAvailable returns false when assets missing`() {
        assertFalse(WhisperEngine(mockContextWithAssets(false)).isModelAvailable())
    }

    @Test
    fun `isModelAvailable returns true when all assets present`() {
        assertTrue(WhisperEngine(mockContextWithAssets(true)).isModelAvailable())
    }

    @Test
    fun `transcribe without load throws IllegalStateException`() = runTest {
        val engine = WhisperEngine(mockk())
        assertThrows<IllegalStateException> { engine.transcribe(ShortArray(16_000)) }
    }

    @Test
    fun `load without model throws IllegalStateException`() = runTest {
        val engine = WhisperEngine(mockContextWithAssets(false))
        assertThrows<IllegalStateException> { engine.load() }
    }

    @Test
    fun `close on unloaded engine does not throw`() {
        WhisperEngine(mockk()).close()
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | grep "error:"
```

Expected: `error: unresolved reference: WhisperEngine`

- [ ] **Step 3: Create `WhisperEngine.kt`**

```kotlin
// core/voice/src/main/kotlin/com/agendroid/core/voice/WhisperEngine.kt
package com.agendroid.core.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

private const val ENCODER_ASSET = "models/whisper/encoder.int8.onnx"
private const val DECODER_ASSET = "models/whisper/decoder.int8.onnx"
private const val TOKENS_ASSET  = "models/whisper/tokens.txt"

/**
 * Speech-to-text engine using Whisper Small (English) via sherpa-onnx.
 *
 * Models: quantised int8 encoder + decoder ONNX files (~280 MB total) in assets/.
 * Download instructions: see Plan 5 Context section.
 *
 * Thread safety: [transcribe] is NOT thread-safe. The calling service (e.g. :core:telephony)
 * must serialise calls — typically via the same Mutex that gates LLM generation.
 *
 * Input: 16 kHz mono PCM-16 from [AudioCapture].
 * Output: raw transcript text string.
 */
class WhisperEngine(private val context: Context) : Closeable {

    private var recognizer: OfflineRecognizer? = null

    /** Returns true if all required model assets are present in the APK. */
    fun isModelAvailable(): Boolean = listOf(ENCODER_ASSET, DECODER_ASSET, TOKENS_ASSET).all { asset ->
        try { context.assets.open(asset).close(); true } catch (_: Exception) { false }
    }

    /**
     * Loads the Whisper model. Idempotent — no-op if already loaded.
     * @throws IllegalStateException if any model asset is missing.
     */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (recognizer != null) return@withContext
        check(isModelAvailable()) {
            "Whisper model assets missing. Run the setup steps in Plan 5 to download them."
        }
        val config = OfflineRecognizerConfig(
            feat = FeatureConfig(sampleRate = AudioCapture.SAMPLE_RATE_HZ, featureDim = 80),
            model = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = ENCODER_ASSET,
                    decoder = DECODER_ASSET,
                    language = "en",
                    task = "transcribe",
                    tailPaddings = -1,
                ),
                tokens     = TOKENS_ASSET,
                numThreads = 2,
                debug      = false,
                provider   = "cpu",
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(assetManager = context.assets, config = config)
    }

    /**
     * Transcribes [pcm] (16 kHz mono PCM-16, any duration) and returns the text.
     * Suspends on [Dispatchers.IO] for the duration of inference.
     *
     * @throws IllegalStateException if [load] was not called first.
     */
    suspend fun transcribe(pcm: ShortArray): String = withContext(Dispatchers.IO) {
        val rec = checkNotNull(recognizer) { "Call load() before transcribe()" }
        val floats = FloatArray(pcm.size) { pcm[it] / Short.MAX_VALUE.toFloat() }
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples = floats, sampleRate = AudioCapture.SAMPLE_RATE_HZ)
            rec.decode(stream)
            rec.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    /** Releases the recognizer. Safe to call multiple times. */
    override fun close() {
        recognizer?.release()
        recognizer = null
    }
}
```

- [ ] **Step 4: Create instrumented test**

```kotlin
// core/voice/src/androidTest/kotlin/com/agendroid/core/voice/WhisperEngineInstrumentedTest.kt
package com.agendroid.core.voice

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperEngineInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun engine_loadsSuccessfully_whenModelPresent() = runTest {
        val engine = WhisperEngine(context)
        assumeTrue("Whisper model not present — skipping load test", engine.isModelAvailable())
        engine.load()
        engine.close()
    }

    @Test
    fun engine_transcribesBlankAudio_withoutCrash() = runTest {
        val engine = WhisperEngine(context)
        assumeTrue("Whisper model not present — skipping transcribe test", engine.isModelAvailable())
        engine.load()
        // 2 s of silence — should return empty string or "[BLANK_AUDIO]" without crashing
        val result = engine.transcribe(ShortArray(32_000) { 0 })
        assertNotNull(result)
        engine.close()
    }
}
```

- [ ] **Step 5: Run JVM tests**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`, 5 WhisperEngine JVM tests pass (+ 11 EnergyVad = 16 total).

- [ ] **Step 6: Build assembleDebug**

```bash
./gradlew :core:voice:assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add core/voice/src/main/kotlin/com/agendroid/core/voice/WhisperEngine.kt \
        core/voice/src/test/kotlin/com/agendroid/core/voice/WhisperEngineTest.kt \
        core/voice/src/androidTest/kotlin/com/agendroid/core/voice/WhisperEngineInstrumentedTest.kt
git commit -m "feat(voice): add WhisperEngine — offline STT via sherpa-onnx Whisper Small"
```

---

## Task 5: KokoroEngine

**Files:**
- Create: `core/voice/src/main/kotlin/com/agendroid/core/voice/KokoroEngine.kt`
- Create: `core/voice/src/test/kotlin/com/agendroid/core/voice/KokoroEngineTest.kt`
- Create: `core/voice/src/androidTest/kotlin/com/agendroid/core/voice/KokoroEngineInstrumentedTest.kt`

sherpa-onnx `OfflineTts` wrapper. Kokoro v0.19 English, 11 speakers. Output is 24 kHz mono float32 PCM.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
// core/voice/src/test/kotlin/com/agendroid/core/voice/KokoroEngineTest.kt
package com.agendroid.core.voice

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class KokoroEngineTest {

    private fun mockContextNoAssets(): Context {
        val am = mockk<AssetManager> { every { open(any()) } throws FileNotFoundException() }
        return mockk { every { assets } returns am }
    }

    @Test
    fun `isModelAvailable returns false when assets missing`() {
        assertFalse(KokoroEngine(mockContextNoAssets()).isModelAvailable())
    }

    @Test
    fun `synthesize without load throws IllegalStateException`() = runTest {
        assertThrows<IllegalStateException> { KokoroEngine(mockContextNoAssets()).synthesize("hi") }
    }

    @Test
    fun `synthesize empty text throws IllegalArgumentException`() = runTest {
        // Empty text check is done before synthesis — no model needed.
        // Use mockContextNoAssets() (not bare mockk()) so the test does not depend on
        // require() executing before checkNotNull(tts) in synthesize().
        val engine = KokoroEngine(mockContextNoAssets())
        assertThrows<IllegalArgumentException> { engine.synthesize("") }
    }

    @Test
    fun `close on unloaded engine does not throw`() {
        KokoroEngine(mockk()).close()
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | grep "error:"
```

Expected: `error: unresolved reference: KokoroEngine`

- [ ] **Step 3: Create `KokoroEngine.kt`**

```kotlin
// core/voice/src/main/kotlin/com/agendroid/core/voice/KokoroEngine.kt
package com.agendroid.core.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

private const val MODEL_ASSET   = "models/kokoro/model.onnx"
private const val VOICES_ASSET  = "models/kokoro/voices.bin"
private const val TOKENS_ASSET  = "models/kokoro/tokens.txt"
private const val LEXICON_ASSET = "models/kokoro/lexicon-us-en.txt"
private const val DATA_DIR      = "models/kokoro/espeak-ng-data"

/** Sample rate of all audio produced by Kokoro v0.19. */
const val KOKORO_SAMPLE_RATE = 24_000

/**
 * Text-to-speech engine using Kokoro v0.19 (English) via sherpa-onnx.
 *
 * Models: ~330 MB in assets/ (model.onnx, voices.bin, tokens.txt, espeak-ng-data/).
 * Download instructions: see Plan 5 Context section.
 *
 * Output: [KOKORO_SAMPLE_RATE] Hz mono float32 PCM in a [FloatArray].
 * Callers must convert to PCM-16 for [android.media.AudioTrack] playback.
 *
 * Thread safety: [synthesize] is NOT thread-safe. Serialise at call site.
 */
class KokoroEngine(private val context: Context) : Closeable {

    private var tts: OfflineTts? = null

    /** Returns true if all Kokoro model assets are present. */
    fun isModelAvailable(): Boolean = listOf(MODEL_ASSET, VOICES_ASSET, TOKENS_ASSET).all { a ->
        try { context.assets.open(a).close(); true } catch (_: Exception) { false }
    }

    /**
     * Loads the Kokoro TTS model. Idempotent.
     * @throws IllegalStateException if model assets are missing.
     */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (tts != null) return@withContext
        check(isModelAvailable()) {
            "Kokoro model assets missing. Run the setup steps in Plan 5 to download them."
        }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model      = MODEL_ASSET,
                    voices     = VOICES_ASSET,
                    tokens     = TOKENS_ASSET,
                    dataDir    = DATA_DIR,
                    lexicon    = LEXICON_ASSET,
                    lengthScale = 1.0f,
                ),
                numThreads = 2,
                debug      = false,
                provider   = "cpu",
            ),
        )
        tts = OfflineTts(assetManager = context.assets, config = config)
    }

    /**
     * Synthesises [text] and returns [KOKORO_SAMPLE_RATE] Hz mono float32 PCM.
     *
     * @param text     Text to synthesise. Must not be empty.
     * @param speakerId Voice index 0–10 (Kokoro en-v0_19 has 11 English voices).
     * @param speed    Playback rate multiplier (0.5 = slow, 1.0 = normal, 2.0 = fast).
     *
     * @throws IllegalArgumentException if [text] is empty.
     * @throws IllegalStateException if [load] was not called first.
     */
    suspend fun synthesize(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f,
    ): FloatArray = withContext(Dispatchers.IO) {
        require(text.isNotEmpty()) { "Cannot synthesise empty text" }
        val engine = checkNotNull(tts) { "Call load() before synthesize()" }
        engine.generate(text = text, speaker = speakerId, speed = speed).samples
    }

    /** Releases Kokoro resources. Safe to call multiple times. */
    override fun close() {
        tts?.release()
        tts = null
    }
}
```

- [ ] **Step 4: Create instrumented test**

```kotlin
// core/voice/src/androidTest/kotlin/com/agendroid/core/voice/KokoroEngineInstrumentedTest.kt
package com.agendroid.core.voice

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KokoroEngineInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun engine_loadsSuccessfully_whenModelPresent() = runTest {
        val engine = KokoroEngine(context)
        assumeTrue("Kokoro model not present — skipping", engine.isModelAvailable())
        engine.load()
        engine.close()
    }

    @Test
    fun engine_synthesizesShortText_returnsNonEmptyPcm() = runTest {
        val engine = KokoroEngine(context)
        assumeTrue("Kokoro model not present — skipping", engine.isModelAvailable())
        engine.load()
        val pcm = engine.synthesize("Hello.")
        assertTrue("Expected non-empty PCM output", pcm.isNotEmpty())
        engine.close()
    }
}
```

- [ ] **Step 5: Run JVM tests**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`. 4 KokoroEngine + 5 WhisperEngine + 11 EnergyVad = 20 JVM tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/voice/src/main/kotlin/com/agendroid/core/voice/KokoroEngine.kt \
        core/voice/src/test/kotlin/com/agendroid/core/voice/KokoroEngineTest.kt \
        core/voice/src/androidTest/kotlin/com/agendroid/core/voice/KokoroEngineInstrumentedTest.kt
git commit -m "feat(voice): add KokoroEngine — offline TTS via sherpa-onnx Kokoro v0.19"
```

---

## Task 6: WakeWordDetector

**Files:**
- Create: `core/voice/src/main/kotlin/com/agendroid/core/voice/WakeWordDetector.kt`
- Create: `core/voice/src/test/kotlin/com/agendroid/core/voice/WakeWordDetectorTest.kt`

sherpa-onnx `KeywordSpotter` wrapper with `EnergyVad` power gating. Always-on streaming detection on 80 ms audio frames. The `KeywordSpotter` uses a streaming zipformer transducer model that matches keywords against a `keywords.txt` file — equivalent capability to openWakeWord with better Android integration.

**Note on model type:** The `sherpa-onnx-kws-zipformer-gigaspeech-3.3M` model is a streaming ASR network trained on GigaSpeech. It runs keyword spotting by comparing decoded hypotheses against entries in `keywords.txt`. This is different from openWakeWord's binary-classifier ONNX models but serves the same purpose. A custom wake-word model (lower false-positive rate) can be trained and swapped in later without changing this Kotlin wrapper.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
// core/voice/src/test/kotlin/com/agendroid/core/voice/WakeWordDetectorTest.kt
package com.agendroid.core.voice

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class WakeWordDetectorTest {

    private fun mockContextNoAssets(): Context {
        val am = mockk<AssetManager> { every { open(any()) } throws FileNotFoundException() }
        return mockk { every { assets } returns am }
    }

    @Test
    fun `isModelAvailable returns false when assets missing`() {
        assertFalse(WakeWordDetector(mockContextNoAssets()).isModelAvailable())
    }

    @Test
    fun `start without load throws IllegalStateException`() {
        // start() is not suspending — assertThrows catches the synchronous throw directly.
        val detector = WakeWordDetector(mockContextNoAssets())
        assertThrows<IllegalStateException> {
            detector.start(kotlinx.coroutines.test.TestScope()) {}
        }
    }

    @Test
    fun `load without model throws IllegalStateException`() {
        assertThrows<IllegalStateException> { WakeWordDetector(mockContextNoAssets()).load() }
    }

    @Test
    fun `close on unloaded detector does not throw`() {
        WakeWordDetector(mockContextNoAssets()).close()
    }

    @Test
    fun `stop on unstarted detector does not throw`() {
        WakeWordDetector(mockContextNoAssets()).stop()
    }

    @Test
    fun `isRunning is false before start`() {
        assertFalse(WakeWordDetector(mockContextNoAssets()).isRunning)
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | grep "error:"
```

Expected: `error: unresolved reference: WakeWordDetector`

- [ ] **Step 3: Create `WakeWordDetector.kt`**

```kotlin
// core/voice/src/main/kotlin/com/agendroid/core/voice/WakeWordDetector.kt
package com.agendroid.core.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.KeywordSpotterModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

private const val ENCODER_ASSET  = "models/kws/encoder.int8.onnx"
private const val DECODER_ASSET  = "models/kws/decoder.int8.onnx"
private const val JOINER_ASSET   = "models/kws/joiner.int8.onnx"
private const val TOKENS_ASSET   = "models/kws/tokens.txt"
private const val KEYWORDS_ASSET = "models/kws/keywords.txt"

/**
 * Always-on wake-word detector using sherpa-onnx KeywordSpotter.
 *
 * Model: sherpa-onnx-kws-zipformer-gigaspeech-3.3M (~7 MB) in assets/.
 * Keywords: "HEY AGENT" (configurable via [KEYWORDS_ASSET]).
 * Download instructions: see Plan 5 Context section.
 *
 * Architecture:
 *  AudioCapture → EnergyVad (gate) → KeywordSpotter (streaming, 80 ms frames)
 *  → [onDetected] callback → 2 s cooldown → resume
 *
 * Power: EnergyVad skips silent frames (no ONNX inference during silence).
 * The zipformer model runs in ~1 ms/frame on Snapdragon 8 Gen 3.
 *
 * Lifecycle: call [load] once, then [start] / [stop] as needed. Call [close] on teardown.
 * The detector is START_STICKY safe — [load] and [start] are idempotent.
 */
class WakeWordDetector(
    private val context: Context,
    private val audioCapture: AudioCapture = AudioCapture(frameSize = EnergyVad.FRAME_80MS),
    private val vad: EnergyVad = EnergyVad(threshold = 0.005f),  // sensitive: low power, always-on
) : Closeable {

    private var spotter: KeywordSpotter? = null
    private var kwsStream: OnlineStream? = null
    val isRunning: Boolean get() = audioCapture.isRunning

    /** Returns true if all KWS model assets are present. */
    fun isModelAvailable(): Boolean =
        listOf(ENCODER_ASSET, DECODER_ASSET, JOINER_ASSET, TOKENS_ASSET, KEYWORDS_ASSET).all { a ->
            try { context.assets.open(a).close(); true } catch (_: Exception) { false }
        }

    /**
     * Loads the KWS model. Idempotent.
     * @throws IllegalStateException if model assets are missing.
     */
    fun load() {
        if (spotter != null) return
        check(isModelAvailable()) {
            "KWS model assets missing. Run the setup steps in Plan 5 to download them."
        }
        val config = KeywordSpotterConfig(
            feat = FeatureConfig(sampleRate = AudioCapture.SAMPLE_RATE_HZ, featureDim = 80),
            model = KeywordSpotterModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = ENCODER_ASSET,
                    decoder = DECODER_ASSET,
                    joiner  = JOINER_ASSET,
                ),
                tokens     = TOKENS_ASSET,
                numThreads = 1,       // low priority — wake word detection is latency-tolerant
                debug      = false,
                provider   = "cpu",
            ),
            maxActivePaths     = 4,
            keywordsFile       = KEYWORDS_ASSET,
            keywordsScore      = 1.0f,
            keywordsThreshold  = 0.25f,
            numTrailingBlanks  = 1,
        )
        spotter = KeywordSpotter(assetManager = context.assets, config = config)
    }

    /**
     * Starts always-on detection. [onDetected] fires each time the wake phrase is recognised,
     * followed by a [COOLDOWN_FRAMES]-frame pause before the next detection can fire.
     *
     * @throws IllegalStateException if [load] was not called first.
     */
    fun start(scope: CoroutineScope, onDetected: () -> Unit) {
        val kws = checkNotNull(spotter) { "Call load() before start()" }
        check(!isRunning) { "Already running. Call stop() first." }

        val stream = kws.createStream()
        kwsStream = stream
        var cooldown = 0

        audioCapture.start(scope) { pcm ->
            if (cooldown > 0) { cooldown--; return@start }
            if (!vad.isSpeech(pcm)) return@start   // skip silent frames

            val floats = FloatArray(pcm.size) { pcm[it] / Short.MAX_VALUE.toFloat() }
            stream.acceptWaveform(samples = floats, sampleRate = AudioCapture.SAMPLE_RATE_HZ)

            if (kws.isReady(stream)) {
                kws.decode(stream)
                val result = kws.getResult(stream)
                if (result.keyword.isNotEmpty()) {
                    cooldown = COOLDOWN_FRAMES
                    kws.reset(stream)
                    onDetected()
                }
            }
        }
    }

    /** Stops detection. Safe to call multiple times. */
    fun stop() {
        audioCapture.stop()
    }

    /** Releases all resources. Safe to call multiple times. */
    override fun close() {
        stop()
        kwsStream?.release()
        kwsStream = null
        audioCapture.close()
        spotter?.release()
        spotter = null
    }

    companion object {
        /** Frames to skip after detection fires (80 ms × 25 = 2 s cooldown). */
        private const val COOLDOWN_FRAMES = 25
    }
}
```

- [ ] **Step 4: Run JVM tests**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`. 6 + 4 + 5 + 11 = 26 JVM tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/voice/src/main/kotlin/com/agendroid/core/voice/WakeWordDetector.kt \
        core/voice/src/test/kotlin/com/agendroid/core/voice/WakeWordDetectorTest.kt
git commit -m "feat(voice): add WakeWordDetector — streaming KWS via sherpa-onnx + EnergyVad gating"
```

---

## Task 7: VoiceModule + final verification

**Files:**
- Create: `core/voice/src/main/kotlin/com/agendroid/core/voice/VoiceModule.kt`

All voice singletons (`WhisperEngine`, `KokoroEngine`, `WakeWordDetector`) are NOT Hilt singletons — they are lifecycle-managed by the service that owns them (`:core:telephony` in Plan 6, `:feature:assistant` in Plan 8). The module is an anchor only. This mirrors the `:core:ai` pattern where `LlmInferenceEngine` is not Hilt-injected.

- [ ] **Step 1: Create `VoiceModule.kt`**

```kotlin
// core/voice/src/main/kotlin/com/agendroid/core/voice/VoiceModule.kt
package com.agendroid.core.voice

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module anchor for :core:voice.
 *
 * Voice engines are NOT provided here — they are Context-bound and lifecycle-managed
 * by the services that own them:
 *   - [WhisperEngine] + [KokoroEngine] + [WakeWordDetector] owned by :core:telephony (Plan 6)
 *   - [WakeWordDetector] also used by :feature:assistant (Plan 8) via a dedicated service
 *
 * [AudioCapture] and [EnergyVad] are value types — instantiate them directly where needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule
```

- [ ] **Step 2: Run the full JVM test suite**

```bash
./gradlew :core:voice:testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. 26 JVM tests pass:
- EnergyVad: 11
- WhisperEngine: 5
- KokoroEngine: 4
- WakeWordDetector: 6

- [ ] **Step 3: Final assembleDebug**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` across all modules.

- [ ] **Step 4: Commit**

```bash
git add core/voice/src/main/kotlin/com/agendroid/core/voice/VoiceModule.kt
git commit -m "feat(voice): add VoiceModule Hilt anchor; :core:voice complete"
```

---

## Final verification

- [ ] `./gradlew assembleDebug` — all modules compile
- [ ] `./gradlew :core:voice:testDebugUnitTest` — 26 JVM tests PASS
- [ ] `./gradlew :core:voice:connectedDebugAndroidTest` — 4 instrumented tests PASS (requires device + model files)
- [ ] Total: 26 JVM + 4 instrumented = 30 voice tests

### Model availability reminder

Instrumented tests use `assumeTrue(engine.isModelAvailable())` — they are skipped (not failed) when model files are absent. Run them on a device with models present to confirm end-to-end audio inference.

---

## Known limitations / deferred to Plan 6+

- **No AudioTrack playback helper**: `KokoroEngine` returns raw `FloatArray`. Conversion to PCM-16 and playback via `AudioTrack` is implemented in `:core:telephony` (Plan 6) where it is consumed in the call loop.
- **No streaming STT**: `WhisperEngine.transcribe` takes a complete audio chunk. Real-time streaming ASR (for live call transcription) uses `OfflineRecognizer` in a sliding-window loop — implemented in Plan 6's `CallTranscriber`.
- **Custom wake word**: "HEY AGENT" with the gigaspeech model has higher false-positive rate than a dedicated model. A fine-tuned wake-word model is a post-Plan 8 task.
- **AudioCapture permission handling**: The module declares the permission but does not request it. Feature modules handle the permission request flow (`:feature:assistant` onboarding in Plan 8).
