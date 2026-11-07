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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun NeonDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(680.dp)
                .background(Neon.Bg2, RoundedCornerShape(16.dp))
                .border(1.dp, Neon.Line, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) { content() }
    }
}

private data class WatchRow(val section: String, val icon: String, val label: String, val detail: String, val onClick: () -> Unit)

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
                add(WatchRow("Full replay", "▶", opt.label, opt.detail) { onDismiss(); PlaybackQueue.launch(context, listOf(opt.channel), 0, null) })
            }
            com.fitztv.sports.Highlights.kindsFor(game).forEach { k ->
                add(WatchRow("Highlights on YouTube", "▶", k.label,
                    if (settings.youtubeApiKey.isBlank()) "Opens YouTube search for this game" else "Opens the video in YouTube") {
                    scope.launch { com.fitztv.sports.Highlights.open(context, game, k, settings.youtubeApiKey) }
                })
            }
            clips.forEach { c ->
                add(WatchRow("ESPN clips", "▶", c.title, if (c.durationSec > 0) "%d:%02d".format(c.durationSec / 60, c.durationSec % 60) else "Clip") {
                    onDismiss()
                    PlaybackQueue.launch(context, listOf(com.fitztv.data.VodItem("clip:" + c.url.hashCode(), c.title, "Highlights", game.sport, c.url, c.thumb).asChannel()), 0, null)
                })
            }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { focus.requestFocus() } }
    NeonDialog(onDismiss) {
        Text(if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Text(
            "${game.league} · ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}" +
                if (game.home.isNotBlank()) " · Final ${game.awayScore ?: ""}–${game.homeScore ?: ""}" else "",
            color = Neon.TextDim, fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (options == null) Text("Looking for the full replay…", color = Neon.TextDim, fontSize = 13.sp)
        else if (options!!.isEmpty()) Text(
            "No full replay found (needs a channel with catch-up, or a match in your provider's on-demand library).",
            color = Neon.TextFaint, fontSize = 12.sp,
        )
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var last = ""
            rows.forEachIndexed { i, r ->
                if (r.section != last) {
                    last = r.section
                    item(key = "h:${r.section}") { Text(r.section, color = Neon.TextDim, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
                item(key = "r:$i:${r.label}") {
                    NeonSurface(
                        onClick = r.onClick,
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier), radius = 10.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.tv.material3.Icon(com.fitztv.ui.components.Glyph.Play, null, tint = Neon.TextDim, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.detail, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1)
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
        val home = com.fitztv.data.isTeam(team, g.home, g.sport)
        val opp = if (home) g.away else g.home
        return if (g.home.isBlank()) g.title else if (home) "vs $opp" else "@ $opp"
    }
    NeonDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            app.repo.logos.get(team)?.let { AsyncImage(it, null, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)) }
            Text(team, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
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
            rows.forEachIndexed { i, (kind, g) ->
                if (kind != lastKind) {
                    val title = when (kind) { "live" -> "Live now"; "next" -> "Next 7 days"; else -> "Recent results" }
                    item(key = "h:$kind") { Text(title, color = Neon.TextDim, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                    lastKind = kind
                }
                item(key = "$kind:${g.id}") {
                    NeonSurface(
                        onClick = { when (kind) { "live" -> onPlay(g); "next" -> onPick(g); else -> onReplay(g) } },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier), radius = 10.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (kind) { "live" -> g.statusText; else -> Fmt.whenLabel(g, zone, settings.use24h) },
                                color = if (kind == "live") Neon.Live else Neon.TextDim, fontSize = 13.sp, modifier = Modifier.width(192.dp), maxLines = 1, style = com.fitztv.ui.components.Tabular,
                            )
                            Text(opponent(g), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.gameState != GameState.PRE && g.home.isNotBlank()) {
                                val us = if (com.fitztv.data.isTeam(team, g.home, g.sport)) g.homeScore else g.awayScore
                                val them = if (com.fitztv.data.isTeam(team, g.home, g.sport)) g.awayScore else g.homeScore
                                Text("${us ?: ""}–${them ?: ""}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = com.fitztv.ui.components.Tabular)
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
