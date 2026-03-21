package com.agendroid.core.data.model

data class ContactInfo(
    val contactId: String,
    val displayName: String,
    /** Normalized dialable number: E.164 when possible, digits-only fallback otherwise. */
    val phoneNumber: String,
    val photoUri: String?,
)
