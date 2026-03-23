# Plan 7: V1 Product Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining product work required to ship Agendroid v1 from the current codebase: SMS autonomy, phone UI, assistant UI, app shell/onboarding, reliability/compliance hardening, and final device validation.

**Architecture:** Keep the existing modular split and current sherpa-onnx-based voice implementation. Finish v1 in vertical slices: first add shared settings/data foundations, then complete SMS automation, harden the telephony call-agent path, build the feature UIs, wire app navigation/onboarding, and close with reliability plus device-level verification. Optional integrations remain extension points only for v1.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Hilt, WorkManager, Android Telecom APIs, Android SMS/Notification APIs, MediaPipe Tasks GenAI, sherpa-onnx Android AAR, Room + SQLCipher + sqlite-vec, JUnit 5 + MockK + Coroutines Test, AndroidX instrumentation, Espresso/UI Automator.

---

## Scope Decisions For This Plan

This spec spans multiple independent subsystems, so this document is an ordered umbrella plan for reaching v1 from the current state. Each task should land a working, testable slice before moving on.

### In scope for v1

- SMS threads/conversation UI and AI reply autonomy (`AUTO`, `SEMI`, `MANUAL`)
- Incoming SMS receiver, delayed send/cancel flow, draft approval flow, per-contact overrides
- Phone UI: dialer, call log, in-call transcript/take-over surface
- Telephony autonomy modes: `FULL_AGENT`, `SCREEN_ONLY`, `PASS_THROUGH`
- Assistant feature: voice entry point, autonomy settings, Knowledge Base UI, model/settings surfaces
- Knowledge ingestion for local documents and URLs into the existing RAG pipeline
- App shell: navigation, onboarding, role + permission guidance, degraded-state UX
- Reliability/compliance safeguards from spec section 11

### Explicitly out of scope for v1

- Home Ollama routing
- CalDAV/CardDAV sync
- IMAP/email ingestion
- Custom wake-word training beyond the current packaged keyword model

### Current-state constraints this plan assumes

- Keep the current sherpa-onnx `WhisperEngine`, `KokoroEngine`, and `WakeWordDetector`
- Keep the current `AiCoreService` binding model and extend it, do not replace it
- Build the product around the code already present in `:core:data`, `:core:embeddings`, `:core:ai`, `:core:voice`, and `:core:telephony`

---

## Current State Snapshot

- `:core:data`, `:core:embeddings`, `:core:ai`, `:core:voice`, and most of `:core:telephony` exist and compile.
- `:feature:sms`, `:feature:phone`, and `:feature:assistant` only have build files plus empty manifests.
- `app/src/main/kotlin/com/agendroid/MainActivity.kt` is still a placeholder with no nav graph.
- There is no `SmsReceiver`, no onboarding flow, no Knowledge Base UI, no app-level degraded-state indicator, and no end-to-end device verification harness for the product flows in the spec.

---

## File Map

### App shell

- Modify: `app/src/main/kotlin/com/agendroid/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/agendroid/navigation/AgendroidDestination.kt`
- Create: `app/src/main/kotlin/com/agendroid/navigation/AgendroidNavHost.kt`
- Create: `app/src/main/kotlin/com/agendroid/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/kotlin/com/agendroid/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/kotlin/com/agendroid/onboarding/RoleSetupHelper.kt`
- Create: `app/src/androidTest/kotlin/com/agendroid/OnboardingSmokeTest.kt`

### Shared settings and repositories

- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabaseMigrations.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/AppSettingsEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/PendingSmsReplyEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/IndexedSourceEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/AppSettingsDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/PendingSmsReplyDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/IndexedSourceDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/AppSettingsRepository.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/PendingSmsReplyRepository.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/IndexedSourceRepository.kt`

### AI service client + ingestion

- Create: `core/ai/src/main/kotlin/com/agendroid/core/ai/AiServiceClient.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeIngestionWorker.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/DocumentTextExtractor.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/UrlContentFetcher.kt`
- Create: `feature/assistant/src/test/kotlin/com/agendroid/feature/assistant/knowledge/DocumentTextExtractorTest.kt`

### SMS feature

- Modify: `feature/sms/src/main/AndroidManifest.xml`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/SmsReceiver.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyMode.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyPolicy.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/IncomingSmsProcessor.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyWorker.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsDraftNotificationManager.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/SmsNavGraph.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/threads/SmsThreadsViewModel.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/threads/SmsThreadsScreen.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/conversation/ConversationViewModel.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/conversation/ConversationScreen.kt`
- Create: `feature/sms/src/test/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyPolicyTest.kt`
- Create: `feature/sms/src/test/kotlin/com/agendroid/feature/sms/autonomy/IncomingSmsProcessorTest.kt`

### Telephony + phone UI

- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidCallScreeningService.kt`
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidInCallService.kt`
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/TelephonyCoordinator.kt`
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallSession.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallAgentLoop.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallDisclosurePrompt.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallTransferPhraseMatcher.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallSummaryRecorder.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/PhoneNavGraph.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/dialer/DialerViewModel.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/dialer/DialerScreen.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/calllog/CallLogViewModel.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/calllog/CallLogScreen.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/incall/InCallViewModel.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/incall/InCallScreen.kt`

### Assistant feature

- Modify: `feature/assistant/src/main/AndroidManifest.xml`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/AssistantNavGraph.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/overlay/AssistantOverlayViewModel.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/overlay/AssistantOverlayScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/settings/AutonomySettingsViewModel.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/settings/AutonomySettingsScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/settings/ModelSettingsScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeBaseViewModel.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeBaseScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/integrations/IntegrationsSettingsScreen.kt`

---

## Task 1: Shared Settings, Persistence, And AI Client Foundations

**Files:**
- Modify: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabase.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/db/AppDatabaseMigrations.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/AppSettingsEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/PendingSmsReplyEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/entity/IndexedSourceEntity.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/AppSettingsDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/PendingSmsReplyDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/dao/IndexedSourceDao.kt`
- Create: `core/data/src/main/kotlin/com/agendroid/core/data/repository/AppSettingsRepository.kt`
- Create: `core/ai/src/main/kotlin/com/agendroid/core/ai/AiServiceClient.kt`
- Test: `core/data/src/androidTest/kotlin/com/agendroid/core/data/db/AppDatabaseMigrationTest.kt`
- Test: `core/ai/src/androidTest/kotlin/com/agendroid/core/ai/AiServiceClientTest.kt`

- [ ] **Step 1: Write the failing database tests**

Add instrumented tests that expect:

```kotlin
@Test
fun migration_1_to_2_preserves_existing_tables_and_creates_settings_tables()

@Test
fun appSettings_roundTrips_global_autonomy_and_model_choice()
```

- [ ] **Step 2: Run the new data tests to verify they fail**

Run: `./gradlew :core:data:connectedDebugAndroidTest --tests '*AppDatabaseMigrationTest'`
Expected: FAIL because the new entities/DAOs/migration do not exist yet.

- [ ] **Step 3: Implement the new Room entities, DAOs, and migration**

Use focused tables only:

```kotlin
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val smsAutonomyMode: String,
    val callAutonomyMode: String,
    val assistantEnabled: Boolean,
    val selectedModel: String,
)
```

Add parallel tables for pending SMS drafts and indexed sources; bump the Room version and register a `MIGRATION_1_2`.

- [ ] **Step 4: Write the failing AI client binding test**

Create an instrumented test for a reusable binder client:

```kotlin
@Test
fun get_binds_to_AiCoreService_and_returns_interface()
```

- [ ] **Step 5: Implement `AiServiceClient` and switch `:core:telephony` to use it**

Move the generic bind/unbind logic out of telephony-only code so `:feature:sms` and `:feature:assistant` can reuse it. Keep `AiCoreService` unchanged except for any small public API needed by the client.

- [ ] **Step 6: Verify the foundation layer**

Run:

```bash
./gradlew :core:data:connectedDebugAndroidTest --tests '*AppDatabaseMigrationTest'
./gradlew :core:ai:connectedDebugAndroidTest --tests '*AiServiceClientTest'
./gradlew :core:telephony:testDebugUnitTest
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add core/data core/ai core/telephony
git commit -m "feat(v1): add shared settings schema and reusable AI service client"
```

---

## Task 2: SMS Autonomy Backend

**Files:**
- Modify: `feature/sms/src/main/AndroidManifest.xml`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/SmsReceiver.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyMode.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyPolicy.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/IncomingSmsProcessor.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyWorker.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/autonomy/SmsDraftNotificationManager.kt`
- Test: `feature/sms/src/test/kotlin/com/agendroid/feature/sms/autonomy/SmsAutonomyPolicyTest.kt`
- Test: `feature/sms/src/test/kotlin/com/agendroid/feature/sms/autonomy/IncomingSmsProcessorTest.kt`

- [ ] **Step 1: Write the failing autonomy policy tests**

Cover:

```kotlin
@Test fun `contact override wins over global setting`()
@Test fun `auto mode schedules delayed send`()
@Test fun `semi mode stores draft and shows approval notification`()
@Test fun `manual mode only posts notification`()
```

- [ ] **Step 2: Run the new SMS backend tests to verify they fail**

Run: `./gradlew :feature:sms:testDebugUnitTest --tests '*SmsAutonomyPolicyTest'`
Expected: FAIL because the policy and processor do not exist.

- [ ] **Step 3: Implement the domain types and processor**

Start with:

```kotlin
enum class SmsAutonomyMode { AUTO, SEMI, MANUAL }

data class SmsAutonomyDecision(
    val mode: SmsAutonomyMode,
    val shouldScheduleSend: Boolean,
    val shouldPersistDraft: Boolean,
    val shouldNotify: Boolean,
)
```

`IncomingSmsProcessor` should:
- read sender context from `SmsThreadRepository` and contact preferences
- request a draft from `AiServiceClient`
- choose the action from settings/contact override
- enqueue WorkManager or notification work

- [ ] **Step 4: Add the broadcast receiver and worker flow**

Register `SmsReceiver` in the feature manifest and enqueue `SmsAutonomyWorker` with the thread ID, sender, and message body.

- [ ] **Step 5: Implement cancel/approve notification actions**

Persist pending replies in Room so `AUTO` can be cancelled inside 10 seconds and `SEMI` can survive process death until the user approves or expires.

- [ ] **Step 6: Verify the SMS backend**

Run:

```bash
./gradlew :feature:sms:testDebugUnitTest
./gradlew :app:assembleDebug
```

Expected: PASS and receiver manifest merges cleanly.

- [ ] **Step 7: Commit**

```bash
git add feature/sms core/data app
git commit -m "feat(sms): add incoming SMS autonomy backend"
```

---

## Task 3: SMS Threads, Conversation, And Compose UI

**Files:**
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/SmsNavGraph.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/threads/SmsThreadsViewModel.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/threads/SmsThreadsScreen.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/threads/SmsThreadRow.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/conversation/ConversationViewModel.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/conversation/ConversationScreen.kt`
- Create: `feature/sms/src/main/kotlin/com/agendroid/feature/sms/ui/conversation/ComposeReplyBar.kt`
- Test: `feature/sms/src/test/kotlin/com/agendroid/feature/sms/ui/ConversationViewModelTest.kt`

- [ ] **Step 1: Write the failing view-model tests**

Cover thread load, conversation load, and send action:

```kotlin
@Test fun `threads screen emits latest threads ordered by date`()
@Test fun `conversation screen sends reply via repository and refreshes messages`()
```

- [ ] **Step 2: Run the new UI tests to verify they fail**

Run: `./gradlew :feature:sms:testDebugUnitTest --tests '*ConversationViewModelTest'`
Expected: FAIL because the view-models and routes do not exist.

- [ ] **Step 3: Implement the view-models and state holders**

Keep them thin: repositories in, `StateFlow` out.

- [ ] **Step 4: Build the Compose screens**

Implement:
- thread list with unread badge and snippet
- conversation view with message bubbles
- compose bar with send action
- banners for pending AI draft / cancelled auto-send when applicable

- [ ] **Step 5: Verify the feature module**

Run:

```bash
./gradlew :feature:sms:testDebugUnitTest
./gradlew :feature:sms:assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/sms
git commit -m "feat(sms): add SMS threads and conversation UI"
```

---

## Task 4: Telephony Full-Agent Hardening

**Files:**
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidCallScreeningService.kt`
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/AgendroidInCallService.kt`
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/TelephonyCoordinator.kt`
- Modify: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallSession.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallAgentLoop.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallDisclosurePrompt.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallTransferPhraseMatcher.kt`
- Create: `core/telephony/src/main/kotlin/com/agendroid/core/telephony/CallSummaryRecorder.kt`
- Test: `core/telephony/src/test/kotlin/com/agendroid/core/telephony/CallTransferPhraseMatcherTest.kt`
- Test: `core/telephony/src/test/kotlin/com/agendroid/core/telephony/CallAgentLoopTest.kt`

- [ ] **Step 1: Write the failing telephony safeguard tests**

Cover:
- screen-only mode does not auto-answer
- disclosure prompt is always spoken first
- caller saying "human" or "talk to [name]" triggers hand-off
- 3 consecutive empty STT turns triggers hand-off
- post-call summary is persisted

- [ ] **Step 2: Run the focused telephony tests to verify they fail**

Run: `./gradlew :core:telephony:testDebugUnitTest --tests '*CallAgentLoopTest'`
Expected: FAIL because the loop, disclosure prompt, and transfer matcher do not exist.

- [ ] **Step 3: Implement autonomy-aware screening and in-call startup**

`AgendroidCallScreeningService` must consult persisted settings and `CallAutonomyDecider`, not hardcode allow/full-agent behavior. `AgendroidInCallService` must use the resolved mode instead of always forcing `FULL_AGENT`.

- [ ] **Step 4: Implement the call-agent loop**

`CallAgentLoop` should own:
- initial disclosure greeting
- caller transcription loop
- AI response generation
- TTS playback
- loop-break safeguards from spec section 11.7

- [ ] **Step 5: Persist call summaries and transcript state**

Use `ConversationSummaryDao` for call summary storage and keep transcript lines live in `CallSessionRepository` for the feature UI.

- [ ] **Step 6: Verify telephony**

Run:

```bash
./gradlew :core:telephony:testDebugUnitTest
./gradlew :core:telephony:assembleDebug
```

If a device is attached, also run:

```bash
./gradlew :core:telephony:connectedDebugAndroidTest
```

- [ ] **Step 7: Commit**

```bash
git add core/telephony core/data
git commit -m "feat(telephony): harden full-agent call flow for v1"
```

---

## Task 5: Phone UI (Dialer, Call Log, In-Call)

**Files:**
- Modify: `feature/phone/src/main/AndroidManifest.xml`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/PhoneNavGraph.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/dialer/DialerViewModel.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/dialer/DialerScreen.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/calllog/CallLogViewModel.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/calllog/CallLogScreen.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/incall/InCallViewModel.kt`
- Create: `feature/phone/src/main/kotlin/com/agendroid/feature/phone/incall/InCallScreen.kt`
- Test: `feature/phone/src/test/kotlin/com/agendroid/feature/phone/incall/InCallViewModelTest.kt`

- [ ] **Step 1: Write the failing phone view-model tests**

Cover:
- call log state loads from repository
- active call transcript maps from `CallSessionRepository`
- take-over button calls `TelephonyCoordinator.requestTakeover()`

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:phone:testDebugUnitTest --tests '*InCallViewModelTest'`
Expected: FAIL because the feature UI does not exist.

- [ ] **Step 3: Implement the view-models**

Expose:
- dialer input + place-call intent state
- call-log list state
- in-call transcript, autonomy mode, assistant speaking flag, take-over action

- [ ] **Step 4: Build the Compose screens**

The in-call screen must visibly show:
- disclosure status
- live transcript
- AI/user speaking state
- large take-over CTA

- [ ] **Step 5: Verify the phone feature**

Run:

```bash
./gradlew :feature:phone:testDebugUnitTest
./gradlew :feature:phone:assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add feature/phone
git commit -m "feat(phone): add dialer call log and in-call UI"
```

---

## Task 6: Knowledge Ingestion Backend

**Files:**
- Modify: `feature/assistant/build.gradle.kts`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeIngestionWorker.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/DocumentTextExtractor.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/UrlContentFetcher.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeIngestionScheduler.kt`
- Create: `feature/assistant/src/test/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeIngestionWorkerTest.kt`

- [ ] **Step 1: Add the ingestion dependencies**

Add only the libraries v1 needs:
- HTML parsing (`jsoup`)
- PDF text extraction (`pdfbox-android` or equivalent Android-safe parser)

- [ ] **Step 2: Write the failing ingestion tests**

Cover:
- local text/PDF file becomes chunks + embeddings + vectors
- URL content becomes a stored indexed source
- deleting a source re-queues re-index cleanup

- [ ] **Step 3: Run the ingestion tests to verify they fail**

Run: `./gradlew :feature:assistant:testDebugUnitTest --tests '*KnowledgeIngestionWorkerTest'`

- [ ] **Step 4: Implement extraction and worker flow**

The worker should:
- read the selected file or fetched URL
- convert to plain text
- chunk via `TextChunker`
- embed via `EmbeddingModel`
- persist through `KnowledgeIndexRepository`
- record the source in `IndexedSourceRepository`

- [ ] **Step 5: Verify ingestion**

Run:

```bash
./gradlew :feature:assistant:testDebugUnitTest
./gradlew :app:assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add feature/assistant core/data gradle/libs.versions.toml
git commit -m "feat(assistant): add document and URL knowledge ingestion backend"
```

---

## Task 7: Assistant UI, Settings, And Voice Entry

**Files:**
- Modify: `feature/assistant/src/main/AndroidManifest.xml`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/AssistantNavGraph.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/overlay/AssistantOverlayViewModel.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/overlay/AssistantOverlayScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/settings/AutonomySettingsViewModel.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/settings/AutonomySettingsScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/settings/ModelSettingsScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeBaseViewModel.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/knowledge/KnowledgeBaseScreen.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/integrations/IntegrationsSettingsScreen.kt`
- Test: `feature/assistant/src/test/kotlin/com/agendroid/feature/assistant/settings/AutonomySettingsViewModelTest.kt`

- [ ] **Step 1: Write the failing assistant settings tests**

Cover:
- changing SMS/call autonomy persists to `AppSettingsRepository`
- model selection persists and updates the UI state
- indexed-source list loads from repository

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :feature:assistant:testDebugUnitTest --tests '*AutonomySettingsViewModelTest'`

- [ ] **Step 3: Implement the view-models**

Keep one responsibility per screen:
- overlay interaction state
- autonomy settings state
- knowledge-base source list and add/delete actions

- [ ] **Step 4: Build the Compose surfaces**

V1 assistant UI must include:
- in-app voice entry sheet/panel
- autonomy settings
- model settings
- Knowledge Base source list with Add Document / Add URL actions
- integrations page with disabled placeholders for post-v1 items

- [ ] **Step 5: Wire wake-word activation to the assistant surface**

Use the existing `WakeWordDetector` to open/focus the assistant UI path while the app is foregrounded. Do not add a second always-on service for v1.

- [ ] **Step 6: Verify the assistant feature**

Run:

```bash
./gradlew :feature:assistant:testDebugUnitTest
./gradlew :feature:assistant:assembleDebug
```

- [ ] **Step 7: Commit**

```bash
git add feature/assistant core/voice
git commit -m "feat(assistant): add overlay settings and knowledge base UI"
```

---

## Task 8: App Navigation, Onboarding, Roles, And Permissions

**Files:**
- Modify: `app/src/main/kotlin/com/agendroid/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/agendroid/navigation/AgendroidDestination.kt`
- Create: `app/src/main/kotlin/com/agendroid/navigation/AgendroidNavHost.kt`
- Create: `app/src/main/kotlin/com/agendroid/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/kotlin/com/agendroid/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/kotlin/com/agendroid/onboarding/RoleSetupHelper.kt`
- Test: `app/src/androidTest/kotlin/com/agendroid/OnboardingSmokeTest.kt`

- [ ] **Step 1: Write the failing onboarding smoke test**

Cover:
- app launches into onboarding when roles/permissions are missing
- completed onboarding routes to the main nav host

- [ ] **Step 2: Run the onboarding test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests '*OnboardingSmokeTest'`

- [ ] **Step 3: Implement app navigation**

Start with:

```kotlin
sealed interface AgendroidDestination {
    data object Onboarding : AgendroidDestination
    data object Sms : AgendroidDestination
    data object Phone : AgendroidDestination
    data object Assistant : AgendroidDestination
}
```

`AgendroidNavHost` should host the three feature nav graphs plus onboarding.

- [ ] **Step 4: Implement role + permission onboarding**

Guide the user through:
- default SMS role
- default dialer role
- runtime permissions from spec section 7
- battery optimization exemption explanation

- [ ] **Step 5: Verify the app shell**

Run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest --tests '*OnboardingSmokeTest'
```

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): add navigation and onboarding flow"
```

---

## Task 9: Reliability, Compliance, And V1 Verification

**Files:**
- Modify: `core/ai/src/main/kotlin/com/agendroid/core/ai/AiCoreService.kt`
- Modify: `core/ai/src/main/kotlin/com/agendroid/core/ai/ResourceMonitor.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/agendroid/boot/BootCompletedReceiver.kt`
- Create: `feature/assistant/src/main/kotlin/com/agendroid/feature/assistant/health/ServiceHealthWatchdog.kt`
- Create: `app/src/androidTest/kotlin/com/agendroid/V1SmokeTest.kt`
- Create: `docs/superpowers/specs/2026-03-23-v1-verification-notes.md`

- [ ] **Step 1: Write the failing reliability/compliance tests**

Cover:
- `AiCoreService` restart path after bind/disconnect
- watchdog rebind signal
- boot receiver starts the service
- degraded-state indicator surfaces from resource state

- [ ] **Step 2: Run the failing tests**

Run:

```bash
./gradlew :core:ai:connectedDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest --tests '*V1SmokeTest'
```

- [ ] **Step 3: Implement the reliability and compliance pieces**

Add:
- boot-completed receiver
- service watchdog
- universal AI call disclosure
- degraded-state banners
- SMS/call failure fallback messaging

- [ ] **Step 4: Run the full verification suite**

Run:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Expected: all product-critical module tests PASS.

- [ ] **Step 5: Perform the manual device checklist and save notes**

Manual v1 checklist:
- onboarding roles complete
- incoming SMS auto mode sends after delay and cancel works
- semi mode approval notification works
- wake word opens assistant surface
- incoming call shows transcript and take-over works
- emergency calls pass through untouched

Save results to `docs/superpowers/specs/2026-03-23-v1-verification-notes.md`.

- [ ] **Step 6: Commit**

```bash
git add app core/ai feature/assistant docs/superpowers/specs/2026-03-23-v1-verification-notes.md
git commit -m "chore(v1): complete reliability hardening and verification"
```

---

## Release Gate Checklist

- [ ] `./gradlew assembleDebug` passes from a clean checkout
- [ ] `./gradlew testDebugUnitTest` passes across all modules
- [ ] `./gradlew connectedDebugAndroidTest` passes on a real device for core smoke tests
- [ ] SMS auto/semi/manual behavior works end-to-end
- [ ] In-call transcript and take-over path work end-to-end
- [ ] Knowledge Base can ingest at least one local document and one URL
- [ ] Onboarding successfully sets up default SMS + dialer roles
- [ ] Call disclosure and emergency bypass behaviors match spec section 11
- [ ] Verification notes saved and reviewed before release tagging
