package com.fitztv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
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
import com.fitztv.ui.components.ChannelTile
import androidx.compose.ui.focus.focusRestorer
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
    onEditFavourites: () -> Unit,
    onTeam: (String) -> Unit = {},
) {
    val zone = settings.zone()
    val context = LocalContext.current
    val epg by FitzApp.instance.repo.epg.collectAsState()

    val repo = FitzApp.instance.repo
    // Live now: every live game from every top league, even ones outside the guide.
    // Rows only need re-bucketing every 5 minutes, not on every 30-second clock tick.
    val bucket = now / 300_000
    val live = remember(games, sport, settings) { repo.liveNow(sport) }
    val nextUp = remember(games, sport, bucket) { if (live.isEmpty()) repo.nextUp(sport) else null }
    val filtered = remember(games, sport, settings) {
        games.filter { (sport == null || it.sport == sport) && settings.showInGuide(it) }
    }
    val rows = remember(filtered, bucket, zone, settings) {
        val t = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        // One pass: work out each game's local day once.
        val pre = filtered.filter { it.gameState == GameState.PRE }
        val day = pre.associateWith { Fmt.localDate(it.startUtcMillis, zone) }
        val favUpcoming = pre.filter { settings.isFavourite(it) }
        val soon = pre.filter { it.startUtcMillis - t in 0..TimeUnit.HOURS.toMillis(3) }
        val soonIds = soon.mapTo(HashSet()) { it.id }
        val todayRest = pre.filter { day[it] == today && it.id !in soonIds }
        val tomorrow = pre.filter { day[it] == today.plusDays(1) }
        val later = pre.filter { day[it]!! > today.plusDays(1) }
        val results = filtered.filter { it.gameState == GameState.FINAL && t - it.startUtcMillis < TimeUnit.HOURS.toMillis(30) }
            .sortedByDescending { it.startUtcMillis }
        listOf(
            "My teams — coming up" to favUpcoming,
            "Starting soon" to soon,
            "Later today" to todayRest,
            "Tomorrow" to tomorrow,
            "Upcoming" to later,
            "Recent results" to results,
        ).filter { it.second.isNotEmpty() }
    }
    val quick = remember(channels, sport) { repo.quickChannels(sport) }
    val chans = remember(channels, sport, settings.hiddenSports) {
        channels.filter { (sport == null || it.sport == sport) && settings.sportVisible(it.sport) }.sortedByDescending { it.quality }
    }

    // Hero: your teams live, big live games, then your teams' next games.
    val heroItems = remember(games, sport, bucket, settings.favoriteTeams) {
        val favLive = live.filter { settings.isFavourite(it) }
        val bigLive = live.filter { it.featured && !settings.isFavourite(it) }
        val favNext = filtered.filter { it.gameState == GameState.PRE && settings.isFavourite(it) }.sortedBy { it.startUtcMillis }.take(2)
        (favLive + bigLive + favNext + live).distinctBy { it.id }.take(6)
    }
    val liveAndSoon = remember(live, rows) { (live + rows.filter { it.first == "Starting soon" || it.first == "Later today" }.flatMap { it.second }).distinctBy { it.id } }
    val laterRows = remember(rows) { rows.filter { it.first in setOf("Tomorrow", "Upcoming", "Recent results") } }
    val directory by repo.teams.teams.collectAsState()
    val favs = remember(games, settings.favoriteTeams, bucket, sport, directory) {
        com.fitztv.data.favouriteEntries(games, settings, now, repo.logos, sport, directory)
    }
    // Live channels: pinned channels, then sports channels whose guide says a live event is on now.
    val liveChannels = remember(channels, epg, sport, bucket) {
        val pinned = quick.mapNotNull { q -> q.channels.firstOrNull()?.let { Triple(q.label, it, q.channels) } }
        val pinnedIds = pinned.map { it.second.id }.toSet()
        val onNow = channels.asSequence()
            .filter { (sport == null || it.sport == sport) && it.id !in pinnedIds && it.logo != null }
            .mapNotNull { c -> repo.nowPlaying(c)?.takeIf { p -> p.title.contains("live", true) }?.let { Triple(c.label, c, listOf(c)) } }
            .distinctBy { it.second.variantKey }.take(12).toList()
        pinned + onNow
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)) {
        if (settings.sources.isEmpty()) {
            item {
                EmptyHint(
                    "Add your IPTV source",
                    "Fitz TV needs an Xtream Codes login or an M3U playlist URL. Only sports channels are kept.",
                )
            }
            item { NeonButton("Add source", onClick = onAddSource, modifier = Modifier.padding(start = 48.dp)) }
        }
        if (heroItems.isNotEmpty()) item(key = "hero") {
            HeroCarousel(heroItems, settings, onWatch = onGame, onStreams = onPick)
        }
        // ---- Live & upcoming ----
        item(key = "t:live") { SectionTitle(if (live.isNotEmpty()) "Live & upcoming" else "Coming up", live = live.isNotEmpty()) }
        item(key = "r:live") {
            if (liveAndSoon.isEmpty()) {
                androidx.tv.material3.Text(
                    "Nothing live right now" + (nextUp?.let { " — next up: ${it.matchup}, ${Fmt.whenLabel(it, zone, settings.use24h)}" } ?: ""),
                    color = com.fitztv.ui.theme.Neon.TextDim, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                )
            } else PosterRow(liveAndSoon, zone, settings, onGame, onPick)
        }
        // ---- My teams ----
        if (favs.isNotEmpty()) {
            item(key = "t:mine") { SectionTitle("My teams") }
            item(key = "r:mine") {
                LazyRow(Modifier.focusRestorerCompat(), contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(favs, key = { "fav:" + it.team }) { e ->
                        androidx.compose.foundation.layout.Box(Modifier.width(300.dp)) {
                            FavouriteCard(e, settings, onClick = {
                                val g = e.game
                                if (g != null && g.gameState == GameState.LIVE) onGame(g) else onTeam(e.team)
                            })
                        }
                    }
                    item(key = "fav:edit") { NeonButton("Edit teams", onClick = onEditFavourites, icon = com.fitztv.ui.components.Glyph.Star) }
                }
            }
        }
        // ---- Live channels ----
        if (liveChannels.isNotEmpty()) {
            item(key = "t:quick") { SectionTitle("Live channels", icon = com.fitztv.ui.components.Glyph.Tv) }
            item(key = "r:quick") {
                LazyRow(Modifier.focusRestorerCompat(), contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(liveChannels, key = { "lc:" + it.second.id }) { (label, c, list) ->
                        ChannelTile(label, c.logo, repo.nowPlaying(c)?.title, onClick = { PlaybackQueue.launch(context, list, 0) })
                    }
                }
            }
        }
        if (liveAndSoon.isEmpty() && laterRows.isEmpty()) {
            item { EmptyHint("No upcoming games", "The TV guide is updating, or there are no fixtures for this sport in the next week.") }
        }
        laterRows.forEach { (title, list) ->
            item(key = "t:$title") { SectionTitle("$title  ${list.size}") }
            item(key = "r:$title") { PosterRow(list, zone, settings, onGame, onPick) }
        }
        if (chans.isNotEmpty()) {
            item(key = "t:chans") { SectionTitle("${sport?.label ?: "Sports"} channels  ${chans.size}") }
            item(key = "r:chans") {
                val shown = chans.take(60)
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val nowProg = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
                        ChannelCard(c, nowProg, onClick = { PlaybackQueue.launch(context, chans, chans.indexOf(c)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(list: List<Game>, zone: java.time.ZoneId, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit) {
    LazyRow(Modifier.focusRestorerCompat(), contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        items(list, key = { it.id }) { g ->
            com.fitztv.ui.components.PosterCard(g, zone, settings.use24h, onClick = { onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g))
        }
    }
}

/** Returning to a row puts focus back on the item you left (no jump to the first card). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.focusRestorerCompat(): Modifier = this.then(Modifier.focusRestorer())
