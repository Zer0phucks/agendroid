package com.agendroid.core.telephony

import javax.inject.Inject

class EmergencyNumberPolicy @Inject constructor(
    private val emergencyNumbers: Set<String> = setOf("911", "112", "999"),
) {
    fun isEmergency(number: String?): Boolean {
        val digits = number?.filter(Char::isDigit).orEmpty()
        return digits in emergencyNumbers
    }
}
