package com.agendroid.feature.assistant.health

import com.agendroid.core.ai.AiServiceClient
import com.agendroid.feature.assistant.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pings [AiServiceClient] every [WATCHDOG_INTERVAL_MS] milliseconds and rebinds if the service
 * is unresponsive, implementing the health watchdog described in spec §11.4.
 *
 * Lifecycle: starts automatically in [init] using the [ApplicationScope]-qualified
 * [CoroutineScope] and runs for the lifetime of the process. No external start/stop calls
 * are needed or provided — the watchdog must not be stopped by any individual component.
 */
@Singleton
class ServiceHealthWatchdog @Inject constructor(
    private val aiServiceClient: AiServiceClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    init {
        appScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                try {
                    val service = aiServiceClient.getService()
                    if (!service.isModelAvailable()) {
                        // Model not ready — release and force a fresh bind on next call
                        aiServiceClient.release()
                        // Brief pause so ResourceStateViewModel's collect() detects the
                        // disconnection before we force a new bind (cooperative, best-effort).
                        delay(1_000L)
                        aiServiceClient.getService() // rebind
                    }
                } catch (_: Exception) {
                    // Service unreachable — release so the next caller triggers a fresh bind
                    aiServiceClient.release()
                    delay(1_000L) // cooperative pause before next watchdog cycle
                }
            }
        }
    }

    companion object {
        /** Interval between health-check pings (30 s per spec §11.4). */
        const val WATCHDOG_INTERVAL_MS = 30_000L
    }
}
