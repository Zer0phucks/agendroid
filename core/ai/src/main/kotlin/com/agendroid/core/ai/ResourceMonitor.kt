// core/ai/src/main/kotlin/com/agendroid/core/ai/ResourceMonitor.kt
package com.agendroid.core.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls device thermal headroom and battery level, emitting a new [ResourceState]
 * whenever the state changes.
 *
 * Temperature uses [PowerManager.thermalHeadroom] (Android 31+, available via reflection).
 * The OnePlus 12 returns -1f for this API; [toResourceState] treats -1f as Normal.
 *
 * Usage: collect [stateFlow] in [AiCoreService]; share the value via [AiServiceInterface]
 * so feature modules can react without polling themselves.
 */
@Singleton
class ResourceMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Emits [ResourceState] updates, polling every [intervalMs] milliseconds. */
    val stateFlow: Flow<ResourceState> = flow {
        while (true) {
            emit(toResourceState(readThermal(), readBattery()))
            delay(POLL_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    private fun readThermal(): Float = try {
        val method = PowerManager::class.java.getMethod("thermalHeadroom", Int::class.java)
        val headroom = method.invoke(powerManager, 10) as? Float ?: -1f
        // headroom 1.0 = cool, 0.0 = critically hot. Map to Celsius range 25–42.
        if (headroom >= 0f) 25f + (1f - headroom) * 17f else -1f
    } catch (_: Exception) { -1f }

    private fun readBattery(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L

        /**
         * Pure function: maps thermal reading and battery % to [ResourceState].
         * Extracted for unit testing (no Android dependencies).
         */
        fun toResourceState(tempCelsius: Float, batteryPct: Int): ResourceState = when {
            batteryPct < 10                         -> ResourceState.LowBattery
            tempCelsius >= 42f || batteryPct < 15   -> ResourceState.Hot
            tempCelsius in 38f..<42f                -> ResourceState.Warm
            else                                    -> ResourceState.Normal  // includes -1f sentinel
        }
    }
}
