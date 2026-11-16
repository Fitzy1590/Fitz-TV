package com.fitztv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Catchup
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.SearchResults
import com.fitztv.data.search
import com.fitztv.data.gridRows
import com.fitztv.player.Launcher
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Leagues
import com.fitztv.sports.Sport
import com.fitztv.sports.StandingsClient
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ---- Guide: Fixtures by day, or the channel grid -----------------------------------------------

@Composable
fun GuideScreen(
    games: List<Game>, sport: Sport?, settings: AppSettings,
    onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onLeague: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    var grid by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = PAD, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Fixtures", onClick = { grid = false }, selected = !grid, icon = Glyph.Games)
            NeonButton("Channel grid", onClick = { grid = true }, selected = grid, icon = Glyph.Grid)
        }
        if (grid) GridGuide(sport, settings, onMessage) else FixturesScreen(games, sport, settings, onGame, onPick, onReplay, onLeague)
    }
}

/** Fixtures: day strip, games grouped by league as dense rows; Join live / From start from the game page. */
@Composable
fun FixturesScreen(games: List<Game>, sport: Sport?, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onLeague: (String) -> Unit) {
    val zone = settings.zone()
    val today = LocalDate.now(zone)
    var dayOffset by remember { mutableLongStateOf(0L) }
    val day = today.plusDays(dayOffset)
    val byLeague = remember(games, sport, day, zone, settings) {
        games.filter { g ->
            (sport == null || g.sport == sport) && (settings.showInGuide(g) || g.gameState == GameState.LIVE) &&
                (Fmt.localDate(g.startUtcMillis, zone) == day || (dayOffset == 0L && g.gameState == GameState.LIVE))
        }.sortedWith(compareBy<Game> { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
            .groupBy { it.league }.toList()
            .sortedWith(compareByDescending<Pair<String, List<Game>>> { (_, l) -> l.any { settings.isFavourite(it) } }.thenBy { it.first })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = PAD, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (-2L..6L).forEach { off ->
                val d = today.plusDays(off)
                val label = if (off == 0L) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " " + d.dayOfMonth
                NeonButton(label, onClick = { dayOffset = off }, selected = off == dayOffset)
            }
        }
        if (byLeague.isEmpty()) { EmptyHint("Nothing scheduled", "No ${sport?.label ?: ""} games on ${Fmt.dayLabel(day, zone)}."); return@Column }
        LazyVerticalGrid(
            GridCells.Adaptive(520.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = PAD, end = PAD, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            byLeague.forEach { (league, list) ->
                item(key = "h:$league", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        list.firstNotNullOfOrNull { it.leagueLogo }?.let { AsyncImage(it, null, Modifier.size(28.dp), contentScale = ContentScale.Fit) }
                        Text(league, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                        if (Leagues.byName(league)?.espnPath != null) NeonButton("Ladder & fixtures", onClick = { onLeague(league) }, icon = Glyph.Later)
                    }
                }
                gridItems(list, key = { it.id }) { g ->
                    val openPage = { if (g.gameState == GameState.FINAL) onReplay(g) else onPick(g) }
                    FixtureRow(g, settings, onLongClick = openPage) { if (g.gameState == GameState.LIVE) onGame(g) else openPage() }
                }
            }
        }
    }
}

@Composable
fun FixtureRow(g: Game, settings: AppSettings, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val live = g.gameState == GameState.LIVE
    NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (g.gameState) {
                    GameState.LIVE -> StatusBadge(true, "LIVE")
                    GameState.PRE -> Text(Fmt.time(g.startUtcMillis, settings.zone(), settings.use24h), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
                    GameState.FINAL -> Text("FT", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
                }
                if (live && g.statusText.isNotBlank()) Text(g.statusText, fontSize = 12.sp, color = Neon.Live, maxLines = 1, style = Tabular)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RowTeam(g.awayLogo, g.away.ifBlank { g.title }, g.awayShort, if (g.gameState != GameState.PRE) g.awayScore else null, live)
                if (g.home.isNotBlank()) RowTeam(g.homeLogo, g.home, g.homeShort, if (g.gameState != GameState.PRE) g.homeScore else null, live)
            }
        }
    }
}

@Composable
private fun RowTeam(logo: String?, name: String, short: String, score: String?, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, null, Modifier.size(30.dp), contentScale = ContentScale.Fit)
            else Text(short.ifBlank { name }.take(3).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
        }
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!score.isNullOrBlank()) Text(score, fontFamily = SairaCondensed, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (live) Neon.Live else Neon.Text, style = Tabular)
    }
}

private const val WINDOW_MIN = 180
private const val DP_PER_MIN = 6

/** Classic grid guide from your provider's EPG: channels down the side, time across the top. */
@Composable
fun GridGuide(sport: Sport?, settings: AppSettings, onMessage: (String) -> Unit) {
    val app = FitzApp.instance
    val epg by app.repo.epg.collectAsState()
    val channels by app.repo.channels.collectAsState()
    val now = System.currentTimeMillis()
    val slot = 30 * 60_000L
    var start by remember { mutableLongStateOf(now - now % slot) }
    val end = start + WINDOW_MIN * 60_000L
    val rows = remember(epg, channels, sport, settings.hiddenSports) { app.repo.gridRows(sport, settings) }
    val zone = settings.zone()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = PAD, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("Earlier", onClick = { start -= slot * 2 }, icon = Glyph.Earlier)
            NeonButton("Now", onClick = { start = now - now % slot }, selected = now in start until end)
            NeonButton("Later", onClick = { start += slot * 2 }, icon = Glyph.Later)
            Spacer(Modifier.width(16.dp))
            Text("${Fmt.dayLabel(Fmt.localDate(start, zone), zone)} · ${rows.size} channels with listings", color = Neon.TextFaint, fontSize = 12.sp)
        }
        Row(Modifier.padding(start = PAD + 212.dp)) {
            (0 until WINDOW_MIN / 30).forEach { i ->
                Text(Fmt.time(start + i * slot, zone, settings.use24h), color = Neon.Cyan, fontSize = 12.sp, modifier = Modifier.width((30 * DP_PER_MIN).dp))
            }
        }
        if (rows.isEmpty()) {
            EmptyHint("No programme listings", "Your sources haven't provided an EPG for these channels yet. Add an XMLTV URL to a source to fill this grid.")
        } else LazyColumn(contentPadding = PaddingValues(start = PAD, end = 24.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.channel.id }) { row ->
                Row(Modifier.height(58.dp)) {
                    NeonSurface(onClick = { PlaybackQueue.launch(row.variants, 0) }, modifier = Modifier.width(200.dp).fillMaxHeight(), radius = 8.dp) {
                        Row(Modifier.padding(horizontal = 8.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                            if (row.channel.logo != null) { AsyncImage(row.channel.logo, null, Modifier.size(width = 40.dp, height = 28.dp)); Spacer(Modifier.width(8.dp)) }
                            Text(row.channel.label, fontSize = 13.sp, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.width((WINDOW_MIN * DP_PER_MIN).dp).fillMaxHeight()) {
                        if (now in start until end) Box(Modifier.offset(x = (((now - start) / 60_000L) * DP_PER_MIN).toInt().dp).width(2.dp).fillMaxHeight().background(Neon.Live))
                        row.programs.filter { it.stopMillis > start && it.startMillis < end }.forEach { p ->
                            val s0 = maxOf(p.startMillis, start)
                            val e0 = minOf(p.stopMillis, end)
                            val x = ((s0 - start) / 60_000L * DP_PER_MIN).toInt()
                            val w = (((e0 - s0) / 60_000L) * DP_PER_MIN).toInt().coerceAtLeast(12)
                            val live = now in p.startMillis until p.stopMillis
                            val past = p.stopMillis <= now
                            NeonSurface(
                                onClick = {
                                    when {
                                        live -> PlaybackQueue.launch(row.variants, 0)
                                        past -> {
                                            val url = Catchup.url(row.channel, p.startMillis, ((p.stopMillis - p.startMillis) / 60_000L).toInt() + 10)
                                            if (url != null) PlaybackQueue.launch(listOf(row.channel.copy(id = "cu:${row.channel.id}:${p.startMillis}", streamUrl = url, vod = true, name = p.title)), 0)
                                            else onMessage("No catch-up on this channel")
                                        }
                                        else -> onMessage("Starts ${Fmt.time(p.startMillis, zone, settings.use24h)} — right-click to record")
                                    }
                                },
                                onLongClick = {
                                    if (!past) {
                                        com.fitztv.record.Recorder.schedule(app, p.title, row.channel.sport.name, row.variants, p.startMillis, p.stopMillis)
                                        onMessage("Recording ${p.title}")
                                    }
                                },
                                modifier = Modifier.offset(x = x.dp).width((w - 4).coerceAtLeast(8).dp).fillMaxHeight(), radius = 8.dp, selected = live,
                            ) {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(p.title, fontSize = 13.sp, fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (past) Neon.TextFaint else Neon.Text)
                                    Text("${Fmt.time(p.startMillis, zone, settings.use24h)}–${Fmt.time(p.stopMillis, zone, settings.use24h)}", fontSize = 10.sp, color = Neon.TextDim, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Channels ---------------------------------------------------------------------------------

@Composable
fun ChannelsScreen(channels: List<Channel>, sport: Sport?) {
    val epg by FitzApp.instance.repo.epg.collectAsState()
    val list = remember(channels, sport) {
        channels.filter { sport == null || it.sport == sport }.sortedWith(compareBy<Channel> { it.group }.thenBy { it.variantKey }.thenByDescending { it.quality })
    }
    if (list.isEmpty()) { EmptyHint("No channels", "Add a source in Settings, or pick a different sport."); return }
    val now = System.currentTimeMillis()
    LazyVerticalGrid(
        GridCells.Adaptive(240.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PAD, end = PAD, top = 8.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(list, key = { it.id }) { c ->
            val p = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
            ChannelCard(c, p, onClick = { PlaybackQueue.launch(list, list.indexOf(c)) })
        }
    }
}

// ---- Replays ----------------------------------------------------------------------------------

@Composable
fun ReplaysScreen(app: FitzApp, sport: Sport?, onReplay: (Game) -> Unit) {
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val vod by app.repo.vod.collectAsState()
    val recent = remember(games, sport, settings.favoriteTeams) { app.repo.replayGames(sport) }
    val library = remember(vod, sport) { com.fitztv.data.VodLibrary.of(vod, sport, 300) }
    val recordings by com.fitztv.record.Recorder.recordings.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (recordings.isNotEmpty()) {
            item { SectionTitle("My recordings  ${recordings.size}", icon = Glyph.Record) }
            item { CardRow { items(recordings.sortedByDescending { it.startMillis }, key = { it.id }) { r -> RecordingCard(app, r) } } }
        }
        item { SectionTitle("Recent games  ${recent.size}") }
        item {
            if (recent.isEmpty()) EmptyHint("No finished games yet", "Results from the past week appear here once games finish.")
            else CardRow(spacing = 20.dp) { items(recent, key = { it.id }) { g -> GameCard(g, settings.zone(), settings.use24h, onClick = { onReplay(g) }, favourite = settings.isFavourite(g)) } }
        }
        item { SectionTitle("Replay library  ${library.size}") }
        if (library.isEmpty()) item { EmptyHint("No replays in your provider's library", "Sport categories in your Xtream / M3U on-demand section show up here.") }
        items(library, key = { it.id }) { v ->
            NeonSurface(onClick = { PlaybackQueue.launch(listOf(v.asChannel()), 0, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = PAD, vertical = 3.dp), radius = 10.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.fitztv.data.VodLibrary.dateLabel(v), fontSize = 14.sp, color = Neon.TextDim, style = Tabular, modifier = Modifier.width(120.dp))
                    Text(v.shortTitle, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(v.category.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = Neon.TextFaint, maxLines = 1, modifier = Modifier.padding(start = 16.dp).width(220.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(app: FitzApp, r: com.fitztv.record.Recording) {
    var confirm by remember { mutableStateOf(false) }
    NeonSurface(
        onClick = {
            when (r.status) {
                "recording", "done" -> PlaybackQueue.launch(listOf(
                    com.fitztv.data.VodItem(r.id, r.title, "Recording", runCatching { Sport.valueOf(r.sport) }.getOrDefault(Sport.GENERAL), java.io.File(r.file).toURI().toASCIIString().replaceFirst("file:/+", "file:///")).asChannel()), 0, null)
                else -> Unit
            }
        },
        onLongClick = {
            if (!confirm) confirm = true
            else if (r.status == "recording" || r.status == "scheduled") com.fitztv.record.Recorder.stop(app, r.id)
            else com.fitztv.record.Recorder.delete(app, r.id)
        },
        modifier = Modifier.width(300.dp).height(120.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Fmt.time(r.startMillis, AppSettings.ZONE, false) + " · " + "%.1f GB".format(r.sizeBytes / 1e9), fontSize = 12.sp, color = Neon.TextDim, style = Tabular)
            Text(
                when (r.status) { "scheduled" -> "Scheduled"; "recording" -> "Recording now — click to watch"; "done" -> "Ready to watch"; "cancelled" -> "Cancelled"; else -> "Recording failed" },
                fontSize = 12.sp, color = when (r.status) { "recording" -> Neon.Live; "done" -> Neon.Text; else -> Neon.TextDim },
            )
            Text(if (confirm) (if (r.status == "recording" || r.status == "scheduled") "Right-click again to stop" else "Right-click again to delete") else "Right-click: stop / delete", fontSize = 12.sp, color = Neon.TextFaint)
        }
    }
}

// ---- Search -----------------------------------------------------------------------------------

@Composable
fun SearchScreen(app: FitzApp, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onTeam: (String) -> Unit) {
    val settings by app.settings.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(250)
        results = withContext(Dispatchers.Default) { app.repo.search(query, settings) }
    }
    val zone = settings.zone()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search teams, games, channels, replays") },
                colors = fieldColors(), modifier = Modifier.padding(horizontal = PAD, vertical = 8.dp).fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.length >= 2 && results.isEmpty) item { Text("No matches for “$query”", color = Neon.TextDim, modifier = Modifier.padding(horizontal = PAD)) }
        if (results.teams.isNotEmpty()) {
            item { SectionTitle("Teams") }
            item {
                CardRow {
                    items(results.teams, key = { it.name + it.league }) { t ->
                        val fav = settings.favoriteTeams.any { com.fitztv.data.sameTeam(it, t.name) }
                        NeonSurface(onClick = { onTeam(t.name) }, onLongClick = {
                            app.settings.update { s -> s.copy(favoriteTeams = if (fav) s.favoriteTeams.filterNot { com.fitztv.data.sameTeam(it, t.name) }.toSet() else s.favoriteTeams + t.name) }
                        }, modifier = Modifier.width(260.dp), selected = fav) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                (t.logo ?: app.repo.logos.get(t.name))?.let { AsyncImage(it, null, Modifier.size(36.dp)); Spacer(Modifier.width(12.dp)) }
                                Column {
                                    if (fav) Text("My team", fontSize = 12.sp, color = Neon.Purple, fontWeight = FontWeight.Medium)
                                    Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.league + " · right-click to " + (if (fav) "remove from My teams" else "add to My teams"), fontSize = 12.sp, color = Neon.TextDim, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (results.games.isNotEmpty()) {
            item { SectionTitle("Games") }
            item { CardRow(spacing = 20.dp) { items(results.games, key = { it.id }) { g -> GameCard(g, zone, settings.use24h, onClick = { if (g.gameState == GameState.FINAL) onReplay(g) else onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g)) } } }
        }
        if (results.channels.isNotEmpty()) {
            item { SectionTitle("Channels") }
            item { CardRow { items(results.channels, key = { it.id }) { c -> ChannelCard(c, app.repo.nowPlaying(c), onClick = { PlaybackQueue.launch(listOf(c) + app.repo.variantsOf(c), 0) }) } } }
        }
        if (results.replays.isNotEmpty()) {
            item { SectionTitle("Replays") }
            item {
                CardRow {
                    items(results.replays, key = { it.id }) { v ->
                        NeonSurface(onClick = { PlaybackQueue.launch(listOf(v.asChannel()), 0, null) }, modifier = Modifier.width(280.dp).height(90.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(v.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(v.category, fontSize = 11.sp, color = Neon.TextDim, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple,
    unfocusedBorderColor = Neon.Line, focusedLabelColor = Neon.Purple, unfocusedLabelColor = Neon.TextDim, cursorColor = Neon.Purple,
)
