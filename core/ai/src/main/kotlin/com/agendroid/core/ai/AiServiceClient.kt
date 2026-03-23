package com.agendroid.core.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Thread-safety:
 * - [service] and [isBound] are [@Volatile] for safe cross-thread publication.
 * - [getService] is protected by a [Mutex] so that concurrent callers never issue
 *   more than one concurrent bind call (the second caller waits for the first to complete).
 * - [release] is [@Synchronized] on the companion lock object so it can be called from
 *   non-coroutine contexts (e.g. Android service lifecycle callbacks) without racing with
 *   the fields updated inside [getService].
 */
@Singleton
class AiServiceClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Coroutine mutex — serialises concurrent getService() callers across suspension points.
    private val bindMutex = Mutex()

    @Volatile private var serviceConnection: ServiceConnection? = null
    @Volatile private var service: AiServiceInterface? = null
    @Volatile private var isBound = false

    /**
     * Injectable bind function — defaults to [Context.bindService].
     * Override in tests to deliver a fake binder synchronously without a real service.
     */
    internal var doBind: (Intent, ServiceConnection, Int) -> Boolean =
        { intent, conn, flags -> context.bindService(intent, conn, flags) }

    /**
     * Injectable unbind function — defaults to [Context.unbindService].
     * Override in tests alongside [doBind].
     */
    internal var doUnbind: (ServiceConnection) -> Unit =
        { conn -> context.unbindService(conn) }

    /**
     * Returns the bound [AiServiceInterface], binding to [AiCoreService] if not already done.
     *
     * Suspends until the service is connected.  If the bind fails synchronously the coroutine
     * is cancelled with an [IllegalStateException].  If the coroutine is cancelled before the
     * service connects the connection is unbound automatically.
     *
     * Concurrent callers are serialised: at most one bind is ever in-flight at a time.
     */
    suspend fun getService(): AiServiceInterface = bindMutex.withLock {
        // Fast path: service is already bound.
        service?.let { return it }

        // Slow path: bind to AiCoreService and suspend until onServiceConnected fires.
        // The coroutine mutex remains held across the suspension point, so any concurrent
        // getService() call queues behind this one instead of issuing a duplicate bind.
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    // Primary path: real AiCoreService.LocalBinder (production).
                    // Secondary path: IBinder that also implements AiServiceInterface directly
                    // (used in tests where constructing an inner-class LocalBinder is not possible).
                    val aiService: AiServiceInterface? =
                        (binder as? AiCoreService.LocalBinder)?.getInterface()
                            ?: (binder as? AiServiceInterface)
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

            val didBind = doBind(
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
                // invokeOnCancellation may run on any thread; @Volatile reads are safe here.
                if (isBound) {
                    runCatching { doUnbind(connection) }
                }
                service = null
                isBound = false
                if (serviceConnection === connection) {
                    serviceConnection = null
                }
            }
        }
    }

    /**
     * Unbinds from [AiCoreService] and clears the cached service reference.
     * Safe to call even if [getService] was never called or the service is already unbound.
     *
     * Synchronised on the instance to prevent a concurrent [release] call from corrupting
     * state.  [service] and [isBound] are also [@Volatile] for cross-thread visibility.
     */
    @Synchronized
    fun release() {
        val connection = serviceConnection ?: return
        if (isBound) {
            runCatching { doUnbind(connection) }
        }
        serviceConnection = null
        service = null
        isBound = false
    }
}
