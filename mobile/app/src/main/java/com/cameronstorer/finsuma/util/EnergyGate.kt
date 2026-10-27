package com.cameronstorer.finsuma.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager

/**
 * Finsuma: one place that decides whether an optional background feature (online lyrics,
 * metadata lookups, ...) may use the network right now. Playback never goes through here.
 * Two user settings: only on Wi-Fi (an unmetered connection), and not while Battery Saver is on.
 */
object EnergyGate {
    @JvmStatic
    fun allowed(context: Context): Boolean = try {
        check(context)
    } catch (t: Throwable) {
        true // never let a failed check break the feature that asked
    }

    private fun check(context: Context): Boolean {
        val app = context.applicationContext
        if (Preferences.isSkipOnBatterySaver() && app.getSystemService(PowerManager::class.java).isPowerSaveMode) {
            return false
        }
        if (Preferences.isBackgroundWifiOnly()) {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        }
        return true
    }
}
