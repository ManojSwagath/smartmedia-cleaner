package com.example.app

import android.content.Context

internal object AppSettings {

    private const val PREFS = "app_settings"

    private const val KEY_BG_INDEXING_ENABLED = "bg_indexing_enabled"
    private const val KEY_LOW_DISTURBANCE_MODE = "low_disturbance_mode"

    fun isBackgroundIndexingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BG_INDEXING_ENABLED, true)
    }

    fun setBackgroundIndexingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BG_INDEXING_ENABLED, enabled).apply()
    }

    fun isLowDisturbanceModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_LOW_DISTURBANCE_MODE, false)
    }

    fun setLowDisturbanceModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOW_DISTURBANCE_MODE, enabled).apply()
    }

    /**
     * Returns a bounded limit that prioritizes low CPU/battery/memory usage.
     *
     * If low-disturbance mode is enabled, this clamps down large scans.
     */
    fun applyAnalysisLimit(context: Context, defaultLimit: Int): Int {
        if (!isLowDisturbanceModeEnabled(context)) return defaultLimit
        // Conservative cap for older/low-end devices.
        return minOf(defaultLimit, 1200)
    }

    fun applyBackgroundWarmLimit(context: Context, defaultLimit: Int): Int {
        if (!isLowDisturbanceModeEnabled(context)) return defaultLimit
        return minOf(defaultLimit, 300)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
