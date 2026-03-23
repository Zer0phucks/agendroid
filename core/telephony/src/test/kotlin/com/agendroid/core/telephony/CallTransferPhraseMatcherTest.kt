package com.agendroid.core.telephony

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallTransferPhraseMatcherTest {

    private val matcher = CallTransferPhraseMatcher()

    @Test
    fun `matches generic human handoff phrases`() {
        assertTrue(matcher.matches("Can I talk to a human?"))
        assertTrue(matcher.matches("Please transfer me"))
        assertTrue(matcher.matches("Stop, I want a real person"))
    }

    @Test
    fun `matches talk to name phrase`() {
        assertTrue(matcher.matches("Please talk to Alex now", ownerName = "Alex"))
    }

    @Test
    fun `ignores normal conversation`() {
        assertFalse(matcher.matches("Can you let them know I'll call back later?"))
    }
}
