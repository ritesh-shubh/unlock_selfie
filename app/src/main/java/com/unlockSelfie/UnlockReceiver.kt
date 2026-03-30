package com.unlockSelfie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            if (Prefs.getTriggerMode(context) == Prefs.TRIGGER_UNLOCK &&
                Prefs.isServiceEnabled(context)) {
                val serviceIntent = Intent(context, SelfieService::class.java)
                    .putExtra(SelfieService.EXTRA_TRIGGER, SelfieService.TRIGGER_UNLOCK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
