// spike/call-pipeline/src/main/kotlin/com/agendroid/spike/callpipeline/phase2/SpikeCallScreeningService.kt
package com.agendroid.spike.callpipeline.phase2

import android.telecom.CallScreeningService

class SpikeCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: android.telecom.Call.Details) {}
}
