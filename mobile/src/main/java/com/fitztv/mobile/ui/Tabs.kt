package com.fitztv.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.favouriteEntries
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private fun LazyGridScope.header(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private val gridPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)

@Composable
fun HomeTab(
    games: List<Game>, channels: List<Channel>, sport: Sport?, settings: AppSettings, now: Long,
    onGame: (Game) -> Unit, onPick: (Game) -> Unit, onEditFavs: () -> Unit, onAddSource: () -> Unit,
    onTeam: (String) -> Unit = {},
) {
    val zone = settings.zone()
    val context = LocalContext.current
    val epg by FitzApp.instance.repo.epg.collectAsState()
    val repo = FitzApp.instance.repo
    val bucket = now / 300_000   // re-bucket rows every 5 min, not every clock tick
    val live = remember(games, sport, settings) { repo.liveNow(sport) }
    val nextUp = remember(games, sport, bucket) { if (live.isEmpty()) repo.nextUp(sport) else null }
    val quick = remember(channels, sport) { repo.quickChannels(sport) }
    val filtered = remember(games, sport, settings) {
        games.filter { (sport == null || it.sport == sport) && settings.showInGuide(it) }
    }
    val favs = remember(games, settings.favoriteTeams, now / 60_000) { favouriteEntries(games, settings, now, repo.logos) }
    val rows = remember(filtered, bucket, zone, settings) {
        val t = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val pre = filtered.filter { it.gameState == GameState.PRE }
        val dayOf = pre.associateWith { Fmt.localDate(it.startUtcMillis, zone) }
        val soon = pre.filter { it.startUtcMillis - t in 0..TimeUnit.HOURS.toMillis(3) }
        val soonIds = soon.mapTo(HashSet()) { it.id }
        listOf(
            "★  My teams — coming up" to pre.filter { settings.isFavourite(it) },
            "Starting soon" to soon,
            "Later today" to pre.filter { dayOf[it] == today && it.id !in soonIds },
            "Tomorrow" to pre.filter { dayOf[it] == today.plusDays(1) },
            "Upcoming" to pre.filter { dayOf[it]!! > today.plusDays(1) },
            "Recent results" to filtered.filter { it.gameState == GameState.FINAL && t - it.startUtcMillis < TimeUnit.HOURS.toMillis(30) }
                .sortedByDescending { it.startUtcMillis },
        ).filter { it.second.isNotEmpty() }
    }
    val chans = remember(channels, sport, settings.hiddenSports) {
        channels.filter { (sport == null || it.sport == sport) && settings.sportVisible(it.sport) }.sortedByDescending { it.quality }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp), modifier = Modifier.fillMaxSize(), contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (settings.sources.isEmpty()) {
            header("nosrc") {
                Column {
                    EmptyHint("Add your IPTV source", "Add an Xtream Codes login or M3U playlist URL in Settings. Only sports channels are kept.")
                    Button(onClick = onAddSource, colors = ButtonDefaults.buttonColors(containerColor = Neon.Purple)) { Text("Add source") }
                }
            }
        }
        // ---- Live now: always first, every top league ----
        header("liveh") { SectionHeader("🔴  Live now" + if (live.isNotEmpty()) "  (${live.size})" else "") }
        if (live.isEmpty()) header("livenone") {
            Text(
                "Nothing live right now" + (nextUp?.let { " — next up: ${it.matchup}, ${Fmt.whenLabel(it, zone, settings.use24h)}" } ?: ""),
                color = Neon.TextDim, fontSize = 14.sp,
            )
        }
        items(live, key = { "live:" + it.id }) { g -> GameCard(g, settings, onClick = { onGame(g) }, onLongClick = { onPick(g) }) }

        // ---- Quick channels: Fox Footy (All / AFL), Fox Sports News (All) ----
        if (quick.isNotEmpty()) {
            header("quickh") { SectionHeader("📺  Quick channels") }
            header("quick") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(quick, key = { it.label }) { q ->
                        NeonCard(
                            onClick = {
                                when {
                                    q.channels.isNotEmpty() -> PlaybackQueue.launch(context, q.channels, 0)
                                    com.fitztv.external.Kayo.isInstalled(context) -> com.fitztv.external.Kayo.open(context)
                                    else -> android.widget.Toast.makeText(context, "${q.label} isn't in your IPTV sources", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.width(220.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(q.label, color = Neon.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        q.channels.isNotEmpty() -> "Best: ${Fmt.resolution(repo.probe.height(q.channels.first()))}"
                                        com.fitztv.external.Kayo.isInstalled(context) -> "Opens Kayo"
                                        else -> "Not in your sources"
                                    },
                                    color = Neon.TextDim, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (favs.isNotEmpty()) {
            header("favh") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("★  My teams", Modifier.weight(1f))
                    Text("Edit", color = Neon.Magenta, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp).clickable(onClick = onEditFavs))
                }
            }
            header("favs") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favs, key = { it.team }) { e ->
                        FavouriteCard(e, settings, modifier = Modifier.width(250.dp), onClick = {
                            // Live: straight into the game. Otherwise: next 7 days + recent results.
                            val g = e.game
                            if (g != null && g.gameState == GameState.LIVE) onGame(g) else onTeam(e.team)
                        })
                    }
                }
            }
        }
        if (rows.isEmpty() && live.isEmpty() && settings.sources.isNotEmpty()) header("none") {
            EmptyHint("No upcoming games", "The guide is updating, or nothing is scheduled for this sport in the next week.")
        }
        rows.forEach { (title, list) ->
            header("h:$title") { SectionHeader("$title  (${list.size})") }
            items(list, key = { "$title:${it.id}" }) { g -> GameCard(g, settings, onClick = { onGame(g) }, onLongClick = { onPick(g) }) }
        }
        if (chans.isNotEmpty()) {
            header("ch") { SectionHeader("${sport?.label ?: "Sports"} channels  (${chans.size})") }
            items(chans.take(40), key = { "c:" + it.id }) { c ->
                val p = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
                ChannelCard(c, p, onClick = { PlaybackQueue.launch(context, chans, chans.indexOf(c)) })
            }
        }
    }
}

/** Guide: fixtures by day, or the channel grid from your provider's EPG. */
@Composable
fun GuideTab(games: List<Game>, sport: Sport?, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit) {
    var grid by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !grid, onClick = { grid = false }, label = { Text("🏆 Games") },
                colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.Purple, selectedLabelColor = Color.White))
            FilterChip(selected = grid, onClick = { grid = true }, label = { Text("📺 TV grid") },
                colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.Purple, selectedLabelColor = Color.White))
        }
        if (grid) GridGuideMobile(sport, settings) else GamesGuideTab(games, sport, settings, onGame, onPick)
    }
}

@Composable
private fun GamesGuideTab(games: List<Game>, sport: Sport?, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit) {
    val zone = settings.zone()
    val today = LocalDate.now(zone)
    var offset by rememberSaveable { mutableLongStateOf(0L) }
    val day = today.plusDays(offset)
    val list = remember(games, sport, day, zone, settings.hiddenSports) {
        games.filter { (sport == null || it.sport == sport) && settings.showInGuide(it) && Fmt.localDate(it.startUtcMillis, zone) == day }
            .sortedBy { it.startUtcMillis }
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((-1L..6L).toList()) { off ->
                FilterChip(
                    selected = off == offset, onClick = { offset = off }, label = { Text(Fmt.dayLabel(today.plusDays(off), zone)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.Purple, selectedLabelColor = Color.White),
                )
            }
        }
        Text("Times shown in ${zone.id}", color = Neon.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (list.isEmpty()) header("empty") { EmptyHint("Nothing scheduled", "No ${sport?.label ?: ""} games on ${Fmt.dayLabel(day, zone)}.") }
            items(list, key = { it.id }) { g ->
                NeonCard(onClick = { onGame(g) }, onLongClick = { onPick(g) }, highlighted = settings.isFavourite(g), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.time(g.startUtcMillis, zone, settings.use24h), color = Neon.Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(78.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill("${g.sport.emoji} ${g.league}", Neon.Purple)
                                if (g.gameState == GameState.LIVE) { Spacer(Modifier.width(6.dp)); LiveDot(); Spacer(Modifier.width(4.dp)); Text(g.statusText, color = Neon.Live, fontSize = 11.sp, maxLines = 1) }
                            }
                            Text(
                                if (g.home.isNotBlank()) "${g.away} vs ${g.home}" else g.title,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            if (g.gameState != GameState.PRE && g.home.isNotBlank())
                                Text("${g.awayScore ?: ""} – ${g.homeScore ?: ""}  ${if (g.gameState == GameState.FINAL) g.statusText else ""}", color = Neon.TextDim, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelsTab(channels: List<Channel>, sport: Sport?) {
    val context = LocalContext.current
    val epg by FitzApp.instance.repo.epg.collectAsState()
    val list = remember(channels, sport) {
        channels.filter { sport == null || it.sport == sport }
            .sortedWith(compareBy<Channel> { it.group }.thenBy { it.variantKey }.thenByDescending { it.quality })
    }
    if (list.isEmpty()) { EmptyHint("No channels", "Add a source in Settings, or pick a different sport.", Modifier.padding(16.dp)); return }
    val now = System.currentTimeMillis()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(list, key = { it.id }) { c ->
            val p = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
            ChannelCard(c, p, onClick = { PlaybackQueue.launch(context, list, list.indexOf(c)) })
        }
    }
}

