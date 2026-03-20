# Agendroid Design Spec

**Date:** 2026-03-20
**Status:** Approved

---

## Overview

Agendroid is an Android application that replaces both the default SMS app and default phone dialer with an AI-powered personal assistant. It runs a large language model entirely on-device, maintains a personal knowledge base via a vector database, and uses voice as the primary interaction layer. The AI can autonomously reply to SMS messages, handle incoming calls on the user's behalf, and be invoked by voice at any time.

**Target device:** Flagship Android (OnePlus 12, Pixel 8 Pro class) — Snapdragon 8 Gen 3, 12–16 GB RAM.
**Min SDK:** API 31 (Android 12). **Target SDK:** API 35 (Android 15).
**Language:** Kotlin. **UI:** Jetpack Compose + Material 3.

---

## 1. Interaction Model

Agendroid presents a full replacement app UI (SMS threads, contact list, dialer, in-call screen, call log) consistent with a standard Android phone/SMS experience, overlaid with a persistent AI voice layer. Users can interact with the app as they would any default messaging or phone app, or invoke the AI assistant — via wake word or tap — to act on their behalf.

---

## 2. Module Architecture

The project is structured as a **modular monorepo** — one APK, multiple Gradle modules with strict layer boundaries.

```text
:app                     Entry point, Hilt DI wiring, registers as default SMS + dialer
├── :feature:sms         SMS thread list, conversation view, compose screen
├── :feature:phone       Dialer, in-call screen, call log
├── :feature:assistant   Voice overlay, autonomy settings, integrations settings
├── :core:ai             AiCoreService, LLM inference, RAG orchestration
├── :core:voice          STT (Whisper), TTS (Kokoro), wake word (openWakeWord)
├── :core:telephony      CallScreeningService, InCallService, TelecomConnectionService
├── :core:data           Room DB, sqlite-vec, content provider access layer
├── :core:embeddings     Embedding model (all-MiniLM-L6-v2 via LiteRT)
└── :core:common         Shared models, DI interfaces, utilities
```

**Dependency rule:** feature modules depend on core modules only; core modules never depend on feature modules; all modules depend on `:core:common`.

### Key Android System Components

| Component | Role |
| --- | --- |
| `AiCoreService` (Foreground Service) | Persistent service owning the LLM runtime and RAG pipeline. All feature modules bind to it. Runs with a persistent notification. Auto-restarts via `START_STICKY`. |
| `CallScreeningService` | Intercepts incoming calls before ringing. Passes to AI agent in full-agent mode. |
| `InCallService` | Provides the custom in-call UI with live transcript and take-over controls. |
| `SmsReceiver` (BroadcastReceiver) | Receives incoming SMS, triggers AI pipeline based on autonomy level. |
| `TelecomConnectionService` | Manages custom call connections for outgoing AI-initiated calls. |

---

## 3. AI Stack

### LLM Inference

- **Model:** Gemma 3 4B (4-bit quantized, default) / Gemma 3 1B (fast mode, user-selectable)
- **Runtime:** LiteRT-LM (Google AI Edge) with GPU delegate (Adreno GPU)
- **Performance:** ~20–40 tokens/sec on Snapdragon 8 Gen 3
- **NPU:** Hexagon NPU delegate available as opt-in for further acceleration

### RAG Pipeline

**Ingestion (background, WorkManager):**

1. Data sources are read: contacts, SMS/MMS history, call logs, notes, calendar events, user-uploaded documents (PDFs, web pages)
2. Text is chunked into 512-token segments with 50-token overlap
3. Each chunk is embedded via `all-MiniLM-L6-v2` (22 MB, runs via LiteRT) → 384-dim float32 vector
4. Vectors and metadata stored in `sqlite-vec` (SQLite extension, app-private, no separate process)

Document ingestion (PDFs, web pages) is initiated from a **"Knowledge Base" screen** in `:feature:assistant`. The user taps "Add Document", picks a file via the system file picker or pastes a URL, and the ingestion WorkManager job is enqueued immediately.

**Query (real-time, on incoming SMS or voice input):**

1. Query text embedded with same MiniLM model
2. ANN search via sqlite-vec → top-5 cosine similarity results, optionally filtered by contact
3. Retrieved chunks + system prompt + conversation history assembled into LLM context
4. Gemma 3 generates response
5. Response routed to TTS, SMS send, or UI display

### Voice Stack

| Component | Technology |
| --- | --- |
| Speech-to-Text | Whisper Small via whisper.cpp JNI (whisper-android) |
| Text-to-Speech | Kokoro TTS (82M params, on-device, natural voice) |
| Wake Word | openWakeWord (free, on-device, always-listening) |

---

## 4. Call Handling — Full Agent Mode

Incoming call flow:

1. `CallScreeningService` intercepts the call before the device rings
2. RAG lookup fetches contact history and context for the caller's number
3. AI answers the call; TTS plays a greeting: *"Hi, this is [Name]'s assistant, how can I help?"*
4. Real-time loop:
   - `AudioRecord` captures caller audio → Whisper STT transcribes in real-time
   - LLM generates contextually appropriate response
   - Kokoro TTS speaks response back to caller via `AudioTrack`
5. User sees a live transcript on the `InCallService` screen at all times
6. User can tap **"Take Over"** at any moment — the AI audio stream is immediately silenced, the microphone is handed to the user's earpiece, and the call continues normally as a standard voice call. The transcript remains visible but the AI stops generating responses.
7. Post-call: AI generates a summary, logs it to Room DB and RAG, suggests follow-up actions

Autonomy for calls is independently configurable: full agent, screen-only (AI screens but user decides to answer), or pass-through (standard behavior).

---

## 5. SMS Autonomy

Autonomy is configurable globally and overridable per-contact.

| Level | Behavior | Cancel mechanism |
| --- | --- | --- |
| **Auto** | AI sends reply after a 10-second delay | Notification with "Cancel" action within window |
| **Semi** | AI shows draft reply in a notification, waits for user approval tap | Draft expires after 24h and is silently discarded; the original message remains unread in the thread |
| **Manual** | Notification only, no draft generated | N/A |

**SMS flow:**

1. `SmsReceiver` fires on incoming message
2. `AiCoreService` runs RAG retrieval for sender context
3. LLM drafts reply matching the established tone of prior conversation with that contact
4. Autonomy level determines next action (auto-send, show draft, or notify only)

---

## 6. Data Architecture

### Storage

| Data | Storage location | Access |
| --- | --- | --- |
| SMS / MMS | Android SMS Provider | Read/write (as default SMS app) |
| Contacts | Android ContactsProvider | Read-only mirror |
| Call logs | Android CallLog Provider | Read/write |
| Calendar | Android CalendarProvider | Read-only (opt-in) |
| Notes | Room DB (app-private) | Read/write |
| Documents | App-private file storage | Read/write |
| Vectors | sqlite-vec DB (app-private) | Read/write |
| AI summaries & preferences | Room DB (app-private) | Read/write |

All data remains on-device. No data is transmitted to external servers unless the user explicitly enables an optional integration.

### Optional Integrations (opt-in per integration)

- **Home Ollama server:** route LLM inference to a self-hosted server when on trusted Wi-Fi; enables larger models (13B+)
- **CalDAV/CardDAV:** calendar and contact sync with self-hosted or third-party services
- **Email (IMAP, read-only):** ingest email threads into RAG for additional context

---

## 7. Required Android Permissions

### Default App Roles (manual user action in Settings)

- `DEFAULT_SMS_APP`
- `DEFAULT_DIALER`

### Runtime Permissions

- **Telephony:** `READ_PHONE_STATE`, `CALL_PHONE`, `READ_CALL_LOG`, `WRITE_CALL_LOG`, `ANSWER_PHONE_CALLS`, `MANAGE_OWN_CALLS`
- **SMS:** `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `WRITE_SMS`
- **Contacts & Calendar:** `READ_CONTACTS`, `READ_CALENDAR`
- **Audio:** `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`
- **System:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`

**Permission strategy:** Request incrementally at the point a feature is first used. Onboarding flow guides the user through setting the default SMS and dialer roles with deep-links to the relevant Settings screens.

---

## 8. Testing Strategy

### Unit Tests (JVM — JUnit 5 + MockK + Coroutines Test)

- RAG chunker: correct token boundary detection, no chunk exceeds limit
- Embedding pipeline: output dimensions match expected (384), deterministic for same input
- Prompt builder: context window limits respected, correct assembly order
- Autonomy logic: correct behavior per level, per-contact override takes precedence
- SMS cancel window: suppression works within window, send proceeds after expiry

### Integration Tests (Android — AndroidX Test + Hilt Testing + Robolectric)

- sqlite-vec: vector round-trips, ANN search returns correct ranking for known embeddings
- Room DB: migrations, DAO queries, Flow emissions
- `AiCoreService` binding: feature modules bind/unbind cleanly, service survives config changes
- Content provider access: contacts and SMS read correctly on instrumented device

### E2E Tests (Device — UI Automator + Espresso)

- Onboarding: default SMS + dialer role assignment completes
- SMS auto-reply: reply sends after delay, cancel notification suppresses it within window
- Voice: wake word fires, voice input captured, TTS response plays
- Call screening: incoming test call intercepted, transcript appears on `InCallService` screen

---

## 9. Error Handling

| Failure scenario | Behavior |
| --- | --- |
| LLM inference fails / times out | Fall back to a canned template reply ("I'll get back to you shortly"). Log error. |
| STT timeout / prolonged silence during call | Prompt caller to repeat after 5s. After 2 consecutive failures, hand call to user. |
| `AiCoreService` killed by OS | `START_STICKY` auto-restart. Feature modules detect service disconnect and display degraded state indicator. |
| Vector DB corrupted / missing | Re-index all sources automatically in background. AI operates without RAG context until complete. |
| SMS send failure | Retry once. On second failure, notify user with the unsent draft for manual action. |
| Permission revoked at runtime | Graceful degradation — affected features disabled with clear in-app explanation and deep-link to Settings to restore. |

---

## 10. Build Configuration

- **Language:** Kotlin 2.x
- **Build system:** Gradle with Kotlin DSL (`build.gradle.kts`), version catalog (`libs.versions.toml`)
- **DI:** Hilt
- **Async:** Kotlin Coroutines + Flow throughout
- **UI:** Jetpack Compose, Material 3
- **Background:** WorkManager (RAG ingestion), Foreground Service (AI core)
- **Min SDK:** 31 (Android 12) — required for Telecom API improvements and `CallScreeningService`
- **Target SDK:** 35 (Android 15)
