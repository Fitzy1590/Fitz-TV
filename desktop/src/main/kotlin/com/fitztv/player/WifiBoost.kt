package com.fitztv.player

/** Wi-Fi power saving is an Android concern; nothing to hold on the desktop. */
object WifiBoost {
    fun acquire(@Suppress("UNUSED_PARAMETER") context: android.content.Context) = Unit
    fun release() = Unit
}
