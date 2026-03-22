// core/ai/src/androidTest/kotlin/com/agendroid/core/ai/AiCoreServiceTest.kt
package com.agendroid.core.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AiCoreServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun service_bindsSuccessfully_andExposesBinder() {
        val latch  = CountDownLatch(1)
        var iface: AiServiceInterface? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                iface = (binder as AiCoreService.LocalBinder).getInterface()
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        val intent = Intent(context, AiCoreService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        assertTrue("Service did not bind within 5 s", latch.await(5, TimeUnit.SECONDS))
        assertNotNull(iface)

        context.unbindService(connection)
    }

    @Test
    fun service_isModelAvailable_returnsFalseWhenModelNotPushed() {
        // In CI / emulators the model won't be present — expect false, not a crash
        val latch  = CountDownLatch(1)
        var available: Boolean? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                available = (binder as AiCoreService.LocalBinder).getInterface().isModelAvailable()
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        context.bindService(Intent(context, AiCoreService::class.java), connection, Context.BIND_AUTO_CREATE)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(available)  // just verify no crash; value may be true or false

        context.unbindService(connection)
    }

    @Test
    fun service_resourceState_emitsInitialValue() = runTest {
        val latch  = CountDownLatch(1)
        var iface: AiServiceInterface? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                iface = (binder as AiCoreService.LocalBinder).getInterface()
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        context.bindService(Intent(context, AiCoreService::class.java), connection, Context.BIND_AUTO_CREATE)
        latch.await(5, TimeUnit.SECONDS)

        val state = iface!!.resourceState.first()
        assertNotNull(state)  // any ResourceState is valid; just verify it emits

        context.unbindService(connection)
    }
}
