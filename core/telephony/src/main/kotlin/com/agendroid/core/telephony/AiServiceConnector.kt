package com.agendroid.core.telephony

import com.agendroid.core.ai.AiServiceClient
import com.agendroid.core.ai.AiServiceInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [TelephonyCoordinator.AiProvider] with the shared [AiServiceClient].
 *
 * Previously this class duplicated the ServiceConnection / bind / unbind pattern.
 * It now delegates entirely to [AiServiceClient], which lives in :core:ai and can
 * be reused by any feature module that needs [AiServiceInterface].
 */
@Singleton
class AiServiceConnector @Inject constructor(
    private val client: AiServiceClient,
) : TelephonyCoordinator.AiProvider {

    override suspend fun get(): AiServiceInterface = client.getService()

    override fun unbind() = client.release()
}
