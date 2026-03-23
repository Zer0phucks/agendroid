package com.agendroid.core.telephony

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallTransferPhraseMatcher @Inject constructor() {

    fun matches(transcript: String, ownerName: String? = null): Boolean {
        val normalized = transcript.lowercase().trim()
        if (normalized.isBlank()) return false

        val builtInPhrases = listOf(
            "human",
            "real person",
            "real human",
            "transfer me",
            "stop",
        )
        if (builtInPhrases.any(normalized::contains)) {
            return true
        }

        val sanitizedOwner = ownerName?.trim()?.lowercase().orEmpty()
        return when {
            sanitizedOwner.isNotEmpty() && normalized.contains("talk to $sanitizedOwner") -> true
            normalized.contains("talk to a real person") -> true
            else -> false
        }
    }
}
