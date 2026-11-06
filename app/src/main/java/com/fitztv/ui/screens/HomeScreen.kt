package com.fitztv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.ChannelCard
import com.fitztv.ui.components.EmptyHint
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.GameCard
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.SectionTitle
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    games: List<Game>,
    channels: List<Channel>,
    sport: Sport?,
    settings: AppSettings,
    now: Long,
    onGame: (Game) -> Unit,
    onPick: (Game) -> Unit,
    onAddSource: () -> Unit,
) {
    val zone = settings.zone()
    val context = LocalContext.current
    val epg by FitzApp.instance.repo.epg.collectAsState()

    val filtered = remember(games, sport) { games.filter { sport == null || it.sport == sport } }
    val rows = remember(filtered, now, zone, settings.favoriteTeams) {
        val today = LocalDate.now(zone)
        val fav = settings.favoriteTeams
        fun isFav(g: Game) = fav.any { it.equals(g.home, true) || it.equals(g.away, true) }
        val live = filtered.filter { it.gameState == GameState.LIVE }
            .sortedWith(compareByDescending<Game> { isFav(it) }.thenBy { it.startUtcMillis })
        val soon = filtered.filter {
            it.gameState == GameState.PRE && it.startUtcMillis - now in 0..TimeUnit.HOURS.toMillis(3)
        }
        val todayRest = filtered.filter {
            it.gameState == GameState.PRE && Fmt.localDate(it.startUtcMillis, zone) == today && it !in soon
        }
        val tomorrow = filtered.filter { it.gameState == GameState.PRE && Fmt.localDate(it.startUtcMillis, zone) == today.plusDays(1) }
        val later = filtered.filter { it.gameState == GameState.PRE && Fmt.localDate(it.startUtcMillis, zone) > today.plusDays(1) }
        val results = filtered.filter { it.gameState == GameState.FINAL && now - it.startUtcMillis < TimeUnit.HOURS.toMillis(30) }
            .sortedByDescending { it.startUtcMillis }
        listOf(
            "🔴  Live now" to live,
            "Starting soon" to soon,
            "Later today" to todayRest,
            "Tomorrow" to tomorrow,
            "Upcoming" to later,
            "Recent results" to results,
        ).filter { it.second.isNotEmpty() }
    }
    val chans = remember(channels, sport) { channels.filter { sport == null || it.sport == sport }.sortedByDescending { it.quality } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (settings.sources.isEmpty()) {
            item {
                EmptyHint(
                    "Add your IPTV source",
                    "Fitz TV needs an Xtream Codes login or an M3U playlist URL. Only sports channels are kept.",
                )
            }
            item { NeonButton("Add source", onClick = onAddSource, modifier = Modifier.padding(start = 48.dp)) }
        }
        if (rows.isEmpty()) {
            item { EmptyHint("No games found", "The TV guide is updating, or there are no fixtures for this sport in the next week.") }
        }
        rows.forEach { (title, list) ->
            item(key = "t:$title") { SectionTitle("$title  (${list.size})") }
            item(key = "r:$title") {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(list, key = { it.id }) { g ->
                        GameCard(g, zone, settings.use24h, onClick = { onGame(g) }, onLongClick = { onPick(g) })
                    }
                }
            }
        }
        if (chans.isNotEmpty()) {
            item(key = "t:chans") { SectionTitle("${sport?.label ?: "Sports"} channels  (${chans.size})") }
            item(key = "r:chans") {
                val shown = chans.take(60)
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val nowProg = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
                        ChannelCard(c, nowProg, onClick = { PlaybackQueue.launch(context, chans, chans.indexOf(c)) })
                    }
                }
            }
        }
    }
}
