package com.fitztv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.alerts.AppAlert
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.Repository
import com.fitztv.data.Team
import com.fitztv.data.sameTeam
import com.fitztv.player.Launcher
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.LiveStats
import com.fitztv.sports.Leagues
import com.fitztv.sports.StandingsClient
import com.fitztv.sports.StatsClient
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.fitztv.sports.Text as T

@Composable
fun Modal(onDismiss: () -> Unit, width: androidx.compose.ui.unit.Dp = 760.dp, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(24.dp),
        ) { content() }
    }
}

private enum class HubTab(val label: String) { STREAMS("Streams"), STATS("Box score"), REPLAYS("Replays") }

/** Game page: scoreboard, main actions, then tabs — Streams (not finished), Replays (finished) and Box score. */
@Composable
fun GameHub(app: FitzApp, game: Game, startReplays: Boolean, onGame: (Game) -> Unit, onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    val tabs = remember(game.id, game.gameState) {
        buildList {
            if (game.gameState != GameState.FINAL) add(HubTab.STREAMS)
            if (game.gameState == GameState.FINAL) add(HubTab.REPLAYS)
            if (game.gameState != GameState.PRE && game.espnPath != null) add(HubTab.STATS)
        }
    }
    var tab by remember(game.id) { mutableStateOf(if (startReplays && HubTab.REPLAYS in tabs) HubTab.REPLAYS else tabs.first()) }
    val zone = settings.zone()
    Modal(onDismiss, width = 900.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(game.league.uppercase(), fontFamily = SairaCondensed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HubTeam(game.awayLogo, game.awayShort.ifBlank { game.away }, Modifier.weight(1f))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (game.gameState != GameState.PRE && game.home.isNotBlank())
                        Text("${game.awayScore ?: 0} – ${game.homeScore ?: 0}", fontFamily = SairaCondensed, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Neon.Text, style = Tabular)
                    else Text("v", fontFamily = SairaCondensed, fontSize = 34.sp, color = Neon.TextDim)
                    val status = when (game.gameState) {
                        GameState.LIVE -> game.statusText.ifBlank { "LIVE" }
                        GameState.FINAL -> game.statusText.ifBlank { "Final" }
                        GameState.PRE -> Fmt.whenLabel(game, zone, settings.use24h)
                    }
                    Text(status, fontSize = 15.sp, color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, style = Tabular, textAlign = TextAlign.Center)
                }
                HubTeam(game.homeLogo, game.homeShort.ifBlank { game.home }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (game.gameState) {
                    GameState.LIVE -> {
                        NeonButton("Join live", onClick = { onGame(game) }, selected = true, icon = Glyph.Play)
                        NeonButton("From start", icon = Glyph.Start, onClick = { scope.launch { if (!Launcher.play(game, fromStart = true)) tab = HubTab.STREAMS } })
                    }
                    GameState.PRE -> {
                        val reminded = game.id in settings.reminders
                        NeonButton(if (reminded) "Reminder set" else "Remind me", onClick = { app.alerts.toggleReminder(game) }, selected = reminded, icon = if (reminded) Glyph.BellOn else Glyph.Bell)
                    }
                    GameState.FINAL -> Unit
                }
                NeonButton("Close", onClick = onDismiss, icon = Glyph.Remove)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { tabs.forEach { t -> NeonButton(t.label, onClick = { tab = t }, selected = t == tab) } }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Line))
            Box(Modifier.fillMaxWidth()) {
                when (tab) {
                    HubTab.STREAMS -> StreamsPane(app, game, onDismiss)
                    HubTab.REPLAYS -> ReplayPane(app, game, onDismiss)
                    HubTab.STATS -> LiveStatsPanel(game)
                }
            }
        }
    }
}

@Composable
private fun HubTeam(logo: String?, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, null, Modifier.size(66.dp), contentScale = ContentScale.Fit)
            else Text(name.take(3).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
        }
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Remind / record / multiview chips and the ranked stream list. */
@Composable
fun StreamsPane(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    var matched by remember { mutableStateOf<List<Channel>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(game.id) { matched = app.repo.rankedStreams(game) }
    val m = matched.orEmpty()
    val others = remember(m) {
        val ids = m.map { it.id }.toSet()
        app.repo.fallbackChannels(game).filter { it.id !in ids }
    }
    val all = m + others
    val scope = rememberCoroutineScope()
    Column {
        val reminded = game.id in settings.reminders
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (game.gameState == GameState.PRE) NeonButton(if (reminded) "Reminder set" else "Remind me", selected = reminded, icon = if (reminded) Glyph.BellOn else Glyph.Bell, onClick = { app.alerts.toggleReminder(game) })
            if (game.gameState != GameState.FINAL) {
                NeonButton("Record", icon = Glyph.Record, onClick = {
                    if (m.isNotEmpty()) {
                        val r = com.fitztv.record.Recorder.scheduleGame(app, game, m)
                        note = if (r.status == "scheduled" && r.startMillis > System.currentTimeMillis() + 60_000) "Recording scheduled" else "Recording started — find it in Replays › My recordings"
                    } else note = "No stream found to record"
                })
                NeonButton("Multiview", icon = Glyph.Multiview, onClick = { onDismiss(); scope.launch { Launcher.addToMultiview(game) } })
            }
            note?.let { Text(it, color = Neon.Purple, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        if (matched == null) Text("Finding the best stream…", color = Neon.TextDim)
        else if (all.isEmpty()) Text("No matching channels in your sources.", color = Neon.TextDim)
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(all, key = { _, c -> c.id }) { i, c ->
                NeonSurface(
                    onClick = { onDismiss(); if (i < m.size) PlaybackQueue.launch(m, i, game.id) else PlaybackQueue.launch(listOf(all[i]) + m, 0, game.id) },
                    modifier = Modifier.fillMaxWidth(), radius = 10.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i < m.size) Pill("Match", Neon.Purple) else Pill(c.sport.label, Neon.TextFaint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.label, fontSize = 15.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = remember(c.id) { com.fitztv.data.StreamNotes.line(app.repo, c, game) }
                            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(app.repo.probe.label(c), color = if (app.repo.probe.isMeasured(c)) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = Tabular)
                    }
                }
            }
        }
    }
}

private data class WatchRow(val section: String, val label: String, val detail: String, val onClick: () -> Unit)

/** The replay list: full replay (catch-up / VOD), official highlights, YouTube, ESPN clips. */
@Composable
fun ReplayPane(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    var options by remember { mutableStateOf<List<Repository.ReplayOption>?>(null) }
    var clips by remember { mutableStateOf<List<com.fitztv.sports.Clip>>(emptyList()) }
    var official by remember { mutableStateOf<List<com.fitztv.sports.Highlights.YtVideo>>(emptyList()) }
    LaunchedEffect(game.id) { options = withTimeoutOrNull(20_000) { app.repo.replayOptions(game) } ?: emptyList() }
    LaunchedEffect(game.id) { clips = com.fitztv.sports.Highlights.espnClips(game) }
    LaunchedEffect(game.id, settings.youtubeApiKey) { official = com.fitztv.sports.Highlights.officialVideos(game, settings.youtubeApiKey) }
    val rows = remember(options, clips, official, settings.youtubeApiKey) {
        buildList {
            options.orEmpty().forEach { opt -> add(WatchRow("Full replay", opt.label, opt.detail) { onDismiss(); PlaybackQueue.launch(listOf(opt.channel), 0, null) }) }
            official.forEach { v ->
                add(WatchRow("Official highlights", v.title, "${v.channel} official" + if (v.durationSec > 0) " · %d:%02d".format(v.durationSec / 60, v.durationSec % 60) else "") {
                    scope.launch { com.fitztv.sports.Highlights.openVideo(app, v.id) }
                })
            }
            com.fitztv.sports.Highlights.kindsFor(game).forEach { k ->
                add(WatchRow("Highlights on YouTube", k.label, if (settings.youtubeApiKey.isBlank()) "Opens YouTube search for this game" else "Opens the video in your browser") {
                    scope.launch { com.fitztv.sports.Highlights.open(app, game, k, settings.youtubeApiKey) }
                })
            }
            clips.forEach { c ->
                add(WatchRow("ESPN clips", c.title, if (c.durationSec > 0) "%d:%02d".format(c.durationSec / 60, c.durationSec % 60) else "Clip") {
                    onDismiss()
                    PlaybackQueue.launch(listOf(com.fitztv.data.VodItem("clip:" + c.url.hashCode(), c.title, "Highlights", game.sport, c.url, c.thumb).asChannel()), 0, null)
                })
            }
        }
    }
    Column {
        if (options == null) Text("Looking for the full replay…", color = Neon.TextDim, fontSize = 13.sp)
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var last = ""
            rows.forEachIndexed { i, r ->
                if (r.section != last) {
                    last = r.section
                    item(key = "h:${r.section}") { Text(r.section, color = Neon.TextDim, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
                item(key = "r:$i:${r.label}") {
                    NeonSurface(onClick = r.onClick, modifier = Modifier.fillMaxWidth(), radius = 10.dp) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Glyph.Play, null, tint = Neon.TextDim, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.detail, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Live box score ---------------------------------------------------------------------------

/** Live box score that refreshes itself (every 15s while the game is live). */
@Composable
fun LiveStatsPanel(game: Game, modifier: Modifier = Modifier, onClip: ((com.fitztv.sports.Clip) -> Unit)? = null) {
    var stats by remember(game.id) { mutableStateOf<LiveStats?>(null) }
    var loading by remember(game.id) { mutableStateOf(true) }
    var failure by remember(game.id) { mutableStateOf<String?>(null) }
    var page by remember(game.id) { mutableStateOf(0) }
    LaunchedEffect(game.id) {
        while (true) {
            val got = withContext(Dispatchers.IO) { StatsClient.fetch(game) }
            got?.let { stats = it }
            failure = if (got == null) StatsClient.lastError else null
            loading = false
            delay(if (got == null) 6_000 else if (stats?.live != false) 15_000 else 60_000)
        }
    }
    val s = stats
    Column(modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s == null) {
            Text(if (loading) "Loading…" else "No live stats for this game", color = Neon.TextDim, fontSize = 14.sp)
            if (!loading && failure != null) Text(failure!!, color = Neon.TextFaint, fontSize = 12.sp)
            return@Column
        }
        val pages = if (s.categories.isNotEmpty()) listOf("Box score", "Highlights") else buildList {
            add("Summary"); if (s.teamBoxes.any { it.players.isNotEmpty() }) add("Players"); if (s.lineups.isNotEmpty()) add("Line-ups")
        }
        val current = pages[page.coerceIn(0, pages.lastIndex)]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pages.forEachIndexed { i, p -> NeonButton(p, onClick = { page = i }, selected = p == current) } }
            if (s.live) { LiveDot(); Spacer(Modifier.width(8.dp)) }
            Text(s.status, color = if (s.live) Neon.Live else Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = Clock)
        }
        when (current) {
            "Box score" -> {
                Row {
                    Text("${s.awayName} ${s.awayScore}", color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = Tabular, modifier = Modifier.weight(1f))
                    Text("${s.homeScore} ${s.homeName}", color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                }
                s.categories.forEach { BoxCategoryTable(it, maxRows = if (it.title == "Players") 16 else 8) }
            }
            "Highlights" -> if (s.clips.isEmpty()) Text("No clips yet", color = Neon.TextDim, fontSize = 13.sp) else s.clips.take(12).forEach { c ->
                NeonSurface(onClick = { onClip?.invoke(c) ?: PlaybackQueue.launch(listOf(com.fitztv.data.VodItem("clip:" + c.url.hashCode(), c.title, "Highlights", game.sport, c.url, c.thumb).asChannel()), 0, null) }, modifier = Modifier.fillMaxWidth(), radius = 8.dp) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Glyph.Play, null, tint = Neon.TextDim, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                        Text(c.title, fontSize = 13.sp, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (c.durationSec > 0) Text("%d:%02d".format(c.durationSec / 60, c.durationSec % 60), fontSize = 12.sp, color = Neon.TextDim, style = Tabular)
                    }
                }
            }
            "Players" -> s.teamBoxes.forEach { box -> PlayersTable(box) }
            "Line-ups" -> s.lineups.forEach { l ->
                Text("${l.team}${l.formation?.let { " · $it" } ?: ""}", color = Neon.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                l.lines().forEach { line -> Text(line.joinToString("   ") { "${it.jersey} ${it.name}" }, color = Neon.TextDim, fontSize = 12.sp) }
                if (l.subs.isNotEmpty()) Text("Subs: " + l.subs.joinToString(", ") { it.name }, color = Neon.TextFaint, fontSize = 12.sp)
            }
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.awayName, color = Neon.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${s.awayScore}  –  ${s.homeScore}", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
                    Text(s.homeName, color = Neon.Text, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                if (s.periods.isNotEmpty()) {
                    StatGrid(listOf("") + s.periods, header = true)
                    StatGrid(listOf(s.awayName.take(4)) + s.awayPeriods)
                    StatGrid(listOf(s.homeName.take(4)) + s.homePeriods)
                }
                s.rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(r.away, fontSize = 13.sp, color = Neon.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
                        Text(r.label, fontSize = 12.sp, color = Neon.TextDim, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(r.home, fontSize = 13.sp, color = Neon.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                    }
                }
                if (s.leaders.isNotEmpty()) {
                    Text("Leaders", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    s.leaders.forEach { Text(it, fontSize = 12.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (s.plays.isNotEmpty()) {
                    Text("Latest plays", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    s.plays.take(8).forEach { Text(it, fontSize = 12.sp, color = Neon.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun StatGrid(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEachIndexed { i, c -> Text(c, fontSize = 12.sp, color = if (header) Neon.TextFaint else Neon.Text, style = Tabular, textAlign = if (i == 0) TextAlign.Start else TextAlign.Center, modifier = Modifier.weight(if (i == 0) 1.4f else 1f)) }
    }
}

@Composable
private fun PlayersTable(box: com.fitztv.sports.TeamBox, maxRows: Int = 13) {
    Text(box.team, color = Neon.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val cols = listOf("MIN", "PTS", "REB", "AST", "STL", "BLK", "+/-")
    Row { Spacer(Modifier.weight(1f)); cols.forEach { Text(it, fontSize = 11.sp, color = Neon.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.width(46.dp)) } }
    box.players.take(maxRows).forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, fontSize = 12.sp, color = if (p.starter) Neon.Text else Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            listOf(p.min, p.pts.toString(), p.reb.toString(), p.ast.toString(), p.stl.toString(), p.blk.toString(), p.plusMinus).forEach {
                Text(it, fontSize = 12.sp, color = Neon.Text, textAlign = TextAlign.Center, style = Tabular, modifier = Modifier.width(46.dp))
            }
        }
    }
}

/** Both teams' players for one stat category (passing, rushing, points…), columns scroll sideways. */
@Composable
private fun BoxCategoryTable(cat: com.fitztv.sports.BoxCategory, maxRows: Int) {
    Text(cat.title.uppercase(), fontFamily = SairaCondensed, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
    Row {
        Column(Modifier.width(190.dp)) {
            Text("", fontSize = 11.sp)
            cat.rows.take(maxRows).forEach { r -> Text(r.name + (r.position?.let { " · $it" } ?: ""), fontSize = 12.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row { cat.columns.forEach { Text(it, fontSize = 11.sp, color = Neon.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.width(54.dp)) } }
            cat.rows.take(maxRows).forEach { r -> Row(Modifier.padding(vertical = 2.dp)) { r.values.forEach { Text(it, fontSize = 12.sp, color = Neon.Text, style = Tabular, textAlign = TextAlign.Center, modifier = Modifier.width(54.dp)) } } }
        }
    }
}

// ---- Team, league, favourites -----------------------------------------------------------------

/** A favourite team: next 7 days and recent results (live games open straight in the player). */
@Composable
fun TeamDialog(app: FitzApp, team: String, onPlay: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val sched = remember(games, team) { app.repo.teamSchedule(team) }
    val zone = settings.zone()
    fun opponent(g: Game): String {
        val home = com.fitztv.data.isTeam(team, g.home, g.sport)
        val opp = if (home) g.away else g.home
        return if (g.home.isBlank()) g.title else if (home) "vs $opp" else "@ $opp"
    }
    Modal(onDismiss, width = 680.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            app.repo.logos.get(team)?.let { AsyncImage(it, null, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)) }
            Text(team, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, modifier = Modifier.weight(1f))
            NeonButton("Close", onClick = onDismiss, icon = Glyph.Remove)
        }
        Spacer(Modifier.height(12.dp))
        val rows = buildList {
            sched.live?.let { add("live" to it) }
            sched.upcoming.forEach { add("next" to it) }
            sched.results.forEach { add("result" to it) }
        }
        if (rows.isEmpty()) Text("No games in the guide for the next 7 days or the past week.", color = Neon.TextDim)
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var lastKind = ""
            rows.forEach { (kind, g) ->
                if (kind != lastKind) {
                    val title = when (kind) { "live" -> "Live now"; "next" -> "Next 7 days"; else -> "Recent results" }
                    item(key = "h:$kind") { Text(title, color = Neon.TextDim, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                    lastKind = kind
                }
                item(key = "$kind:${g.id}") {
                    NeonSurface(onClick = { when (kind) { "live" -> onPlay(g); "next" -> onPick(g); else -> onReplay(g) } }, modifier = Modifier.fillMaxWidth(), radius = 10.dp) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (kind == "live") g.statusText else Fmt.whenLabel(g, zone, settings.use24h), color = if (kind == "live") Neon.Live else Neon.TextDim, fontSize = 13.sp, modifier = Modifier.width(180.dp), maxLines = 1, style = Tabular)
                            Text(opponent(g), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.gameState != GameState.PRE && g.home.isNotBlank()) {
                                val us = if (com.fitztv.data.isTeam(team, g.home, g.sport)) g.homeScore else g.awayScore
                                val them = if (com.fitztv.data.isTeam(team, g.home, g.sport)) g.awayScore else g.homeScore
                                Text("${us ?: ""}–${them ?: ""}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
                                Spacer(Modifier.width(12.dp))
                            }
                            Pill(when (kind) { "live" -> "Watch"; "next" -> g.league; else -> "Replay" }, if (kind == "live") Neon.Live else Neon.TextDim)
                        }
                    }
                }
            }
        }
    }
}

/** League hub: upcoming fixtures, recent results and the ladder for one league. */
@Composable
fun LeagueHub(league: String, settings: com.fitztv.data.AppSettings, onDismiss: () -> Unit, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit) {
    val repo = FitzApp.instance.repo
    val games by repo.games.collectAsState()
    val lg = Leagues.byName(league)
    var tab by remember { mutableStateOf(0) }
    val now = System.currentTimeMillis()
    val upcoming = remember(games) { games.filter { it.league == league && it.gameState != GameState.FINAL && it.startUtcMillis < now + 8 * 86_400_000L }.sortedBy { it.startUtcMillis } }
    val results = remember(games) { games.filter { it.league == league && it.gameState == GameState.FINAL }.sortedByDescending { it.startUtcMillis } }
    val tables by produceState<List<StandingsClient.Table>?>(null, league) {
        value = lg?.let { withContext(Dispatchers.IO) { runCatching { StandingsClient.fetch(it) }.getOrDefault(emptyList()) } } ?: emptyList()
    }
    val zone = settings.zone()
    Modal(onDismiss, width = 1100.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            (upcoming + results).firstNotNullOfOrNull { it.leagueLogo }?.let { AsyncImage(it, null, Modifier.size(44.dp), contentScale = ContentScale.Fit) }
            Text(league, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
            Spacer(Modifier.weight(1f))
            listOf("Fixtures", "Results", "Ladder").forEachIndexed { i, t -> NeonButton(t, onClick = { tab = i }, selected = tab == i) }
            NeonButton("Close", onClick = onDismiss, icon = Glyph.Remove)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.heightIn(max = 600.dp)) {
            when (tab) {
                0, 1 -> {
                    val list = if (tab == 0) upcoming else results
                    if (list.isEmpty()) EmptyHint(if (tab == 0) "No fixtures this week" else "No recent results", "The guide covers the next 8 days and the last week.")
                    LazyVerticalGrid(GridCells.Adaptive(300.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        gridItems(list, key = { it.id }) { g ->
                            PosterCard(g, zone, settings.use24h, onClick = { if (g.gameState == GameState.FINAL) onReplay(g) else onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g), width = 300.dp)
                        }
                    }
                }
                else -> LadderView(tables)
            }
        }
    }
}

@Composable
private fun LadderView(tables: List<StandingsClient.Table>?) {
    when {
        tables == null -> Text("Loading ladder…", color = Neon.TextDim)
        tables.isEmpty() -> EmptyHint("No ladder", "This league has no standings in the feed.")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tables.forEach { t ->
                item(key = "h:" + t.name) {
                    Row(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                        Text(t.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, modifier = Modifier.weight(1f))
                        t.headers.forEach { Text(it, fontSize = 12.sp, color = Neon.TextFaint, textAlign = TextAlign.Center, modifier = Modifier.width(54.dp)) }
                    }
                }
                itemsIndexed(t.rows, key = { _, r -> t.name + r.team }) { i, r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontSize = 14.sp, color = Neon.TextFaint, modifier = Modifier.width(32.dp), style = Tabular)
                        if (r.logo != null) AsyncImage(r.logo, null, Modifier.size(26.dp)) else Spacer(Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(r.team, fontSize = 15.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        r.values.forEach { Text(it, fontSize = 14.sp, color = Neon.Text, textAlign = TextAlign.Center, modifier = Modifier.width(54.dp), style = Tabular) }
                    }
                }
            }
        }
    }
}

@Composable
fun FavouritesDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val directory by app.repo.teams.teams.collectAsState()
    val games by app.repo.games.collectAsState()
    LaunchedEffect(Unit) { app.repo.teams.load() }
    var league by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val favs = settings.favoriteTeams
    val all = remember(directory, games) {
        val fromGames = games.flatMap { listOf(Team(it.home, it.league, it.sport, it.homeLogo), Team(it.away, it.league, it.sport, it.awayLogo)) }.filter { it.name.isNotBlank() }
        (directory + fromGames).distinctBy { it.name + "|" + it.league }
    }
    val leagues = remember(all) { Leagues.all.map { it.name }.filter { n -> all.any { it.league == n } } }
    val list = remember(all, favs, league, query) {
        val q = T.norm(query).trim()
        val mine = favs.map { f -> all.firstOrNull { sameTeam(it.name, f) } ?: Team(f, "", com.fitztv.sports.Sport.GENERAL, app.repo.logos.get(f)) }
        val rest = all.filter { t -> favs.none { sameTeam(it, t.name) } }.filter { league == null || it.league == league }.filter { q.isEmpty() || T.norm(it.name).contains(q) }
            .distinctBy { it.name }.sortedWith(compareBy<Team> { it.league }.thenBy { it.name })
        (if (league == null && q.isEmpty()) mine else mine.filter { league == null || it.league == league }) + rest
    }
    Modal(onDone, width = 720.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My teams", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                Text("Pick a league or search, then click a team to add or remove it.", color = Neon.TextDim, fontSize = 13.sp)
            }
            NeonButton("Done", onClick = onDone, selected = true)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search teams") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("All leagues", onClick = { league = null }, selected = league == null)
            leagues.forEach { l -> NeonButton(l, onClick = { league = l }, selected = league == l) }
        }
        Spacer(Modifier.height(8.dp))
        if (all.isEmpty()) Text("Loading teams…", color = Neon.TextDim)
        LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.name + "|" + it.league }) { t ->
                val on = favs.any { sameTeam(it, t.name) }
                NeonSurface(
                    onClick = { app.settings.update { st -> st.copy(favoriteTeams = if (on) st.favoriteTeams.filterNot { sameTeam(it, t.name) }.toSet() else st.favoriteTeams + t.name) } },
                    modifier = Modifier.fillMaxWidth(), radius = 10.dp, selected = on,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(if (on) Glyph.Star else Glyph.StarOff, if (on) "In My teams" else "Not in My teams", tint = if (on) Neon.Purple else Neon.TextFaint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        val logo = t.logo ?: app.repo.logos.get(t.name)
                        if (logo != null) AsyncImage(logo, null, Modifier.size(30.dp)) else Spacer(Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(t.name, fontSize = 15.sp, color = Neon.Text, modifier = Modifier.weight(1f))
                        if (t.league.isNotBlank()) Pill(t.league, Neon.Purple)
                    }
                }
            }
        }
    }
}

/** Priority categories: provider categories whose streams rank first. */
@Composable
fun PriorityGroupsDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val channels by app.repo.channels.collectAsState()
    val options = remember(channels, settings.priorityGroups) { com.fitztv.data.PriorityGroups.options(channels, settings.priorityGroups) }
    Modal(onDone, width = 680.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Priority categories", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                Text("Streams from these provider categories rank first (after the best match and resolution rules).", color = Neon.TextDim, fontSize = 13.sp)
            }
            NeonButton("Done", onClick = onDone, selected = true)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { it }) { g ->
                val on = com.fitztv.data.PriorityGroups.isOn(g, settings.priorityGroups)
                NeonSurface(onClick = { app.settings.update { it.copy(priorityGroups = com.fitztv.data.PriorityGroups.toggle(g, it.priorityGroups)) } }, modifier = Modifier.fillMaxWidth(), radius = 8.dp, selected = on) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(if (on) Glyph.RadioOn else Glyph.RadioOff, null, tint = if (on) Neon.Purple else Neon.TextFaint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(g, fontSize = 14.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---- Updates, alerts --------------------------------------------------------------------------

@Composable
fun UpdatePrompt(app: FitzApp) {
    val state by app.updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    val release = when (val s = state) {
        is com.fitztv.update.UpdateState.Available -> s.release
        is com.fitztv.update.UpdateState.Downloading -> s.release
        else -> return
    }
    Modal({ app.updates.dismiss() }, width = 520.dp) {
        Text("Update available", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Text("Build ${release.build} (you have build ${app.updates.currentBuild})", color = Neon.Cyan, fontSize = 14.sp)
        if (release.notes.isNotBlank()) Text(release.notes, color = Neon.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        (state as? com.fitztv.update.UpdateState.Downloading)?.let { Text("Downloading… ${it.percent}%", color = Neon.Purple, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton(if (state is com.fitztv.update.UpdateState.Downloading) "Downloading…" else "Update now", selected = true, onClick = { if (state !is com.fitztv.update.UpdateState.Downloading) scope.launch { app.updates.downloadAndInstall(release) } })
            NeonButton("Later", onClick = { app.updates.dismiss() })
        }
    }
}

fun updateStatusText(app: FitzApp, state: com.fitztv.update.UpdateState): String = when (state) {
    com.fitztv.update.UpdateState.Idle -> "Installed: build ${app.updates.currentBuild}"
    com.fitztv.update.UpdateState.Checking -> "Checking GitHub…"
    is com.fitztv.update.UpdateState.UpToDate -> "Up to date (build ${state.build})"
    is com.fitztv.update.UpdateState.Available -> "Build ${state.release.build} is available"
    is com.fitztv.update.UpdateState.Downloading -> "Downloading build ${state.release.build}… ${state.percent}%"
    is com.fitztv.update.UpdateState.Error -> state.message
}

/** Reminder / score alerts; click to watch. Auto-switches if enabled. */
@Composable
fun BoxScope.AlertBanner(app: FitzApp) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<AppAlert?>(null) }
    LaunchedEffect(Unit) {
        app.alerts.events.collect { a ->
            if (a.kind == AppAlert.Kind.TEAM_STARTING && app.settings.value.autoSwitchToTeam) { Launcher.play(a.game); return@collect }
            current = a
            delay(15_000)
            if (current == a) current = null
        }
    }
    val a = current ?: return
    NeonSurface(
        onClick = { current = null; scope.launch { Launcher.play(a.game) } },
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp), radius = 12.dp, selected = true,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Spacer(Modifier.width(16.dp))
            Text("Click to watch", fontSize = 13.sp, color = Neon.TextDim)
            Spacer(Modifier.width(12.dp))
            Text("✕", fontSize = 15.sp, color = Neon.TextDim, modifier = Modifier.padding(4.dp).let { it })
        }
    }
}
