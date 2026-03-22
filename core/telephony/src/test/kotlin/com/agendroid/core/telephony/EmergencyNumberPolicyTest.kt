package com.agendroid.core.telephony

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmergencyNumberPolicyTest {

    private val policy = EmergencyNumberPolicy()

    @Test
    fun `911 always passes through`() {
        assertTrue(policy.isEmergency("911"))
    }

    @Test
    fun `112 always passes through`() {
        assertTrue(policy.isEmergency("112"))
    }

    @Test
    fun `blank number is not considered emergency`() {
        assertFalse(policy.isEmergency(" "))
    }
}
