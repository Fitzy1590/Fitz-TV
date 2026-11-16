package com.fitztv.alerts

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
 * Game reminders, "your team is starting" and score / close-game alerts, shown as an in-app banner
 * (same rules as the TV app). Live scores refresh every minute or so while the app is open.
 */
class Alerts(private val repo: Repository, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _events = MutableSharedFlow<AppAlert>(extraBufferCapacity = 8)
    val events: SharedFlow<AppAlert> = _events

    /** Game currently on screen in the player: no alerts about it. */
    @Volatile var watchingGameId: String? = null

    private var loop: Job? = null
    private val fired = HashSet<String>()
    private val lastScores = HashMap<String, Pair<String?, String?>>()

    fun toggleReminder(game: Game) = settings.update {
        it.copy(reminders = if (game.id in it.reminders) it.reminders - game.id else it.reminders + game.id)
    }

    fun start() {
        loop?.cancel()
        loop = scope.launch {
            var tick = 0
            while (isActive) {
                val s = settings.value
                val now = System.currentTimeMillis()
                val anyLiveFav = repo.games.value.any { it.gameState == GameState.LIVE && s.isFavourite(it) }
                if (tick % 3 == 0 && (anyLiveFav || s.reminders.isNotEmpty())) runCatching { repo.refreshLiveScores() }
                check(now)
                tick++
                delay(20_000)
            }
        }
    }

    private fun check(now: Long) {
        val s = settings.value
        for (g in repo.games.value) {
            if (g.id in s.reminders && g.startUtcMillis - now < 60_000 && now - g.startUtcMillis < 30 * 60_000) {
                emitOnce("rem:${g.id}", AppAlert(AppAlert.Kind.REMINDER, "Starting now: ${g.matchup} (${g.league})", g))
            }
            val fav = s.isFavourite(g)
            if (fav && s.teamStartAlerts && g.startUtcMillis - now < 60_000 && now - g.startUtcMillis < 20 * 60_000 && g.gameState != GameState.FINAL) {
                emitOnce("start:${g.id}", AppAlert(AppAlert.Kind.TEAM_STARTING, "Your team is on: ${g.matchup}", g))
            }
            if (g.gameState != GameState.LIVE || !s.scoreAlerts || g.id == watchingGameId) continue
            val prev = lastScores[g.id]
            val cur = g.awayScore to g.homeScore
            if (fav && prev != null && prev != cur && g.home.isNotBlank()) {
                val scorer = when {
                    (cur.second?.toIntOrNull() ?: 0) > (prev.second?.toIntOrNull() ?: 0) -> g.homeShort.ifBlank { g.home }
                    else -> g.awayShort.ifBlank { g.away }
                }
                if (g.sport == Sport.SOCCER || g.sport == Sport.AFL || g.sport == Sport.NFL) {
                    _events.tryEmit(AppAlert(AppAlert.Kind.SCORE, "$scorer score · ${g.awayShort} ${cur.first}–${cur.second} ${g.homeShort} · ${g.statusText}", g))
                }
            }
            lastScores[g.id] = cur
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

    private fun emitOnce(key: String, alert: AppAlert) { if (fired.add(key)) _events.tryEmit(alert) }
}
