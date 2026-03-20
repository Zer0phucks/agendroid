package com.agendroid.spike.callpipeline.measurement

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ThermalMonitor(context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Emits SoC temperature in Celsius every [intervalMs]. Uses the thermal headroom API
     *  (Android 31+) which reports normalized thermal headroom 0.0–1.0, scaled to ~42°C max.
     *  If the API is unavailable, emits -1f as a sentinel. */
    fun temperatureFlow(intervalMs: Long = 2_000L): Flow<Float> = flow {
        while (true) {
            val headroom = try {
                // thermalHeadroom(durationHint) is available on Android 31+ via reflection
                val method = PowerManager::class.java.getMethod("thermalHeadroom", Int::class.java)
                (method.invoke(powerManager, 10) as? Float) ?: -1f
            } catch (e: Exception) {
                -1f
            }
            // headroom=1.0 means no thermal pressure; 0.0 means critically hot.
            // Approximate conversion: 25°C baseline + (1.0 - headroom) * 17°C range
            val approxCelsius = if (headroom >= 0f) 25f + (1f - headroom) * 17f else -1f
            emit(approxCelsius)
            delay(intervalMs)
        }
    }

    /** Returns true if SoC is at or above the 42°C throttle threshold per spec §11.2. */
    fun isOverThreshold(tempCelsius: Float): Boolean = tempCelsius >= 42f
}
