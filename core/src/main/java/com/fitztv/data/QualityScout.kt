package com.fitztv.data

import com.fitztv.sports.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Measures the real resolution / frame rate of the streams you're most likely to watch
 * (live and upcoming games, favourites, quick channels), one at a time, while the app is open
 * on the menus and nothing is playing — so it never competes with a stream for your
 * provider's connection(s). Results are kept for 3 days and drive the 4K > 1080p > 720p ranking.
 */
class QualityScout(private val repo: Repository, private val settings: SettingsStore) {
    private var job: Job? = null

    companion object {
        /** Players currently running (any screen). Scouting pauses while > 0. */
        val activePlayers = AtomicInteger(0)
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            delay(8_000)   // let the guide and channels load first
            val done = HashSet<String>()
            while (isActive) {
                if (activePlayers.get() > 0) { delay(5_000); continue }
                val next = candidates().firstOrNull { it.id !in done && repo.probe.needsProbe(it) }
                if (next == null) { delay(60_000); done.clear(); continue }
                done += next.id
                repo.probe.probeOne(next, settings.value.userAgent)
                delay(700)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    private fun candidates(): List<Channel> {
        val s = settings.value
        val now = System.currentTimeMillis()
        val games = repo.games.value
            .filter { it.gameState == GameState.LIVE || (it.gameState == GameState.PRE && it.startUtcMillis - now < 12 * 3_600_000L) }
            .sortedWith(compareByDescending<Game> { s.isFavourite(it) }.thenBy { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
            .take(30)
        val fromGames = games.flatMap { g -> repo.matcher.scoredFor(g).filter { it.second >= 45 }.map { it.first }.take(10) }
        val quick = listOf<Sport?>(null, Sport.NBA, Sport.NFL, Sport.AFL).flatMap { sp -> repo.quickChannels(sp).flatMap { it.channels.take(6) } }
        return (fromGames + quick).distinctBy { it.id }
    }
}
