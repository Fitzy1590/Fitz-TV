package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.Repository
import com.fitztv.data.sameTeam
import com.fitztv.player.PlaybackQueue
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.Neon

@Composable
private fun NeonDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(680.dp)
                .background(Neon.Bg2, RoundedCornerShape(20.dp))
                .border(2.dp, Neon.Purple, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) { content() }
    }
}

/** Ways to watch a finished game: catch-up of the full game, or replays from the VOD library. */
@Composable
fun ReplayDialog(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var options by remember { mutableStateOf<List<Repository.ReplayOption>?>(null) }
    LaunchedEffect(game.id) { options = app.repo.replayOptions(game) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(options) { if (!options.isNullOrEmpty()) runCatching { focus.requestFocus() } }
    NeonDialog(onDismiss) {
        Text("Replay: ${if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title}", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
        Text(
            "${game.league} • ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}" +
                if (game.home.isNotBlank()) " • Final ${game.awayScore ?: ""}-${game.homeScore ?: ""}" else "",
            color = Neon.Cyan, fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        val o = options
        when {
            o == null -> Text("Looking for replays…", color = Neon.TextDim)
            o.isEmpty() -> Text(
                "No replay found. Full-game replays need a channel with catch-up (TV archive) from your provider, " +
                    "or a matching video in its on-demand library.",
                color = Neon.TextDim, fontSize = 14.sp,
            )
            else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(o.withIndex().toList(), key = { it.value.channel.id }) { (i, opt) ->
                    NeonSurface(
                        onClick = { onDismiss(); PlaybackQueue.launch(context, listOf(opt.channel), 0, null) },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier), radius = 10.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶", color = Neon.Magenta, fontSize = 18.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(opt.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(opt.detail, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A favourite team: next 7 days and recent results (live games open straight in the player instead). */
@Composable
fun TeamDialog(app: FitzApp, team: String, onPlay: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val sched = remember(games, team) { app.repo.teamSchedule(team) }
    val zone = settings.zone()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun opponent(g: Game): String {
        val home = sameTeam(g.home, team)
        val opp = if (home) g.away else g.home
        return if (g.home.isBlank()) g.title else if (home) "vs $opp" else "@ $opp"
    }
    NeonDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            app.repo.logos.get(team)?.let { AsyncImage(it, null, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)) }
            Text(team, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
        }
        Spacer(Modifier.height(12.dp))
        val rows = buildList {
            sched.live?.let { add("live" to it) }
            sched.upcoming.forEach { add("next" to it) }
            sched.results.forEach { add("result" to it) }
        }
        if (rows.isEmpty()) Text("No games in the guide for the next 7 days or the past week.", color = Neon.TextDim)
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var lastKind = ""
            rows.forEachIndexed { i, (kind, g) ->
                if (kind != lastKind) {
                    val title = when (kind) { "live" -> "🔴 Live now"; "next" -> "Next 7 days"; else -> "Recent results" }
                    item(key = "h:$kind") { Text(title, color = Neon.Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
                    lastKind = kind
                }
                item(key = "$kind:${g.id}") {
                    NeonSurface(
                        onClick = { when (kind) { "live" -> onPlay(g); "next" -> onPick(g); else -> onReplay(g) } },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier), radius = 10.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (kind) { "live" -> g.statusText; else -> Fmt.whenLabel(g, zone, settings.use24h) },
                                color = if (kind == "live") Neon.Live else Neon.Cyan, fontSize = 13.sp, modifier = Modifier.width(190.dp), maxLines = 1,
                            )
                            Text(opponent(g), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.gameState != GameState.PRE && g.home.isNotBlank()) {
                                val us = if (sameTeam(g.home, team)) g.homeScore else g.awayScore
                                val them = if (sameTeam(g.home, team)) g.awayScore else g.homeScore
                                Text("${us ?: ""}-${them ?: ""}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Neon.Text)
                                Spacer(Modifier.width(10.dp))
                            }
                            Pill(when (kind) { "live" -> "WATCH"; "next" -> g.league; else -> "REPLAY" }, if (kind == "next") Neon.Purple else Neon.Magenta)
                        }
                    }
                }
            }
        }
    }
}
