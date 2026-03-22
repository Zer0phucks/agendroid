package com.agendroid.core.telephony

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneAccountRegistrarInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun register_and_unregister_updates_phone_account_state() {
        val registrar = PhoneAccountRegistrar(context)

        registrar.unregister()
        assertEquals(false, registrar.isRegistered())

        registrar.register()
        assertEquals(true, registrar.isRegistered())

        registrar.unregister()
        assertEquals(false, registrar.isRegistered())
    }
}
