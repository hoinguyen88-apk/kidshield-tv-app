package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.KidShieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            val app = context.applicationContext as? KidShieldApp
            if (app == null) {
                pendingResult.finish()
                return
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.onBootCompleted()
                    WatchGuardForegroundService.ensureRunning(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
