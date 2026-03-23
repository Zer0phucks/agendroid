package com.agendroid.feature.sms.autonomy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsAutonomyPolicyTest {

    private lateinit var policy: SmsAutonomyPolicy

    @BeforeEach
    fun setUp() {
        policy = SmsAutonomyPolicy()
    }

    // ------------------------------------------------------------------------------------------
    // Contact override
    // ------------------------------------------------------------------------------------------

    @Test
    fun `contact override wins over global setting`() {
        // Global is AUTO, but contact says MANUAL — MANUAL should win.
        val decision = policy.decide(
            globalMode = SmsAutonomyMode.AUTO,
            contactOverride = SmsAutonomyMode.MANUAL,
        )
        assertEquals(SmsAutonomyMode.MANUAL, decision.mode)
        assertFalse(decision.shouldScheduleSend)
        assertFalse(decision.shouldPersistDraft)
        assertTrue(decision.shouldNotify)
    }

    @Test
    fun `null contact override falls back to global mode`() {
        val decision = policy.decide(
            globalMode = SmsAutonomyMode.AUTO,
            contactOverride = null,
        )
        assertEquals(SmsAutonomyMode.AUTO, decision.mode)
        assertTrue(decision.shouldScheduleSend)
    }

    // ------------------------------------------------------------------------------------------
    // AUTO mode
    // ------------------------------------------------------------------------------------------

    @Test
    fun `auto mode schedules delayed send`() {
        val decision = policy.decide(SmsAutonomyMode.AUTO, contactOverride = null)

        assertEquals(SmsAutonomyMode.AUTO, decision.mode)
        assertTrue(decision.shouldScheduleSend, "AUTO should schedule an auto-send")
        assertTrue(decision.shouldPersistDraft, "AUTO should persist the draft")
        assertTrue(decision.shouldNotify,        "AUTO should show a cancel notification")
    }

    // ------------------------------------------------------------------------------------------
    // SEMI mode
    // ------------------------------------------------------------------------------------------

    @Test
    fun `semi mode stores draft and shows approval notification`() {
        val decision = policy.decide(SmsAutonomyMode.SEMI, contactOverride = null)

        assertEquals(SmsAutonomyMode.SEMI, decision.mode)
        assertFalse(decision.shouldScheduleSend, "SEMI should NOT auto-schedule a send")
        assertTrue(decision.shouldPersistDraft,  "SEMI should persist the draft for approval")
        assertTrue(decision.shouldNotify,         "SEMI should show an approve/cancel notification")
    }

    // ------------------------------------------------------------------------------------------
    // MANUAL mode
    // ------------------------------------------------------------------------------------------

    @Test
    fun `manual mode only posts notification`() {
        val decision = policy.decide(SmsAutonomyMode.MANUAL, contactOverride = null)

        assertEquals(SmsAutonomyMode.MANUAL, decision.mode)
        assertFalse(decision.shouldScheduleSend, "MANUAL should NOT auto-schedule a send")
        assertFalse(decision.shouldPersistDraft, "MANUAL should NOT persist a draft")
        assertTrue(decision.shouldNotify,         "MANUAL should still show a notification")
    }

    // ------------------------------------------------------------------------------------------
    // Contact override — SEMI contact overrides global AUTO
    // ------------------------------------------------------------------------------------------

    @Test
    fun `contact semi override overrides global auto`() {
        val decision = policy.decide(
            globalMode = SmsAutonomyMode.AUTO,
            contactOverride = SmsAutonomyMode.SEMI,
        )
        assertEquals(SmsAutonomyMode.SEMI, decision.mode)
        assertFalse(decision.shouldScheduleSend)
        assertTrue(decision.shouldPersistDraft)
    }
}
