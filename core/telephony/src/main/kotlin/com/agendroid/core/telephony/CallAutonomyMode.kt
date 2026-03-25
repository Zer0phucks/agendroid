package com.agendroid.core.telephony

enum class CallAutonomyMode {
    FULL_AGENT,
    SCREEN_ONLY,
    PASS_THROUGH,
}

internal fun String?.toCallAutonomyMode(): CallAutonomyMode = when (this?.uppercase()) {
    "FULL_AGENT",
    "AGENT",
    -> CallAutonomyMode.FULL_AGENT

    "SCREEN_ONLY",
    "SCREEN",
    -> CallAutonomyMode.SCREEN_ONLY

    else -> CallAutonomyMode.PASS_THROUGH
}
