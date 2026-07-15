package com.ankos.skyhomeproxy

import android.content.Context

object AppPrefs {
    private const val NAME = "sky_home_proxy_prefs"
    private const val KEY_PAUSED = "paused"
    private const val KEY_DELAY_MS = "delay_ms"

    fun isPaused(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAUSED, false)
    }

    fun setPaused(context: Context, paused: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    fun getDelayMs(context: Context): Long {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DELAY_MS, 50L)
    }

    fun setDelayMs(context: Context, delay: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DELAY_MS, delay).apply()
    }
}
