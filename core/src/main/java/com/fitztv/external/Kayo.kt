package com.fitztv.external

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import com.fitztv.data.Game
import com.fitztv.sports.Sport

/**
 * Hand-off to the official Kayo app (your subscription, signed in there). Kayo's streams are
 * DRM-protected and only play inside Kayo, so Fitz TV opens Kayo rather than loading them itself.
 */
object Kayo {
    /** Android TV and phone builds of the Kayo app. */
    private val packages = listOf("au.com.kayosports.tv", "au.com.kayosports", "com.streamotion.kayo")

    /** Kayo's usual quality: 1080p at 50 fps. */
    const val HEIGHT = 1080
    const val FPS = 50f

    private val sports = setOf(Sport.AFL, Sport.NRL, Sport.CRICKET, Sport.NBL, Sport.NBA, Sport.NFL, Sport.MOTOR, Sport.GOLF, Sport.FIGHT)

    fun installedPackage(context: Context): String? = packages.firstOrNull { p ->
        runCatching { context.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
    }

    fun isInstalled(context: Context) = installedPackage(context) != null

    /** Sports Kayo carries in Australia (also anything the guide says is on Kayo / Fox). */
    fun covers(game: Game): Boolean =
        game.sport in sports || game.broadcasts.any { b -> listOf("kayo", "fox footy", "fox cricket", "fox league", "foxtel").any { b.contains(it, true) } }

    /** True if Kayo's 1080p50 beats the best IPTV stream we found. */
    fun betterThan(bestHeight: Int, bestFps: Float): Boolean = bestHeight < HEIGHT || (bestHeight == HEIGHT && bestFps < FPS - 1)

    /** Opens Kayo (TV launcher entry on Android TV). Falls back to the store / website. */
    fun open(context: Context): Boolean {
        val pm = context.packageManager
        val isTv = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        installedPackage(context)?.let { p ->
            val intent = (if (isTv) pm.getLeanbackLaunchIntentForPackage(p) else null) ?: pm.getLaunchIntentForPackage(p)
            if (intent != null && runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return true
        }
        val fallback = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${if (isTv) packages[0] else packages[1]}")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://kayosports.com.au")),
        )
        return fallback.any { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
    }
}
