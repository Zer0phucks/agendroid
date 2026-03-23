package com.agendroid.feature.sms.autonomy

/**
 * Pure domain class — no Android framework dependencies — that maps a global autonomy mode
 * and an optional per-contact override to a concrete [SmsAutonomyDecision].
 *
 * Rules:
 * - Contact override wins over the global setting when present.
 * - AUTO  → schedule the send automatically, persist the draft, show a cancel notification.
 * - SEMI  → persist the draft and show an approve/cancel notification; do NOT auto-send.
 * - MANUAL → show a notification only; do NOT persist or auto-send.
 */
class SmsAutonomyPolicy {

    fun decide(
        globalMode: SmsAutonomyMode,
        contactOverride: SmsAutonomyMode?,
    ): SmsAutonomyDecision {
        val effective = contactOverride ?: globalMode
        return when (effective) {
            SmsAutonomyMode.AUTO -> SmsAutonomyDecision(
                mode = SmsAutonomyMode.AUTO,
                shouldScheduleSend = true,
                shouldPersistDraft = true,
                shouldNotify = true,
            )
            SmsAutonomyMode.SEMI -> SmsAutonomyDecision(
                mode = SmsAutonomyMode.SEMI,
                shouldScheduleSend = false,
                shouldPersistDraft = true,
                shouldNotify = true,
            )
            SmsAutonomyMode.MANUAL -> SmsAutonomyDecision(
                mode = SmsAutonomyMode.MANUAL,
                shouldScheduleSend = false,
                shouldPersistDraft = false,
                shouldNotify = true,
            )
        }
    }
}

data class SmsAutonomyDecision(
    val mode: SmsAutonomyMode,
    val shouldScheduleSend: Boolean,
    val shouldPersistDraft: Boolean,
    val shouldNotify: Boolean,
)
