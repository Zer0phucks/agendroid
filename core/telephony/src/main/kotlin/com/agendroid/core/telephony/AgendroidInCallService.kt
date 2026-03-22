package com.agendroid.core.telephony

import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgendroidInCallService : InCallService() {

    @Inject lateinit var callSessionRepository: CallSessionRepository
    @Inject lateinit var telephonyCoordinator: TelephonyCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val number = call.details.handle?.schemeSpecificPart
        val mode = CallAutonomyMode.FULL_AGENT
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    telephonyCoordinator.endSession()
                }
            }
        }
        call.registerCallback(callback)
        callCallbacks[call] = callback

        serviceScope.launch {
            telephonyCoordinator.startSession(
                callId = call.hashCode().toString(),
                number = number,
                mode = mode,
            )
            if (mode == CallAutonomyMode.FULL_AGENT && call.state == Call.STATE_RINGING) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        callCallbacks.remove(call)?.let(call::unregisterCallback)
        telephonyCoordinator.endSession()
        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        callCallbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callCallbacks.clear()
        serviceScope.cancel()
        telephonyCoordinator.endSession()
        super.onDestroy()
    }
}
