# Plan 6: `:core:telephony` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the production telephony integration layer in `:core:telephony`: incoming-call screening, active-call session management, AI call handling hooks, take-over support, and outgoing call registration via Telecom APIs.

**Architecture:** `:core:telephony` becomes the system-facing boundary for phone calls. It owns the Android services (`CallScreeningService`, `InCallService`, `ConnectionService`), a pure-Kotlin decision engine for call autonomy and emergency bypass, and a `CallSessionRepository` that exposes active call state and transcript updates as `StateFlow`s for later UI plans. The call agent path reuses the validated spike approach for duplex audio routing, but wraps it behind production-safe classes (`CallAudioBridge`, `TelephonyCoordinator`) so `:feature:phone` can consume state later without depending on Telecom directly.

**Tech Stack:** Android Telecom APIs (`CallScreeningService`, `InCallService`, `ConnectionService`, `TelecomManager`, `PhoneAccount`), Kotlin Coroutines + Flow, Hilt, JUnit 5 + MockK, AndroidX instrumented tests.

**Spec reference:** `docs/superpowers/specs/2026-03-20-agendroid-design.md` §2, §4, §7, §9, §11.1, §11.5, §11.7 and `docs/superpowers/specs/2026-03-20-spike-results.md`.

---

## Context for implementers

### Why full-agent mode is in scope

The spike results document explicitly records:

- `ALL GATES PASS -> Proceed with full call-agent implementation`
- Take-over handoff measured at `131ms`
- End-to-end latency to first audio estimated at `~1300ms`

So Plan 6 should target the full-agent service layer, not screen-only fallback. Keep the spec's downgrade logic in code paths (`screen-only`, `pass-through`, emergency bypass, degraded AI unavailable), but the primary implementation target is full-agent mode.

### Scope boundary for Plan 6

Plan 6 owns Telecom integration and call-state orchestration. It does **not** build the polished in-call/dialer Compose UI from `:feature:phone`; instead it exposes a clean repository + service contract that the feature module will render in a later plan.

That means:

- `:core:telephony` owns system services, call state, transcript state, take-over action, and outgoing call plumbing
- `:feature:phone` will later own the actual user-facing dialer/call log/in-call Compose screens

### Existing code to reuse

- `spike/call-pipeline/.../SpikeCallScreeningService.kt`
- `spike/call-pipeline/.../SpikeInCallService.kt`
- `core/ai/src/main/kotlin/com/agendroid/core/ai/AiCoreService.kt`
- `core/voice/src/main/kotlin/com/agendroid/core/voice/AudioCapture.kt`
- `core/voice/src/main/kotlin/com/agendroid/core/voice/WhisperEngine.kt`
- `core/voice/src/main/kotlin/com/agendroid/core/voice/KokoroEngine.kt`

Do not copy spike code verbatim into services. Port the proven behavior, but split it into focused production classes with tests.

---

## File map

```text
core/telephony/
├── build.gradle.kts                                        MODIFY
└── src/
    ├── main/
    │   ├── AndroidManifest.xml                             MODIFY
    │   └── kotlin/com/agendroid/core/telephony/
    │       ├── CallAutonomyMode.kt                         CREATE
    │       ├── CallScreeningDecision.kt                    CREATE
    │       ├── EmergencyNumberPolicy.kt                    CREATE
    │       ├── CallTranscriptLine.kt                       CREATE
    │       ├── CallSession.kt                              CREATE
    │       ├── CallSessionRepository.kt                    CREATE
    │       ├── CallAutonomyDecider.kt                      CREATE
    │       ├── TelephonyCoordinator.kt                     CREATE
    │       ├── CallAudioBridge.kt                          CREATE
    │       ├── AiServiceConnector.kt                       CREATE
    │       ├── AgendroidCallScreeningService.kt            CREATE
    │       ├── AgendroidInCallService.kt                   CREATE
    │       ├── AgendroidConnectionService.kt               CREATE
    │       ├── PhoneAccountRegistrar.kt                    CREATE
    │       └── TelephonyModule.kt                          CREATE
    ├── test/kotlin/com/agendroid/core/telephony/
    │   ├── EmergencyNumberPolicyTest.kt                    CREATE
    │   ├── CallAutonomyDeciderTest.kt                      CREATE
    │   ├── CallSessionRepositoryTest.kt                    CREATE
    │   └── TelephonyCoordinatorTest.kt                     CREATE
    └── androidTest/kotlin/com/agendroid/core/telephony/
        ├── PhoneAccountRegistrarInstrumentedTest.kt        CREATE
        └── TelephonyManifestInstrumentedTest.kt            CREATE

app/src/main/AndroidManifest.xml                            MODIFY (role-facing intent filters / comments only if needed)
```

---

## Task 1: Build + manifest wiring

**Files:**
- Modify: `core/telephony/build.gradle.kts`
- Modify: `core/telephony/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add Android test support and core Android deps**

Update `core/telephony/build.gradle.kts` so the module has:

```kotlin
android {
    namespace = "com.agendroid.core.telephony"
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
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ai"))
    implementation(project(":core:voice"))
    implementation(libs.core.ktx)
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 2: Declare the Telecom services in `core/telephony`**

Replace `core/telephony/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <service
            android:name=".AgendroidCallScreeningService"
            android:exported="true"
            android:permission="android.permission.BIND_SCREENING_SERVICE">
            <intent-filter>
                <action android:name="android.telecom.CallScreeningService" />
            </intent-filter>
        </service>

        <service
            android:name=".AgendroidInCallService"
            android:exported="true"
            android:permission="android.permission.BIND_INCALL_SERVICE">
            <meta-data
                android:name="android.telecom.IN_CALL_SERVICE_UI"
                android:value="true" />
            <intent-filter>
                <action android:name="android.telecom.InCallService" />
            </intent-filter>
        </service>

        <service
            android:name=".AgendroidConnectionService"
            android:exported="true"
            android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE">
            <intent-filter>
                <action android:name="android.telecom.ConnectionService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

- [ ] **Step 3: Verify manifest merge**

Run:

```bash
./gradlew :core:telephony:processDebugManifest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add core/telephony/build.gradle.kts core/telephony/src/main/AndroidManifest.xml
git commit -m "build(telephony): add Telecom service manifest wiring"
```

---

## Task 2: Pure call-screening policy and safety rules

**Files:**
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallAutonomyMode.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallScreeningDecision.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/EmergencyNumberPolicy.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallAutonomyDecider.kt`
- Create: `core/telephony/src/test/kotlin/com/agendroid/core/telephony/EmergencyNumberPolicyTest.kt`
- Create: `core/telephony/src/test/kotlin/com/agendroid/core/telephony/CallAutonomyDeciderTest.kt`

- [ ] **Step 1: Write failing tests for emergency bypass**

Cover at minimum:

```kotlin
@Test
fun `911 always passes through`() { ... }

@Test
fun `112 always passes through`() { ... }

@Test
fun `blank number is not considered emergency`() { ... }
```

- [ ] **Step 2: Implement `EmergencyNumberPolicy`**

Use a simple normalized digit-only check first:

```kotlin
internal class EmergencyNumberPolicy(
    private val emergencyNumbers: Set<String> = setOf("911", "112", "999"),
) {
    fun isEmergency(number: String?): Boolean {
        val digits = number?.filter(Char::isDigit).orEmpty()
        return digits in emergencyNumbers
    }
}
```

- [ ] **Step 3: Write failing tests for autonomy decisions**

Cover:

- emergency numbers -> `PassThrough`
- AI unavailable -> `PassThrough`
- `PASS_THROUGH` mode -> `PassThrough`
- `SCREEN_ONLY` mode -> `ScreenOnly`
- `FULL_AGENT` mode -> `FullAgent`

- [ ] **Step 4: Implement `CallAutonomyMode`, `CallScreeningDecision`, and `CallAutonomyDecider`**

The decider should be pure Kotlin:

```kotlin
enum class CallAutonomyMode { FULL_AGENT, SCREEN_ONLY, PASS_THROUGH }

sealed interface CallScreeningDecision {
    data object PassThrough : CallScreeningDecision
    data object ScreenOnly : CallScreeningDecision
    data object FullAgent : CallScreeningDecision
}
```

Make `CallAutonomyDecider` accept:

- autonomy mode
- `isAiAvailable`
- incoming number

and always prioritize emergency + degraded pass-through over user autonomy.

- [ ] **Step 5: Verify JVM tests**

Run:

```bash
./gradlew :core:telephony:testDebugUnitTest --tests '*EmergencyNumberPolicyTest' --tests '*CallAutonomyDeciderTest'
```

- [ ] **Step 6: Commit**

```bash
git add core/telephony/src/main/kotlin/com/agendroid/core/telephony core/telephony/src/test/kotlin/com/agendroid/core/telephony
git commit -m "feat(telephony): add call screening policy and emergency safeguards"
```

---

## Task 3: Active call state repository

**Files:**
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallTranscriptLine.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallSession.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallSessionRepository.kt`
- Create: `core/telephony/src/test/kotlin/com/agendroid/core/telephony/CallSessionRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

Cover:

- starting a session exposes caller number + mode
- appending transcript lines preserves order
- `requestTakeover()` flips the session to user-controlled
- clearing the session resets state to idle

- [ ] **Step 2: Implement a small immutable session model**

Suggested shape:

```kotlin
data class CallTranscriptLine(
    val speaker: Speaker,
    val text: String,
    val timestampMs: Long,
) {
    enum class Speaker { CALLER, ASSISTANT, USER, SYSTEM }
}

data class CallSession(
    val callId: String,
    val number: String?,
    val mode: CallAutonomyMode,
    val transcript: List<CallTranscriptLine> = emptyList(),
    val isAiHandling: Boolean = false,
    val isTakeoverRequested: Boolean = false,
)
```

- [ ] **Step 3: Implement `CallSessionRepository` as a `@Singleton`**

Expose:

- `val activeSession: StateFlow<CallSession?>`
- `fun startSession(...)`
- `fun appendTranscript(...)`
- `fun setAiHandling(...)`
- `fun requestTakeover()`
- `fun clearSession()`

Use `MutableStateFlow` and immutable copies only.

- [ ] **Step 4: Verify JVM tests**

Run:

```bash
./gradlew :core:telephony:testDebugUnitTest --tests '*CallSessionRepositoryTest'
```

- [ ] **Step 5: Commit**

```bash
git add core/telephony/src/main/kotlin/com/agendroid/core/telephony core/telephony/src/test/kotlin/com/agendroid/core/telephony
git commit -m "feat(telephony): add active call session repository"
```

---

## Task 4: AI binding and call agent coordinator

**Files:**
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AiServiceConnector.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallAudioBridge.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/TelephonyCoordinator.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/TelephonyModule.kt`
- Create: `core/telephony/src/test/kotlin/com/agendroid/core/telephony/TelephonyCoordinatorTest.kt`

- [ ] **Step 1: Write failing coordinator tests**

Mock the collaborators and cover:

- `SCREEN_ONLY` mode starts transcription only, never TTS reply generation
- `FULL_AGENT` mode transcribes caller speech, calls `AiServiceInterface.generateResponse`, then synthesizes reply
- `requestTakeover()` stops AI speaking and marks the repository as user-controlled
- empty STT result increments a consecutive-failure counter

- [ ] **Step 2: Implement `AiServiceConnector`**

This class should own the Android `ServiceConnection` to `AiCoreService` and expose:

```kotlin
suspend fun get(): AiServiceInterface
fun unbind()
```

Keep it internal to `:core:telephony`.

- [ ] **Step 3: Implement `CallAudioBridge`**

Port the spike's audio-routing behavior into a focused class that handles:

- `AudioRecord` with `VOICE_COMMUNICATION`
- `AudioTrack` for spoken responses
- start/stop duplex resources safely
- immediate stop for take-over

Do not talk to the AI layer directly here.

- [ ] **Step 4: Implement `TelephonyCoordinator`**

Responsibilities:

- bind to `AiCoreService`
- load/use `WhisperEngine` and `KokoroEngine`
- append transcript lines into `CallSessionRepository`
- honor `SCREEN_ONLY`, `FULL_AGENT`, `PASS_THROUGH`
- stop AI output immediately on take-over request
- enforce safeguards from spec §11.7:
  - maximum 10 AI turns
  - hand off after 3 consecutive empty STT turns

Keep call-loop business logic here, not in Android service subclasses.

- [ ] **Step 5: Provide dependencies with Hilt**

`TelephonyModule` should provide:

- `AiServiceConnector`
- `CallAudioBridge`
- `TelephonyCoordinator`

- [ ] **Step 6: Verify JVM tests**

Run:

```bash
./gradlew :core:telephony:testDebugUnitTest --tests '*TelephonyCoordinatorTest'
```

- [ ] **Step 7: Commit**

```bash
git add core/telephony/src/main/kotlin/com/agendroid/core/telephony core/telephony/src/test/kotlin/com/agendroid/core/telephony
git commit -m "feat(telephony): add AI call coordinator and audio bridge"
```

---

## Task 5: Production Android Telecom services

**Files:**
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidCallScreeningService.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidInCallService.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidConnectionService.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/PhoneAccountRegistrar.kt`
- Create: `core/telephony/src/androidTest/kotlin/com/agendroid/core/telephony/PhoneAccountRegistrarInstrumentedTest.kt`
- Create: `core/telephony/src/androidTest/kotlin/com/agendroid/core/telephony/TelephonyManifestInstrumentedTest.kt`

- [ ] **Step 1: Implement `AgendroidCallScreeningService`**

Behavior:

- normalize incoming number
- compute `CallScreeningDecision`
- always pass through emergency calls
- default to pass-through if AI service/model is unavailable
- for AI-handled modes, allow the call through so `InCallService` receives it

Use `respondToCall(...)` with:

```kotlin
CallResponse.Builder()
    .setDisallowCall(false)
    .setRejectCall(false)
    .setSilenceCall(false)
    .build()
```

Plan 6 should not silently reject or block any call.

- [ ] **Step 2: Implement `AgendroidInCallService`**

Responsibilities:

- on `onCallAdded`, start a session in `CallSessionRepository`
- answer only when autonomy decision is `FULL_AGENT`
- route call control to `TelephonyCoordinator`
- expose a binder or singleton-backed repository for later UI consumers
- on `onCallRemoved`, stop audio and clear session

Reuse the spike's proven answer/start/stop flow, but keep Android callbacks thin.

- [ ] **Step 3: Implement `AgendroidConnectionService`**

Create a minimal `ConnectionService` that can host future AI-assisted outgoing calls, but enforce the spec's safety rule: no autonomous outgoing calls. The service should require an explicit user-started request from a higher layer later.

- [ ] **Step 4: Implement `PhoneAccountRegistrar`**

Register a `PhoneAccountHandle` for `AgendroidConnectionService` via `TelecomManager` and expose:

- `fun register()`
- `fun unregister()`
- `fun isRegistered(): Boolean`

- [ ] **Step 5: Add instrumented tests**

At minimum:

- `PhoneAccountRegistrarInstrumentedTest`
  - register -> account visible
  - unregister -> account removed
- `TelephonyManifestInstrumentedTest`
  - `PackageManager` resolves the screening, in-call, and connection services

- [ ] **Step 6: Verify builds + tests**

Run:

```bash
./gradlew :core:telephony:assembleDebug
./gradlew :core:telephony:testDebugUnitTest
```

If a device is connected, also run:

```bash
./gradlew :core:telephony:connectedDebugAndroidTest
```

- [ ] **Step 7: Commit**

```bash
git add core/telephony
git commit -m "feat(telephony): add production Telecom services and phone account registration"
```

---

## Task 6: App-level verification and manual device checklist

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` only if manifest merge or dialer-role entry points need correction

- [ ] **Step 1: Assemble the app**

Run:

```bash
./gradlew assembleDebug
```

- [ ] **Step 2: Install on device and verify service registration**

Run:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dumpsys package com.agendroid | rg 'Agendroid(CallScreening|InCall|Connection)Service'
```

- [ ] **Step 3: Verify default dialer flow**

Manual checklist:

- set Agendroid as default dialer
- place a real inbound test call
- confirm `AgendroidCallScreeningService` fires
- confirm `AgendroidInCallService` receives `onCallAdded`
- confirm take-over stops AI audio immediately
- confirm emergency numbers still pass through untouched

- [ ] **Step 4: Save verification notes**

Append a short note to the implementation PR or commit message body with:

- device model
- Android version
- whether full-agent audio path worked
- whether take-over stayed under 200ms
- any OEM-specific call-routing quirks

- [ ] **Step 5: Commit any final manifest fixups**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix(app): finalize telephony integration manifest wiring"
```

---

## Implementation notes

- Keep Android framework callbacks tiny. Put all branching and safety logic in pure Kotlin classes with JVM tests.
- Do not let `CallScreeningService` block waiting on model load or long async work. Screening should decide quickly and hand control to `InCallService`.
- `AiCoreService` already declares `foregroundServiceType="microphone|phoneCall"` in `:core:ai`; do not duplicate a second foreground service for the same work unless Android requires it.
- `CallSessionRepository` is the integration seam for later `:feature:phone` UI work. Avoid pushing UI concerns into `:core:telephony`.
- Prefer reusing the spike's measured-good audio behavior, but replace logcat-driven state with repository-driven state.
