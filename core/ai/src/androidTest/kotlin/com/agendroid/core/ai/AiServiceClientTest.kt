// core/ai/src/androidTest/kotlin/com/agendroid/core/ai/AiServiceClientTest.kt
package com.agendroid.core.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiServiceClientTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun get_binds_to_AiCoreService_and_returns_interface() = runTest {
        val client = AiServiceClient(context)
        try {
            val service = client.getService()
            // Verify we received a valid AiServiceInterface (not null, no crash).
            assertNotNull(service)
        } finally {
            client.release()
        }
    }
}
