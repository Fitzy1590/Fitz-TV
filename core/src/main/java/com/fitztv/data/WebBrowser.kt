package com.fitztv.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a separate web browser app. On Android TV that is TV Bro (open source, built for a
 * remote) or Puffin TV; elsewhere the phone's own browser. Nothing is embedded or pre-filled:
 * you browse wherever you like, and the video plays in the browser.
 */
object WebBrowser {
    private val tvBrowsers = listOf("com.phlox.tvwebbrowser", "com.cloudmosa.puffinTV")
    const val TV_BRO_PACKAGE = "com.phlox.tvwebbrowser"

    /** Returns false when no browser could be opened (a store page is opened to install TV Bro). */
    fun open(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in tvBrowsers) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        // Any browser that handles web links (a phone's Chrome, for example).
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(view) }.isSuccess) return true
        // None installed: offer TV Bro in the store.
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TV_BRO_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return false
    }
}
