package com.unlockSelfie

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREFS_NAME = "unlock_selfie_prefs"
    private const val KEY_SAVE_DIR = "save_directory"
    private const val KEY_TRIGGER_MODE = "trigger_mode"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    const val TRIGGER_UNLOCK = "unlock"
    const val TRIGGER_WRONG_PASSWORD = "wrong_password"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSaveDir(ctx: Context): String =
        prefs(ctx).getString(KEY_SAVE_DIR, null)
            ?: (ctx.getExternalFilesDir(null)?.absolutePath ?: ctx.filesDir.absolutePath)

    fun setSaveDir(ctx: Context, dir: String) =
        prefs(ctx).edit().putString(KEY_SAVE_DIR, dir).apply()

    fun getTriggerMode(ctx: Context): String =
        prefs(ctx).getString(KEY_TRIGGER_MODE, TRIGGER_UNLOCK) ?: TRIGGER_UNLOCK

    fun setTriggerMode(ctx: Context, mode: String) =
        prefs(ctx).edit().putString(KEY_TRIGGER_MODE, mode).apply()

    fun isAutoStart(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AUTO_START, enabled).apply()

    fun isServiceEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
}
