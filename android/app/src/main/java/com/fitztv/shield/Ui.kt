package com.fitztv.shield

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

const val COLOR_TEXT = 0xFFE8EAED.toInt()
const val COLOR_MUTED = 0xFF9AA0A6.toInt()
const val COLOR_SAFE = 0xFF76B900.toInt()
const val COLOR_CAUTION = 0xFFF2B33D.toInt()
const val COLOR_KEEP = 0xFF8A9199.toInt()
const val COLOR_YOURS = 0xFF5EA1FF.toInt()
const val COLOR_PANEL = 0xFF1B2128.toInt()

/** Shared helpers for the app's screens: D-pad friendly buttons, background work, dialogs. */
abstract class Screen : Activity() {
    protected val dp get() = resources.displayMetrics.density
    protected fun Int.px() = (this * dp).toInt()
    protected val isTv by lazy { Engine.isTv(this) }
    protected val actionViews = mutableListOf<View>()
    protected var working = false

    protected fun host() = Engine.host(this)

    protected fun column(padH: Int = 24, padV: Int = 20) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH.px(), padV.px(), padH.px(), padV.px())
    }

    protected fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    protected fun text(s: CharSequence, size: Float = 15f, bold: Boolean = false, color: Int = COLOR_TEXT) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 6.px())
        }

    protected fun section(title: String) = text(title.uppercase(), 13f, bold = true, color = COLOR_SAFE).apply {
        setPadding(0, 22.px(), 0, 2.px()); letterSpacing = 0.08f
    }

    protected fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 17f; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.btn); setTextColor(COLOR_TEXT)
        setPadding(20.px(), 14.px(), 20.px(), 14.px())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 10.px() }
        setOnClickListener { onClick() }
        actionViews += this
    }

    protected fun small(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 14f
        setBackgroundResource(R.drawable.btn); setTextColor(COLOR_TEXT)
        setPadding(14.px(), 6.px(), 14.px(), 6.px()); minHeight = 0; minimumHeight = 0
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = 8.px(); topMargin = 6.px() }
        setOnClickListener { onClick() }
        actionViews += this
    }

    protected fun setBusy(b: Boolean) {
        working = b; actionViews.forEach { it.isEnabled = !b }
        if (b) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Runs [work] off the UI thread (buttons locked), then [done] on the UI thread. */
    protected fun <T> background(fast: Boolean = false, lock: Boolean = true, work: () -> T, done: (Result<T>) -> Unit) {
        if (lock) { if (working) return; setBusy(true) }
        (if (fast) Engine.fast else Engine.worker).execute {
            val r = runCatching(work)
            runOnUiThread {
                if (lock) setBusy(false)
                if (!isFinishing) done(r)
            }
        }
    }

    protected fun confirm(title: String, msg: String, yesLabel: String = "Yes", yes: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton(yesLabel) { _, _ -> yes() }.setNegativeButton("Cancel", null).show()
    }

    protected fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    protected fun error(e: Throwable) = Engine.explain(e, host())

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)
    }
}

/** Friendly names for common packages (used when we can't read labels from the Shield directly). */
object Names {
    private val known = mapOf(
        "com.google.android.katniss" to "Google voice search / Assistant",
        "com.google.android.gms" to "Google Play Services",
        "com.google.android.gsf" to "Google Services Framework",
        "com.google.android.tvlauncher" to "Android TV Home (stock launcher)",
        "com.google.android.tvrecommendations" to "Home-screen recommendations",
        "com.google.android.apps.mediashell" to "Chromecast built-in",
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        "com.google.android.videos" to "Google TV / Play Movies",
        "com.google.android.backdrop" to "Ambient mode screensaver",
        "com.google.android.tts" to "Text-to-speech",
        "com.google.android.speech.pumpkin" to "Offline voice commands",
        "com.android.vending" to "Play Store",
        "com.android.tv.settings" to "Settings",
        "com.spocky.projengmenu" to "Projectivy Launcher",
        "com.wolf.google.lm" to "Wolf Launcher",
        "com.plexapp.android" to "Plex",
        "com.netflix.ninja" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "org.xbmc.kodi" to "Kodi",
        "com.nvidia.ota" to "System updates (NVIDIA)",
        "com.nvidia.tegrazone3" to "NVIDIA Games",
        "com.nvidia.geforcenow" to "GeForce NOW",
        "com.nvidia.shield.remote.server" to "Shield phone remote service",
        "com.nvidia.blakepairing" to "Controller / remote pairing",
        "com.fitztv.shield" to "Fitz-TV Optimizer",
        "com.fitztv" to "Fitz-TV",
        "android" to "Android system",
        "system" to "Android system",
    )

    fun of(ctx: Context, pkg: String, local: Boolean): String {
        if (local) runCatching {
            val pm = ctx.packageManager
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }
        return known[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
