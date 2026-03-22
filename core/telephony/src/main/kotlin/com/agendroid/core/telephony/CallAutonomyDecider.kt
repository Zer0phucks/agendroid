package com.agendroid.core.telephony

internal class CallAutonomyDecider(
    private val emergencyNumberPolicy: EmergencyNumberPolicy,
) {
    fun decide(
        autonomyMode: CallAutonomyMode,
        isAiAvailable: Boolean,
        incomingNumber: String?,
    ): CallScreeningDecision {
        if (emergencyNumberPolicy.isEmergency(incomingNumber)) {
            return CallScreeningDecision.PassThrough
        }

        if (!isAiAvailable) {
            return CallScreeningDecision.PassThrough
        }

        return when (autonomyMode) {
            CallAutonomyMode.FULL_AGENT -> CallScreeningDecision.FullAgent
            CallAutonomyMode.SCREEN_ONLY -> CallScreeningDecision.ScreenOnly
            CallAutonomyMode.PASS_THROUGH -> CallScreeningDecision.PassThrough
        }
    }
}
