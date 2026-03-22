// core/ai/src/main/kotlin/com/agendroid/core/ai/AiCoreService.kt
package com.agendroid.core.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

private const val NOTIF_CHANNEL_ID = "ai_core_service"
private const val NOTIF_ID         = 1001

/**
 * Persistent foreground service that owns the LLM runtime and RAG pipeline.
 *
 * Feature modules bind to this service to access [AiServiceInterface].
 * The LLM is loaded lazily on the first [generateResponse] call.
 *
 * Foreground service types: `microphone` (always-listening wake word, Plan 5)
 * and `phoneCall` (call-agent mode, Plan 6). Both are declared in AndroidManifest.xml.
 *
 * Lifecycle: Android's system may kill and restart this service (START_STICKY).
 * After restart, feature modules detect the disconnect via [ServiceConnection.onServiceDisconnected]
 * and rebind. The health watchdog (implemented in feature modules per spec §11.4) pings
 * every 30 s and rebinds if unresponsive.
 */
@AndroidEntryPoint
class AiCoreService : Service(), AiServiceInterface {

    @Inject lateinit var ragOrchestrator: RagOrchestrator
    @Inject lateinit var resourceMonitor: ResourceMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val llmMutex     = Mutex()
    private lateinit var llmEngine: LlmInferenceEngine

    private lateinit var sharedResourceState: Flow<ResourceState>

    // AiServiceInterface

    override fun isModelAvailable(): Boolean = llmEngine.isModelAvailable()

    override val resourceState: Flow<ResourceState> get() = sharedResourceState

    override suspend fun generateResponse(
        userQuery: String,
        contactFilter: String?,
        conversationHistory: List<String>,
        onToken: (String, Boolean) -> Unit,
    ): String = llmMutex.withLock {
        llmEngine.load()   // idempotent — no-op if already loaded
        val prompt = ragOrchestrator.buildPrompt(userQuery, contactFilter, conversationHistory)
        llmEngine.generate(prompt, onToken)
    }

    // Service lifecycle

    inner class LocalBinder : Binder() {
        fun getInterface(): AiServiceInterface = this@AiCoreService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        llmEngine = LlmInferenceEngine(applicationContext)
        sharedResourceState = resourceMonitor.stateFlow
            .shareIn(serviceScope, SharingStarted.WhileSubscribed(5_000L), replay = 1)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        // Wait for any active generateResponse to finish before releasing the native handle.
        // runBlocking is acceptable here because onDestroy is called on the main thread
        // and the LLM has a 30 s timeout that bounds the worst-case wait.
        @Suppress("BlockingMethodInNonBlockingContext")
        runBlocking { llmMutex.withLock { llmEngine.close() } }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL_ID, "AI Assistant", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "AI assistant is running" }
            )
        }
        return Notification.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("AI Assistant")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
