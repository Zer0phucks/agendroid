package com.agendroid.core.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reusable, injectable wrapper for the Android [ServiceConnection] / bind / unbind pattern
 * required to access [AiServiceInterface] from [AiCoreService].
 *
 * Feature modules and other core modules should inject [AiServiceClient] rather than
 * duplicating the bind logic.  Call [getService] to obtain the interface and [release]
 * when the caller no longer needs it.
 *
 * Thread-safety: the cached [service] reference is only safe to use on the thread that
 * received [ServiceConnection.onServiceConnected].  Callers that need multi-thread access
 * should re-invoke [getService] rather than caching the returned reference themselves.
 */
@Singleton
class AiServiceClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var serviceConnection: ServiceConnection? = null
    private var service: AiServiceInterface? = null
    private var isBound = false

    /**
     * Returns the bound [AiServiceInterface], binding to [AiCoreService] if not already done.
     *
     * Suspends until the service is connected.  If the bind fails synchronously the coroutine
     * is cancelled with an [IllegalStateException].  If the coroutine is cancelled before the
     * service connects the connection is unbound automatically.
     */
    suspend fun getService(): AiServiceInterface {
        service?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val localBinder = binder as? AiCoreService.LocalBinder
                    val aiService = localBinder?.getInterface()
                    if (aiService == null) {
                        continuation.resumeWithException(
                            IllegalStateException("AiCoreService binder did not expose AiServiceInterface.")
                        )
                        return
                    }

                    service = aiService
                    serviceConnection = this
                    isBound = true
                    continuation.resume(aiService)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                    isBound = false
                    serviceConnection = null
                }
            }

            val didBind = context.bindService(
                Intent(context, AiCoreService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            if (!didBind) {
                continuation.resumeWithException(
                    IllegalStateException("Failed to bind to AiCoreService.")
                )
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                if (isBound) {
                    runCatching { context.unbindService(connection) }
                }
                if (serviceConnection === connection) {
                    serviceConnection = null
                }
                service = null
                isBound = false
            }
        }
    }

    /**
     * Unbinds from [AiCoreService] and clears the cached service reference.
     * Safe to call even if [getService] was never called or the service is already unbound.
     */
    fun release() {
        val connection = serviceConnection ?: return
        if (isBound) {
            runCatching { context.unbindService(connection) }
        }
        serviceConnection = null
        service = null
        isBound = false
    }
}
