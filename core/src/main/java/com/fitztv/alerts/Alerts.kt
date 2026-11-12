package com.fitztv.alerts

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.Repository
import com.fitztv.data.SettingsStore
import com.fitztv.sports.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Something to tell the user about, with the game to jump to. */
data class AppAlert(val kind: Kind, val text: String, val game: Game) {
    enum class Kind { REMINDER, TEAM_STARTING, SCORE, CLOSE_GAME }
}

/**
 * Game reminders, "your team is starting" and score / close-game alerts. Only runs while the
 * app is on screen (no background polling); live scores refresh every minute while it does.
 */
class Alerts(private val app: Application, private val repo: Repository, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _events = MutableSharedFlow<AppAlert>(extraBufferCapacity = 8)
    val events: SharedFlow<AppAlert> = _events

    /** Game currently on screen in the player: no alerts about it. */
    @Volatile var watchingGameId: String? = null

    private var started = 0
    private var loop: Job? = null
    private val fired = HashSet<String>()
    private val lastScores = HashMap<String, Pair<String?, String?>>()
    /** Score alerts waiting out the stream delay: due time to alert. */
    private val pendingScores = ArrayList<Pair<Long, AppAlert>>()

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { if (started++ == 0) startLoop() }
            override fun onActivityStopped(a: Activity) { if (--started <= 0) { started = 0; loop?.cancel() } }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    fun toggleReminder(game: Game) = settings.update {
        it.copy(reminders = if (game.id in it.reminders) it.reminders - game.id else it.reminders + game.id)
    }

    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            var tick = 0
            while (isActive) {
                val s = settings.value
                val now = System.currentTimeMillis()
                val anyLiveFav = repo.games.value.any { it.gameState == GameState.LIVE && s.isFavourite(it) }
                // Score refresh every minute; the loop ticks every 5 s so delayed alerts release on time.
                if (tick % 12 == 0 && (anyLiveFav || s.reminders.isNotEmpty())) runCatching { repo.refreshLiveScores() }
                check(System.currentTimeMillis())
                tick++
                delay(5_000)
            }
        }
    }

    private fun check(now: Long) {
        val s = settings.value
        // Release score alerts whose delay has elapsed (dropped if the user turned them off
        // or started watching that game meanwhile).
        val due = pendingScores.filter { it.first <= now }
        pendingScores.removeAll(due)
        for ((_, a) in due) if (s.scoreAlerts && a.game.id != watchingGameId) emitAlways(a)
        for (g in repo.games.value) {
            // Reminders: at kick-off (from a minute before).
            if (g.id in s.reminders && g.startUtcMillis - now < 60_000 && now - g.startUtcMillis < 30 * 60_000) {
                emitOnce("rem:${g.id}", AppAlert(AppAlert.Kind.REMINDER, "Starting now: ${g.matchup} (${g.league})", g))
            }
            val fav = s.isFavourite(g)
            if (fav && s.teamStartAlerts && g.startUtcMillis - now < 60_000 && now - g.startUtcMillis < 20 * 60_000 && g.gameState != GameState.FINAL) {
                emitOnce("start:${g.id}", AppAlert(AppAlert.Kind.TEAM_STARTING, "Your team is on: ${g.matchup}", g))
            }
            if (g.gameState != GameState.LIVE || !s.scoreAlerts || g.id == watchingGameId) continue
            // Score alerts for favourite teams.
            val prev = lastScores[g.id]
            val cur = g.awayScore to g.homeScore
            if (fav && prev != null && prev != cur && g.home.isNotBlank()) {
                val scorer = when {
                    (cur.second?.toIntOrNull() ?: 0) > (prev.second?.toIntOrNull() ?: 0) -> g.homeShort.ifBlank { g.home }
                    else -> g.awayShort.ifBlank { g.away }
                }
                if (g.sport == Sport.SOCCER || g.sport == Sport.AFL || g.sport == Sport.NFL) {
                    // Hold back by the stream delay so it lands after the moment is on screen.
                    pendingScores += now + s.scoreDelaySeconds * 1000L to
                        AppAlert(AppAlert.Kind.SCORE, "$scorer score · ${g.awayShort} ${cur.first}–${cur.second} ${g.homeShort} · ${g.statusText}", g)
                }
            }
            lastScores[g.id] = cur
            // Close game, final minutes (any league).
            if (isClose(g)) emitOnce("close:${g.id}", AppAlert(AppAlert.Kind.CLOSE_GAME, "Close finish · ${g.awayShort} ${g.awayScore}–${g.homeScore} ${g.homeShort} · ${g.statusText}", g))
        }
    }

    private fun isClose(g: Game): Boolean {
        val a = g.awayScore?.toIntOrNull() ?: return false
        val h = g.homeScore?.toIntOrNull() ?: return false
        val margin = kotlin.math.abs(a - h)
        val st = g.statusText.lowercase()
        val last = st.contains("4th") || st.contains("q4") || st.contains("ot")
        return when (g.sport) {
            Sport.NBA, Sport.NBL -> last && margin <= 6
            Sport.NFL -> last && margin <= 8
            Sport.AFL -> last && margin <= 12
            Sport.SOCCER -> (Regex("(\\d+)'").find(st)?.groupValues?.get(1)?.toIntOrNull() ?: 0) >= 80 && margin <= 1
            else -> false
        }
    }

    private fun emitOnce(key: String, alert: AppAlert) { if (fired.add(key)) emitAlways(alert) }

    private fun emitAlways(alert: AppAlert) {
        _events.tryEmit(alert)
        notify(alert)
    }

    /** System notification too (shown on the phone; the TV shows the in-app banner). */
    private fun notify(alert: AppAlert) {
        val ctx: Context = app
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("alerts", "Game alerts", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(
            ctx, alert.game.id.hashCode(),
            Intent(Intent.ACTION_VIEW, gameUri(alert.game.id)).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            NotificationManagerCompat.from(ctx).notify(
                alert.game.id.hashCode(),
                NotificationCompat.Builder(ctx, "alerts").setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Fitz TV").setContentText(alert.text).setContentIntent(pi).setAutoCancel(true).build(),
            )
        }
    }

    companion object {
        /** Deep link used by notifications and the Android TV home row. */
        fun gameUri(id: String): Uri = Uri.parse("fitztv://game/" + Uri.encode(id))
        fun gameIdFrom(intent: Intent?): String? =
            intent?.data?.takeIf { it.scheme == "fitztv" && it.host == "game" }?.path?.trimStart('/')?.let { Uri.decode(it) }
    }
}
