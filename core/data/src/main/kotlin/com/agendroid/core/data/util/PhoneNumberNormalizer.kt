package com.agendroid.core.data.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneNumberNormalizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun normalize(raw: String?, regionIsoOverride: String? = null): String {
        if (raw.isNullOrBlank()) return ""

        val trimmed = raw.trim()
        val regionIso = regionIsoOverride
            ?.takeIf { it.isNotBlank() }
            ?: context.getSystemService(TelephonyManager::class.java)
                ?.networkCountryIso
                ?.takeIf { it.isNotBlank() }
            ?: context.getSystemService(TelephonyManager::class.java)
                ?.simCountryIso
                ?.takeIf { it.isNotBlank() }

        val e164 = regionIso?.let { PhoneNumberUtils.formatNumberToE164(trimmed, it.uppercase()) }
        val normalized = e164 ?: PhoneNumberUtils.normalizeNumber(trimmed)
        return normalized.ifBlank { trimmed }
    }
}
