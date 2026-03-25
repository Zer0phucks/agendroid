package com.agendroid.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.agendroid.core.ai.AiCoreService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and starts [AiCoreService] as a foreground service
 * so the AI assistant is available immediately after device reboot.
 *
 * Requires [android.Manifest.permission.RECEIVE_BOOT_COMPLETED] in AndroidManifest.xml
 * (already declared). The receiver itself is declared as non-exported so no external
 * app can trigger it directly.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, AiCoreService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
