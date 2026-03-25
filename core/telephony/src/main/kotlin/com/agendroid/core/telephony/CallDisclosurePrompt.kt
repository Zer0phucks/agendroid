package com.agendroid.core.telephony

object CallDisclosurePrompt {

    fun forOwner(ownerName: String? = null): String {
        val sanitizedName = ownerName?.trim().orEmpty()
        return if (sanitizedName.isNotEmpty()) {
            "Hi, this is an AI assistant for $sanitizedName. This call may be transcribed. Say 'talk to $sanitizedName' at any time to reach them directly."
        } else {
            "Hi, this is an AI assistant. This call may be transcribed. Say 'talk to a real person' at any time to reach them directly."
        }
    }
}
