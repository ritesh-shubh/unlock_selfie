package com.unlockSelfie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            if (Prefs.isAutoStart(context) && Prefs.isServiceEnabled(context)) {
                val serviceIntent = Intent(context, SelfieService::class.java)
                    .putExtra(SelfieService.EXTRA_TRIGGER, SelfieService.TRIGGER_BOOT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
