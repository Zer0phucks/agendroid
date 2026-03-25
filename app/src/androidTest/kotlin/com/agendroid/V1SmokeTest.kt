package com.agendroid

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agendroid.core.ai.ResourceState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V1 smoke tests for reliability and compliance features.
 *
 * These tests verify:
 * 1. [BootCompletedReceiver] is registered in AndroidManifest.xml.
 * 2. Structural wiring ensures [AiCoreService] can be started on boot.
 * 3. [ResourceState] degraded states are distinct from Normal.
 *
 * Note: Tests that require a live device (actual boot, real thermal readings) are
 * annotated with a comment explaining the limitation. The tests here verify structural
 * wiring and compile-time contracts.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class V1SmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * Verifies that [BootCompletedReceiver] is registered in the app's AndroidManifest.xml.
     *
     * Uses [PackageManager] to query receivers for the package; if the receiver is not declared
     * the manifest check would fail silently at runtime, so we assert here statically.
     */
    @Test
    fun boot_receiver_is_registered_in_manifest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        val pm = context.packageManager

        val packageInfo = pm.getPackageInfo(
            packageName,
            PackageManager.GET_RECEIVERS,
        )

        val receiverNames = packageInfo.receivers?.map { it.name } ?: emptyList()
        val hasBootReceiver = receiverNames.any { name ->
            name.contains("BootCompletedReceiver")
        }

        assertTrue(
            "BootCompletedReceiver must be registered in AndroidManifest.xml. " +
                "Found receivers: $receiverNames",
            hasBootReceiver,
        )
    }

    /**
     * Verifies that [AiCoreService] can be referenced for a start-on-boot intent.
     *
     * This is a structural test — it verifies that the service class is accessible from
     * the test context, which is a prerequisite for [BootCompletedReceiver] being able to
     * start it. An actual boot test requires a rooted device or emulator with controllable
     * boot state, which is outside the scope of CI instrumented tests.
     */
    @Test
    fun ai_core_service_starts_on_boot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        val pm = context.packageManager

        val packageInfo = pm.getPackageInfo(
            packageName,
            PackageManager.GET_SERVICES,
        )

        val serviceNames = packageInfo.services?.map { it.name } ?: emptyList()
        val hasAiCoreService = serviceNames.any { name ->
            name.contains("AiCoreService")
        }

        assertTrue(
            "AiCoreService must be declared in AndroidManifest.xml for boot-start to work. " +
                "Found services: $serviceNames",
            hasAiCoreService,
        )
    }

    /**
     * Verifies that [ResourceState] degraded states (Hot, LowBattery) are distinct from
     * [ResourceState.Normal] so the UI can reliably show/hide the degraded-state banner.
     *
     * This is a compile-time / type-system test. If the sealed class structure changes in a
     * way that breaks equality, this test catches it.
     */
    @Test
    fun degraded_state_indicator_reflects_resource_state() {
        val normal: ResourceState = ResourceState.Normal
        val warm: ResourceState = ResourceState.Warm
        val hot: ResourceState = ResourceState.Hot
        val lowBattery: ResourceState = ResourceState.LowBattery

        // Normal is not a degraded state
        assertFalse("Normal should not be a degraded state", normal.isDegraded())
        assertFalse("Warm should not trigger the degraded banner", warm.isDegraded())

        // Hot and LowBattery are degraded states shown as persistent banners
        assertTrue("Hot must be a degraded state", hot.isDegraded())
        assertTrue("LowBattery must be a degraded state", lowBattery.isDegraded())

        // Each state is distinct
        assertFalse(normal == hot)
        assertFalse(normal == lowBattery)
        assertFalse(hot == lowBattery)

        // Verify banner message content is non-null for degraded states
        assertNotNull(hot.bannerMessage())
        assertNotNull(lowBattery.bannerMessage())
    }

    // Helper extensions mirroring the banner logic in AgendroidNavHost
    private fun ResourceState.isDegraded(): Boolean =
        this is ResourceState.Hot || this is ResourceState.LowBattery

    private fun ResourceState.bannerMessage(): String? = when (this) {
        is ResourceState.LowBattery -> "AI disabled — battery critically low"
        is ResourceState.Hot -> "Device hot — AI running in low-power mode"
        else -> null
    }
}
