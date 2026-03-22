package com.agendroid.core.telephony

import android.telecom.Call
import android.telecom.CallScreeningService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AgendroidCallScreeningService : CallScreeningService() {

    @Inject lateinit var emergencyNumberPolicy: EmergencyNumberPolicy

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        if (emergencyNumberPolicy.isEmergency(number)) {
            respondToCall(callDetails, allowCall())
            return
        }

        respondToCall(callDetails, allowCall())
    }

    private fun allowCall(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
}
