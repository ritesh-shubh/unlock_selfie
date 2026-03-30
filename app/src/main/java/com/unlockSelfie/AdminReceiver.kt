package com.unlockSelfie

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        if (Prefs.getTriggerMode(context) == Prefs.TRIGGER_WRONG_PASSWORD &&
            Prefs.isServiceEnabled(context)) {
            triggerSelfie(context)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Prefs.setServiceEnabled(context, true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Prefs.setServiceEnabled(context, false)
    }

    private fun triggerSelfie(context: Context) {
        val serviceIntent = Intent(context, SelfieService::class.java)
            .putExtra(SelfieService.EXTRA_TRIGGER, SelfieService.TRIGGER_WRONG_PASSWORD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
