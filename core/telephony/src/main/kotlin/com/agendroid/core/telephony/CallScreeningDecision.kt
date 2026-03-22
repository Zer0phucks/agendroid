package com.agendroid.core.telephony

sealed interface CallScreeningDecision {
    data object PassThrough : CallScreeningDecision
    data object ScreenOnly : CallScreeningDecision
    data object FullAgent : CallScreeningDecision
}
