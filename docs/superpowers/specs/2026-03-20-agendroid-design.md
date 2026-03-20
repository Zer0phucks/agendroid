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

---

## 11. Engineering Readiness Addendum

*Added in response to pre-implementation technical review. Addresses execution risks before coding begins.*

### 11.1 Call Pipeline Latency SLOs

The full-agent call loop (end of caller speech → start of AI spoken response) must complete within **2 seconds** to feel conversational. Hard budgets per stage:

| Pipeline stage | Budget | Notes |
| --- | --- | --- |
| VAD (end-of-speech detection) | ≤ 100ms | WebRTC VAD or Energy VAD |
| STT (Whisper Small) | ≤ 500ms | Runs on CPU; GPU delegate experimental |
| RAG retrieval (sqlite-vec) | ≤ 50ms | ANN search is fast; acceptable |
| LLM first token (Gemma 4B, Adreno GPU) | ≤ 500ms | Prefill of short prompt |
| LLM full response (≤ 25 tokens) | ≤ 600ms | At 40 tok/s |
| TTS synthesis (Kokoro) | ≤ 200ms | Short utterances |
| **Total end-to-end** | **≤ 1.95s** | |

If the combined budget is exceeded (e.g., under thermal throttling), the fallback is: play a bridging phrase via TTS ("One moment…") while generation continues. A **2-week feasibility spike** on the call pipeline is required before the main implementation begins — success criterion is sustained sub-2s latency across 20 consecutive turns at room temperature on a OnePlus 12.

### 11.2 Resource Budgets & Thermal Fallback

**Memory budget:**

| Component | Estimated RAM |
| --- | --- |
| Gemma 3 4B (4-bit quantized) | ~2.5 GB |
| Whisper Small | ~150 MB |
| Kokoro TTS | ~350 MB |
| all-MiniLM-L6-v2 | ~22 MB |
| App + framework overhead | ~300 MB |
| **Total** | **~3.3 GB** |

This is well within the OnePlus 12's 12–16 GB RAM. However, OEM memory pressure management may still reclaim the foreground service — see §11.4.

**Thermal fallback matrix:**

| Device state | LLM model | Voice features | Auto-reply |
| --- | --- | --- | --- |
| Normal | Gemma 3 4B | Full | Enabled |
| Warm (>38°C SoC) | Gemma 3 1B | Full | Enabled |
| Hot (>42°C SoC) | Gemma 3 1B | STT only (no wake word) | Semi-auto only |
| Battery < 15% | Gemma 3 1B | Disabled | Semi-auto only |
| Battery < 10% | Off | Disabled | Disabled (notify only) |

SoC temperature is read from the Android thermal API (`ThermalManager`). `AiCoreService` monitors temperature and battery via broadcast receivers and switches model/feature sets accordingly, emitting a `Flow<ResourceState>` that feature modules observe to update their UI.

### 11.3 Security & Privacy Model

**At-rest encryption:**

- Room DB encrypted with SQLCipher; key stored in Android Keystore
- Documents in app-private storage encrypted with AES-256-GCM via Jetpack Security
- sqlite-vec database encrypted via SQLCipher

**Integration auth:**

- OAuth2 tokens (for CalDAV, IMAP) stored in Android Keystore-backed `EncryptedSharedPreferences`
- No credentials stored in plaintext anywhere

**Prompt injection defense:**

- User-controlled content (SMS body, document text, contact names) inserted into LLM prompts inside clearly delimited sections (e.g., `[USER DATA START] … [USER DATA END]`)
- System prompt is hardcoded and not modifiable by any ingested data
- RAG chunks are sanitized to strip control characters before insertion

**Data retention:**

- Vectors are re-indexed from source data; deleting a source (e.g., a document) re-queues re-indexing with that source excluded
- No telemetry or crash data sent to external servers; opt-in only via a future setting

### 11.4 Background Service Reliability

Android 14/15 and OEM battery optimizers (especially OnePlus OxygenOS) aggressively kill long-running foreground services. Mitigations:

- Request battery optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) during onboarding; explain why (persistent AI assistant requires it)
- `AiCoreService` uses correct foreground service types: `FOREGROUND_SERVICE_TYPE_MICROPHONE` (when wake word is active) + `FOREGROUND_SERVICE_TYPE_PHONE_CALL` (during calls)
- Feature modules implement a **health watchdog**: ping `AiCoreService` every 30 seconds via the bound service interface; if no response within 5 seconds, rebind and show a degraded state indicator
- `RECEIVE_BOOT_COMPLETED` restarts `AiCoreService` after reboot
- Persistent notification is non-dismissible (required by Android for foreground services with microphone)

### 11.5 Compliance & Consent UX

**Call disclosure (required):**

- Every AI-answered call must open with a disclosure: *"Hi, this is an AI assistant for [Name]. This call may be transcribed. Say 'talk to [Name]' at any time to reach them directly."*
- Disclosure is not skippable and not configurable off
- If the caller says any variant of "talk to a real person", "transfer me", "stop", or "human", the AI immediately hands the call to the user (keyword detection via simple string match on STT output, not LLM)

**Recording consent:**

- In two-party consent jurisdictions (e.g., California, Germany), the disclosure above satisfies the notification requirement
- The app does not determine jurisdiction automatically; it applies the disclosure universally to be safe
- Call transcripts are stored locally only; not uploaded

**SMS disclosure:**

- Auto-replied messages include a configurable footer (default: *"[Sent by AI assistant]"*); the user can customize or remove it, but removing it shows a one-time warning about impersonation risk

**Data access transparency:**

- A "What the AI knows about you" screen in `:feature:assistant` shows all indexed data sources, vector count per source, and allows deletion of individual sources

### 11.6 Permission Corrections

The original spec listed `CALL_PHONE` as a telephony permission. For Telecom-API-based call management (our architecture), the correct permission is `MANAGE_OWN_CALLS`, not `CALL_PHONE`. Corrected permission list:

- Remove: `CALL_PHONE` (for direct PSTN dialing, not needed with Telecom API)
- Retain: `MANAGE_OWN_CALLS` (registers `TelecomConnectionService` for outgoing call management)
- Note: `WRITE_CALL_LOG` requires the app to be the default dialer; this is already enforced by onboarding

Play Store policy note: the app uses `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, and `WRITE_SMS` — these require the app to be the default SMS handler (already required by onboarding) and will trigger Play Store review under the SMS permissions policy. The app must clearly state its default-SMS-app use case in the store listing.
