package com.fitztv.tvhome

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.favouriteEntries
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shield screensaver (Settings › Device Preferences › Screen saver › Fitz TV live scores):
 * neon clock, live games (your teams first) and your teams' next games. Refreshes every
 * minute and drifts slowly to avoid burn-in.
 */
class LiveScoresDream : DreamService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var panel: LinearLayout
    private lateinit var root: FrameLayout

    // Colours follow Settings › Theme.
    private val pal get() = com.fitztv.ui.components.Palettes.byId(FitzApp.instance.settings.value.theme)
    private val purple get() = pal.accent.toInt()
    private val magenta get() = pal.accent2.toInt()
    private val cyan get() = pal.info.toInt()
    private val live get() = pal.live.toInt()
    private val text get() = pal.text.toInt()
    private val dim get() = pal.textDim.toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        root = FrameLayout(this).apply { setBackgroundColor(pal.bg.toInt()) }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            background = GradientDrawable().apply {
                cornerRadius = 36f
                setColor((pal.bg2.toInt() and 0x00FFFFFF) or (0xCC shl 24))
                setStroke(3, purple)
            }
        }
        root.addView(panel, FrameLayout.LayoutParams(1100, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scope.launch {
            var step = 0
            while (isActive) {
                runCatching { FitzApp.instance.repo.refreshLiveScores() }
                render()
                // Drift a little each minute (burn-in protection).
                val dx = listOf(-120, -60, 0, 60, 120)[step % 5]
                val dy = listOf(-60, 40, -20, 60, 0)[step % 5]
                panel.animate().translationX(dx.toFloat()).translationY(dy.toFloat()).setDuration(2_000).start()
                step++
                delay(60_000)
            }
        }
    }

    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 6, 0, 6)
    }

    private fun line(g: Game): String {
        val zone = FitzApp.instance.settings.value.zone()
        return when (g.gameState) {
            GameState.LIVE -> if (g.home.isNotBlank()) "● ${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}   ${g.statusText}" else "● ${g.title}   ${g.statusText}"
            GameState.PRE -> "${Fmt.whenLabel(g, zone, false)}   ${g.matchup}"
            GameState.FINAL -> "Final  ${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}"
        }
    }

    private fun render() {
        val app = FitzApp.instance
        val s = app.settings.value
        val now = System.currentTimeMillis()
        panel.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(tv("FITZ ", 30f, text, true)); head.addView(tv("TV", 30f, purple, true))
        head.addView(tv("    " + Fmt.time(now, s.zone(), s.use24h), 30f, cyan, true))
        panel.addView(head)

        val liveGames = app.repo.liveNow(null).take(6)
        panel.addView(tv("LIVE NOW", 16f, live, true))
        if (liveGames.isEmpty()) panel.addView(tv("Nothing live right now", 20f, dim))
        liveGames.forEach { g -> panel.addView(tv((if (s.isFavourite(g)) "★ " else "") + line(g) + "   ${g.league}", 22f, if (s.isFavourite(g)) magenta else text)) }

        val mine = favouriteEntries(app.repo.games.value, s, now, app.repo.logos).filter { it.game != null && it.game!!.gameState != GameState.LIVE }.take(5)
        if (mine.isNotEmpty()) {
            panel.addView(tv("MY TEAMS", 16f, purple, true))
            mine.forEach { e -> panel.addView(tv("${e.team}:  ${line(e.game!!)}", 19f, dim)) }
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
