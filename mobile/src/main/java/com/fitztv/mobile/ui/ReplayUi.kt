package com.fitztv.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.Repository
import com.fitztv.data.sameTeam
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.launch

@Composable
fun ReplaysTab(app: FitzApp, sport: Sport?, onReplay: (Game) -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val vod by app.repo.vod.collectAsState()
    val recent = remember(games, sport, settings.favoriteTeams) { app.repo.replayGames(sport) }
    val library = remember(vod, sport) { vod.filter { sport == null || it.sport == sport || it.sport == Sport.GENERAL }.take(300) }
    val recordings by com.fitztv.record.Recorder.recordings.collectAsState()
    var confirm by remember { mutableStateOf<String?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (recordings.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("⏺  My recordings  (${recordings.size})") }
            items(recordings.sortedByDescending { it.startMillis }, key = { "rec:" + it.id }) { r ->
                NeonCard(
                    onClick = {
                        if (r.status == "recording" || r.status == "done") PlaybackQueue.launch(context, listOf(
                            com.fitztv.data.VodItem(r.id, r.title, "Recording", runCatching { Sport.valueOf(r.sport) }.getOrDefault(Sport.GENERAL), "file://" + r.file).asChannel()
                        ), 0, null)
                    },
                    onLongClick = { confirm = r.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.title, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.time(r.startMillis, com.fitztv.data.AppSettings.ZONE, false) + " • " + "%.1f GB".format(r.sizeBytes / 1e9), color = Neon.TextDim, fontSize = 12.sp)
                        Text(when (r.status) { "scheduled" -> "⏰ Scheduled"; "recording" -> "⏺ Recording now — tap to watch"; "done" -> "✓ Ready"; "cancelled" -> "Cancelled"; else -> "⚠ Failed" },
                            color = if (r.status == "recording") Neon.Live else Neon.Cyan, fontSize = 12.sp)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Recent games  (${recent.size})") }
        if (recent.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Results from the past week appear here once games finish.", color = Neon.TextDim, fontSize = 14.sp)
        }
        items(recent, key = { "g:" + it.id }) { g -> GameCard(g, settings, onClick = { onReplay(g) }, onLongClick = { onReplay(g) }) }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Replay library  (${library.size})") }
        if (library.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Sport categories in your provider's on-demand section show up here.", color = Neon.TextDim, fontSize = 14.sp)
        }
        items(library, key = { "v:" + it.id }) { v ->
            NeonCard(onClick = { PlaybackQueue.launch(context, listOf(v.asChannel()), 0, null) }, modifier = Modifier.fillMaxWidth().height(96.dp)) {
                Box(Modifier.fillMaxSize()) {
                    if (v.poster != null) AsyncImage(v.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.3f)
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Text(v.label, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${v.sport.emoji} ${v.category}", fontSize = 11.sp, color = Neon.TextDim, maxLines = 1)
                    }
                }
            }
        }
    }
    RecordingConfirm(confirm) { confirm = null }
}

@Composable
fun RecordingConfirm(id: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val r = com.fitztv.record.Recorder.recordings.collectAsState().value.firstOrNull { it.id == id } ?: return
    val active = r.status == "recording" || r.status == "scheduled"
    AlertDialog(
        onDismissRequest = onDone, containerColor = Neon.Bg2,
        title = { Text(r.title) },
        text = { Text(if (active) "Stop this recording? What's recorded so far is kept." else "Delete this recording from the phone?", color = Neon.TextDim) },
        confirmButton = { TextButton(onClick = {
            if (active) com.fitztv.record.Recorder.stop(context, r.id) else com.fitztv.record.Recorder.delete(context, r.id); onDone()
        }) { Text(if (active) "Stop" else "Delete", color = Neon.Live) } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

private data class WatchRow(val section: String, val label: String, val detail: String, val onClick: () -> Unit)

/** Watch a finished game: full replay (catch-up / VOD), YouTube highlights, or ESPN clips. */
@Composable
fun ReplayDialog(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    var options by remember { mutableStateOf<List<Repository.ReplayOption>?>(null) }
    var clips by remember { mutableStateOf<List<com.fitztv.sports.Clip>>(emptyList()) }
    LaunchedEffect(game.id) { options = app.repo.replayOptions(game) }
    LaunchedEffect(game.id) { clips = com.fitztv.sports.Highlights.espnClips(game) }
    val rows = remember(options, clips, settings.youtubeApiKey) {
        buildList {
            options.orEmpty().forEach { opt ->
                add(WatchRow("Full replay", opt.label, opt.detail) { onDismiss(); PlaybackQueue.launch(context, listOf(opt.channel), 0, null) })
            }
            com.fitztv.sports.Highlights.kindsFor(game).forEach { k ->
                add(WatchRow("Highlights on YouTube", k.label, if (settings.youtubeApiKey.isBlank()) "Opens YouTube search for this game" else "Opens the video in YouTube") {
                    scope.launch { com.fitztv.sports.Highlights.open(context, game, k, settings.youtubeApiKey) }
                })
            }
            clips.forEach { c ->
                add(WatchRow("ESPN clips", c.title, if (c.durationSec > 0) "%d:%02d".format(c.durationSec / 60, c.durationSec % 60) else "Clip") {
                    onDismiss()
                    PlaybackQueue.launch(context, listOf(com.fitztv.data.VodItem("clip:" + c.url.hashCode(), c.title, "Highlights", game.sport, c.url, c.thumb).asChannel()), 0, null)
                })
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Neon.Bg2, modifier = Modifier.widthIn(max = 600.dp),
        title = {
            Column {
                Text(if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title, fontWeight = FontWeight.Bold)
                Text(
                    "${game.league} • ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}" +
                        if (game.home.isNotBlank()) " • Final ${game.awayScore ?: ""}-${game.homeScore ?: ""}" else "",
                    color = Neon.Cyan, fontSize = 13.sp,
                )
            }
        },
        text = {
            Column {
                if (options == null) Text("Looking for the full replay…", color = Neon.TextDim, fontSize = 13.sp)
                else if (options!!.isEmpty()) Text("No full replay found (needs catch-up or a match in your provider's on-demand library).", color = Neon.TextFaint, fontSize = 12.sp)
                LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var last = ""
                    rows.forEachIndexed { i, r ->
                        if (r.section != last) {
                            last = r.section
                            item(key = "h:${r.section}") { Text(r.section, color = Neon.Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
                        }
                        item(key = "r:$i:${r.label}") {
                            NeonCard(onClick = r.onClick, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("▶", color = Neon.Magenta, fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(r.label, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(r.detail, color = Neon.TextDim, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Favourite team: next 7 days and recent results (live games open straight in the player). */
@Composable
fun TeamDialog(app: FitzApp, team: String, onPlay: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val sched = remember(games, team) { app.repo.teamSchedule(team) }
    val zone = settings.zone()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Neon.Bg2, modifier = Modifier.widthIn(max = 600.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(app.repo.logos.get(team), team, 40); Spacer(Modifier.width(10.dp)); Text(team, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            val rows = buildList {
                sched.live?.let { add("live" to it) }
                sched.upcoming.forEach { add("next" to it) }
                sched.results.forEach { add("result" to it) }
            }
            if (rows.isEmpty()) Text("No games in the guide for the next 7 days or the past week.", color = Neon.TextDim)
            else LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                var last = ""
                rows.forEach { (kind, g) ->
                    if (kind != last) {
                        last = kind
                        item(key = "h:$kind") {
                            Text(when (kind) { "live" -> "🔴 Live now"; "next" -> "Next 7 days"; else -> "Recent results" },
                                color = Neon.Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    item(key = "$kind:${g.id}") {
                        val isHome = com.fitztv.data.isTeam(team, g.home, g.sport)
                        val opp = if (g.home.isBlank()) g.title else if (isHome) "vs ${g.away}" else "@ ${g.home}"
                        NeonCard(onClick = { when (kind) { "live" -> onPlay(g); "next" -> onPick(g); else -> onReplay(g) } }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(opp, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (kind == "live") g.statusText else "${g.league} • ${Fmt.whenLabel(g, zone, settings.use24h)}",
                                        color = if (kind == "live") Neon.Live else Neon.Cyan, fontSize = 12.sp, maxLines = 1)
                                }
                                if (g.gameState != GameState.PRE && g.home.isNotBlank()) {
                                    val us = if (isHome) g.homeScore else g.awayScore
                                    val them = if (isHome) g.awayScore else g.homeScore
                                    Text("${us ?: ""}-${them ?: ""}", color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Pill(when (kind) { "live" -> "WATCH"; "next" -> "STREAMS"; else -> "REPLAY" }, Neon.Magenta)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
