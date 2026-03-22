package com.agendroid.core.telephony

class EmergencyNumberPolicy(
    private val emergencyNumbers: Set<String> = setOf("911", "112", "999"),
) {
    fun isEmergency(number: String?): Boolean {
        val digits = number?.filter(Char::isDigit).orEmpty()
        return digits in emergencyNumbers
    }
}
