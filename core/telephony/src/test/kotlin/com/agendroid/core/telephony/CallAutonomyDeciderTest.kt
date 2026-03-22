package com.agendroid.core.telephony

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CallAutonomyDeciderTest {

    private val decider = CallAutonomyDecider(EmergencyNumberPolicy())

    @Test
    fun `emergency numbers force pass through`() {
        assertEquals(
            CallScreeningDecision.PassThrough,
            decider.decide(CallAutonomyMode.FULL_AGENT, isAiAvailable = true, incomingNumber = "911"),
        )
    }

    @Test
    fun `ai unavailable forces pass through`() {
        assertEquals(
            CallScreeningDecision.PassThrough,
            decider.decide(CallAutonomyMode.FULL_AGENT, isAiAvailable = false, incomingNumber = "5551234"),
        )
    }

    @Test
    fun `pass through mode stays pass through`() {
        assertEquals(
            CallScreeningDecision.PassThrough,
            decider.decide(CallAutonomyMode.PASS_THROUGH, isAiAvailable = true, incomingNumber = "5551234"),
        )
    }

    @Test
    fun `screen only mode returns screen only`() {
        assertEquals(
            CallScreeningDecision.ScreenOnly,
            decider.decide(CallAutonomyMode.SCREEN_ONLY, isAiAvailable = true, incomingNumber = "5551234"),
        )
    }

    @Test
    fun `full agent mode returns full agent`() {
        assertEquals(
            CallScreeningDecision.FullAgent,
            decider.decide(CallAutonomyMode.FULL_AGENT, isAiAvailable = true, incomingNumber = "5551234"),
        )
    }
}
