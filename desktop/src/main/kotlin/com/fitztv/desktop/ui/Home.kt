package com.fitztv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Home: featured trio, live now, coming up, my teams, live channels, later days, channels. */
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
    onTeam: (String) -> Unit,
) {
    val zone = settings.zone()
    val repo = FitzApp.instance.repo
    val epg by repo.epg.collectAsState()

    // Rows only need re-bucketing every 5 minutes, not on every clock tick.
    val bucket = now / 300_000
    val live = remember(games, sport, settings) { repo.liveNow(sport) }
    val nextUp = remember(games, sport, bucket) { if (live.isEmpty()) repo.nextUp(sport) else null }
    val filtered = remember(games, sport, settings) { games.filter { (sport == null || it.sport == sport) && settings.showInGuide(it) } }
    val rows = remember(filtered, bucket, zone, settings) {
        val t = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val pre = filtered.filter { it.gameState == GameState.PRE }
        val day = pre.associateWith { Fmt.localDate(it.startUtcMillis, zone) }
        val soon = pre.filter { it.startUtcMillis - t in 0..TimeUnit.HOURS.toMillis(3) }
        val soonIds = soon.mapTo(HashSet()) { it.id }
        val todayRest = pre.filter { day[it] == today && it.id !in soonIds }
        val tomorrow = pre.filter { day[it] == today.plusDays(1) }
        val later = pre.filter { day[it]!! > today.plusDays(1) }
        val results = filtered.filter { it.gameState == GameState.FINAL && t - it.startUtcMillis < TimeUnit.HOURS.toMillis(30) }.sortedByDescending { it.startUtcMillis }
        listOf("Starting soon" to soon, "Later today" to todayRest, "Tomorrow" to tomorrow, "Upcoming" to later, "Recent results" to results).filter { it.second.isNotEmpty() }
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
    val liveChannels = remember(channels, epg, sport, bucket) {
        val pinned = quick.mapNotNull { q -> q.channels.firstOrNull()?.let { Triple(q.label, it, q.channels) } }
        val pinnedIds = pinned.map { it.second.id }.toSet()
        val onNow = channels.asSequence()
            .filter { (sport == null || it.sport == sport) && it.id !in pinnedIds && it.logo != null }
            .mapNotNull { c -> repo.nowPlaying(c)?.takeIf { p -> p.title.contains("live", true) }?.let { Triple(c.label, c, listOf(c)) } }
            .distinctBy { it.second.variantKey }.take(12).toList()
        (pinned + onNow).distinctBy { it.second.id }
    }

    // Rows that load in above the viewport (hero, live now) would otherwise push the page down
    // (a lazy list keeps the item it was showing): go back to the top when they first appear.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(heroItems.isNotEmpty(), live.isNotEmpty(), sport) { listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)) {
        if (settings.sources.isEmpty()) {
            item { EmptyHint("Add your IPTV source", "Fitz TV needs an Xtream Codes login or an M3U playlist URL. Only sports channels are kept.") }
            item { NeonButton("Add source", onClick = onAddSource, modifier = Modifier.padding(start = PAD)) }
        }
        if (heroItems.isNotEmpty()) item(key = "hero") { FeaturedTrio(heroItems.take(3), settings, onWatch = onGame, onStreams = onPick) }
        if (live.isNotEmpty()) {
            item(key = "t:now") { SectionTitle("Live now  ${live.size}", live = true) }
            item(key = "r:now") { PosterRow(live, settings, onGame, onPick) }
        }
        val soonOnly = liveAndSoon.filter { it.gameState != GameState.LIVE }
        item(key = "t:live") { SectionTitle("Coming up") }
        item(key = "r:live") {
            if (soonOnly.isEmpty()) {
                Text(
                    (if (live.isEmpty()) "Nothing live right now" else "Nothing else starting soon") + (nextUp?.let { " — next up: ${it.matchup}, ${Fmt.whenLabel(it, zone, settings.use24h)}" } ?: ""),
                    color = Neon.TextDim, fontSize = 15.sp, modifier = Modifier.padding(start = PAD, bottom = 4.dp),
                )
            } else PosterRow(soonOnly, settings, onGame, onPick)
        }
        if (favs.isNotEmpty()) {
            item(key = "t:mine") { SectionTitle("My teams") }
            item(key = "r:mine") {
                CardRow {
                    items(favs.distinctBy { it.team }, key = { "fav:" + it.team }) { e ->
                        Box(Modifier.width(290.dp)) {
                            FavouriteCard(e, settings, onClick = {
                                val g = e.game
                                if (g != null && g.gameState == GameState.LIVE) onGame(g) else onTeam(e.team)
                            })
                        }
                    }
                    item(key = "fav:edit") { NeonButton("Edit teams", onClick = onEditFavourites, icon = Glyph.Star) }
                }
            }
        }
        if (liveChannels.isNotEmpty()) {
            item(key = "t:quick") { SectionTitle("Live channels", icon = Glyph.Tv) }
            item(key = "r:quick") {
                CardRow {
                    items(liveChannels, key = { "lc:" + it.second.id }) { (label, c, list) ->
                        ChannelTile(label, remember(c.id) { com.fitztv.data.ChannelLogos.urlFor(c.label) ?: c.logo }, repo.nowPlaying(c)?.title, onClick = { PlaybackQueue.launch(list, 0) })
                    }
                }
            }
        }
        if (liveAndSoon.isEmpty() && laterRows.isEmpty()) item { EmptyHint("No upcoming games", "The TV guide is updating, or there are no fixtures for this sport in the next week.") }
        laterRows.forEach { (title, list) ->
            item(key = "t:$title") { SectionTitle("$title  ${list.size}") }
            item(key = "r:$title") { PosterRow(list, settings, onGame, onPick) }
        }
        if (chans.isNotEmpty()) {
            item(key = "t:chans") { SectionTitle("${sport?.label ?: "Sports"} channels  ${chans.size}") }
            item(key = "r:chans") {
                val shown = chans.take(60)
                CardRow {
                    items(shown, key = { it.id }) { c ->
                        val nowProg = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
                        ChannelCard(c, nowProg, onClick = { PlaybackQueue.launch(chans, chans.indexOf(c)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(list: List<Game>, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit) {
    // Rows key their cards by game id: the same game twice in one row would crash the list.
    val unique = remember(list) { list.distinctBy { it.id } }
    CardRow(spacing = 20.dp) {
        items(unique, key = { it.id }) { g ->
            PosterCard(g, settings.zone(), settings.use24h, onClick = { onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g))
        }
    }
}

/** A favourite team: its live / next / last game. */
@Composable
fun FavouriteCard(e: com.fitztv.data.FavouriteEntry, settings: AppSettings, onClick: () -> Unit) {
    val g = e.game
    NeonSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(96.dp), radius = 12.dp) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(e.logo, e.team, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.team, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (g?.gameState) {
                    GameState.LIVE -> Text("LIVE · ${g.awayShort} ${g.awayScore ?: 0}–${g.homeScore ?: 0} ${g.homeShort}", fontSize = 13.sp, color = Neon.Live, maxLines = 1, style = Tabular)
                    GameState.PRE -> Text("Next: ${Fmt.whenLabel(g, settings.zone(), settings.use24h)}", fontSize = 13.sp, color = Neon.TextDim, maxLines = 1)
                    GameState.FINAL -> Text("Final ${g.awayShort} ${g.awayScore ?: ""}–${g.homeScore ?: ""} ${g.homeShort}", fontSize = 13.sp, color = Neon.TextDim, maxLines = 1)
                    null -> Text("No games in the guide", fontSize = 13.sp, color = Neon.TextFaint)
                }
            }
        }
    }
}

/**
 * Home featured trio: one big game on the left (fanart, matchup art, Watch / Streams) and two
 * smaller ones stacked on the right. Your teams' and the biggest games, in that order.
 */
@Composable
fun FeaturedTrio(items: List<Game>, settings: AppSettings, onWatch: (Game) -> Unit, onStreams: (Game) -> Unit) {
    if (items.isEmpty()) return
    val g = items[0]
    val side = items.drop(1).take(2)
    val zone = settings.zone()
    val repo = FitzApp.instance.repo
    val fanTeam = when {
        settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.home, g.sport) } -> g.home
        settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.away, g.sport) } -> g.away
        else -> g.home
    }
    val fanart by produceState<String?>(null, fanTeam) { value = runCatching { repo.logos.fanart(fanTeam).firstOrNull() }.getOrNull() }

    Row(Modifier.fillMaxWidth().height(340.dp).padding(horizontal = PAD), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NeonSurface(
            onClick = { if (g.gameState == GameState.LIVE) onWatch(g) else onStreams(g) },
            onLongClick = { onStreams(g) },
            modifier = Modifier.weight(1.9f).fillMaxHeight(), radius = 16.dp,
        ) {
            Box(Modifier.fillMaxSize().background(Neon.Bg2))
            fanart?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.5f) }
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().androidxAspect()) { MatchupArt(g, Modifier.fillMaxSize(), logoSize = 100.dp) }
            // Legibility scrim for the text on the left (functional, not decoration).
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Neon.Bg2, 0.46f to Neon.Bg2.copy(alpha = 0.92f), 0.7f to Color.Transparent)))
            Column(Modifier.fillMaxHeight().width(420.dp).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val live = g.gameState == GameState.LIVE
                    Text(
                        if (live) "LIVE" else Fmt.whenLabel(g, zone, settings.use24h), fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tabular,
                        color = if (live) Color.White else Color(0xFF111215),
                        modifier = Modifier.background(if (live) Neon.Live else Color(0xFFF2F1EE), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Text(g.league, fontSize = 15.sp, color = Neon.TextDim, maxLines = 1)
                    if (settings.isFavourite(g)) Text("Your team", fontSize = 15.sp, color = Neon.Purple, maxLines = 1)
                }
                Spacer(Modifier.height(14.dp))
                Text(g.vsTitle(), fontFamily = SairaCondensed, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 42.sp)
                Spacer(Modifier.height(6.dp))
                Text(posterSubtitle(g), fontSize = 17.sp, color = Neon.TextDim, style = Tabular, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton(if (g.gameState == GameState.LIVE) "Watch" else "Streams", selected = true, icon = Glyph.Play, onClick = { if (g.gameState == GameState.LIVE) onWatch(g) else onStreams(g) })
                    if (g.gameState == GameState.LIVE) NeonButton("Streams", icon = Glyph.Tv, onClick = { onStreams(g) })
                }
            }
        }
        if (side.isNotEmpty()) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            side.forEach { sg ->
                NeonSurface(onClick = { if (sg.gameState == GameState.LIVE) onWatch(sg) else onStreams(sg) }, onLongClick = { onStreams(sg) }, modifier = Modifier.weight(1f).fillMaxWidth(), radius = 12.dp) {
                    MatchupArt(sg, Modifier.fillMaxSize(), logoSize = 46.dp)
                    PosterBadge(sg, zone, settings.use24h)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Column {
                            Text(sg.vsTitle(), fontFamily = SairaCondensed, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(posterSubtitle(sg), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), style = Tabular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.androidxAspect(): Modifier = this.then(Modifier.aspectRatio169())
private fun Modifier.aspectRatio169(): Modifier = aspectRatio(16f / 9f)
