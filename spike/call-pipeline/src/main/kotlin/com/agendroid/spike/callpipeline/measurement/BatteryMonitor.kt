package com.agendroid.spike.callpipeline.measurement

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BatteryMonitor(private val context: Context) {

    /** Current battery level 0–100. */
    fun currentLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level < 0) -1 else (level * 100 / scale)
    }

    /** Emits battery level every [intervalMs]. */
    fun levelFlow(intervalMs: Long = 10_000L): Flow<Int> = flow {
        while (true) {
            emit(currentLevel())
            delay(intervalMs)
        }
    }
}
