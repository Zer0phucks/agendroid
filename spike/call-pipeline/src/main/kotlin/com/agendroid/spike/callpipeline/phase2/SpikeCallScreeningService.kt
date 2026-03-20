// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeCallScreeningService.kt
package com.agendroid.spike.callpipeline.phase2

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/** Intercepts all incoming calls and allows them through (does not block).
 *  Logs the intercept time so we can confirm the service is being called. */
class SpikeCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(TAG, "onScreenCall: number=${callDetails.handle}, direction=${callDetails.callDirection}")
        Log.d(TAG, "Call intercepted at ${System.currentTimeMillis()}")

        // Allow the call through — SpikeInCallService handles the UI
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        )
    }

    companion object { const val TAG = "SpikeCallScreening" }
}
