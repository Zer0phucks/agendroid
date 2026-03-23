package com.agendroid.core.telephony

import android.telecom.Call
import android.telecom.CallScreeningService
import com.agendroid.core.ai.AiServiceClient
import com.agendroid.core.data.repository.AppSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class AgendroidCallScreeningService : CallScreeningService() {

    @Inject lateinit var aiServiceClient: AiServiceClient
    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var callAutonomyDecider: CallAutonomyDecider
    @Inject lateinit var emergencyNumberPolicy: EmergencyNumberPolicy

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        if (emergencyNumberPolicy.isEmergency(number)) {
            respondToCall(callDetails, allowCall())
            return
        }

        val response = runBlocking {
            val autonomyMode = appSettingsRepository.settingsFlow.first()
                ?.callAutonomyMode
                .toCallAutonomyMode()
            val aiAvailable = runCatching {
                aiServiceClient.getService().isModelAvailable()
            }.getOrDefault(false)
            aiServiceClient.release()

            when (callAutonomyDecider.decide(autonomyMode, aiAvailable, incomingNumber = number)) {
                CallScreeningDecision.PassThrough -> allowCall(silence = false)
                CallScreeningDecision.ScreenOnly,
                CallScreeningDecision.FullAgent,
                -> allowCall(silence = true)
            }
        }

        respondToCall(callDetails, response)
    }

    private fun allowCall(silence: Boolean = false): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(silence)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
}
