package com.agendroid.core.telephony

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.agendroid.core.ai.AiCoreService
import com.agendroid.core.ai.AiServiceInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AiServiceConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) : TelephonyCoordinator.AiProvider {

    private var serviceConnection: ServiceConnection? = null
    private var service: AiServiceInterface? = null
    private var isBound = false

    override suspend fun get(): AiServiceInterface {
        service?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val localBinder = binder as? AiCoreService.LocalBinder
                    val aiService = localBinder?.getInterface()
                    if (aiService == null) {
                        continuation.resumeWithException(
                            IllegalStateException("AiCoreService binder did not expose AiServiceInterface."),
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
                    IllegalStateException("Failed to bind to AiCoreService."),
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

    fun unbind() {
        val connection = serviceConnection ?: return
        if (isBound) {
            runCatching { context.unbindService(connection) }
        }
        serviceConnection = null
        service = null
        isBound = false
    }
}
