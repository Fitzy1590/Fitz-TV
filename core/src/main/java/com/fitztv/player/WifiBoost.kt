package com.fitztv.player

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Keeps the Wi-Fi radio awake and at full speed while any stream plays. Wi-Fi power saving is a
 * common cause of brief drops that come and go. Reference-counted so several windows share one lock.
 */
object WifiBoost {
    private var lock: WifiManager.WifiLock? = null
    private var users = 0

    @Synchronized fun acquire(context: Context) {
        users++
        if (lock != null) return
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            lock = wm.createWifiLock(mode, "FitzTV:stream").apply { setReferenceCounted(false); acquire() }
        }
    }

    @Synchronized fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) { runCatching { lock?.release() }; lock = null }
    }
}
