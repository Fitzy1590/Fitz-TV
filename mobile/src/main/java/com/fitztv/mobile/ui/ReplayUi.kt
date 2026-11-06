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

@Composable
fun ReplaysTab(app: FitzApp, sport: Sport?, onReplay: (Game) -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val vod by app.repo.vod.collectAsState()
    val recent = remember(games, sport, settings.favoriteTeams) { app.repo.replayGames(sport) }
    val library = remember(vod, sport) { vod.filter { sport == null || it.sport == sport || it.sport == Sport.GENERAL }.take(300) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
}

@Composable
fun ReplayDialog(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var options by remember { mutableStateOf<List<Repository.ReplayOption>?>(null) }
    LaunchedEffect(game.id) { options = app.repo.replayOptions(game) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Neon.Bg2, modifier = Modifier.widthIn(max = 600.dp),
        title = {
            Column {
                Text("Replay: ${if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title}", fontWeight = FontWeight.Bold)
                Text(
                    "${game.league} • ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}" +
                        if (game.home.isNotBlank()) " • Final ${game.awayScore ?: ""}-${game.homeScore ?: ""}" else "",
                    color = Neon.Cyan, fontSize = 13.sp,
                )
            }
        },
        text = {
            val o = options
            when {
                o == null -> Text("Looking for replays…", color = Neon.TextDim)
                o.isEmpty() -> Text(
                    "No replay found. Full-game replays need a channel with catch-up (TV archive) from your provider, or a matching video in its on-demand library.",
                    color = Neon.TextDim,
                )
                else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(o, key = { it.channel.id }) { opt ->
                        NeonCard(onClick = { onDismiss(); PlaybackQueue.launch(context, listOf(opt.channel), 0, null) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("▶", color = Neon.Magenta, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(opt.label, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(opt.detail, color = Neon.TextDim, fontSize = 12.sp, maxLines = 1)
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
                        val isHome = sameTeam(g.home, team)
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
