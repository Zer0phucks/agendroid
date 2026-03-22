package com.agendroid.core.data.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneNumberNormalizerTest {

    private val normalizer = PhoneNumberNormalizer(ApplicationProvider.getApplicationContext())

    @Test
    fun normalize_formatsUsNumberToE164WhenRegionKnown() {
        assertEquals("+14155550123", normalizer.normalize("(415) 555-0123", regionIsoOverride = "US"))
    }

    @Test
    fun normalize_fallsBackToDigitsOnlyWhenE164Unavailable() {
        assertEquals("4155550123", normalizer.normalize("415-555-0123", regionIsoOverride = "ZZ"))
    }
}
