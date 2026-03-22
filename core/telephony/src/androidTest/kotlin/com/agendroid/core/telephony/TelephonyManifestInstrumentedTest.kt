package com.agendroid.core.telephony

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelephonyManifestInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun manifest_registers_screening_incall_and_connection_services() {
        val packageManager = context.packageManager

        assertNotNull(
            packageManager.getServiceInfo(
                ComponentName(context, AgendroidCallScreeningService::class.java),
                0,
            ),
        )
        assertNotNull(
            packageManager.getServiceInfo(
                ComponentName(context, AgendroidInCallService::class.java),
                0,
            ),
        )
        assertNotNull(
            packageManager.getServiceInfo(
                ComponentName(context, AgendroidConnectionService::class.java),
                0,
            ),
        )
    }
}
