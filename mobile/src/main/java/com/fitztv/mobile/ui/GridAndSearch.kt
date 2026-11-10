package com.fitztv.mobile.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Catchup
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.SearchResults
import com.fitztv.data.gridRows
import com.fitztv.data.search
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val WINDOW_MIN = 150
private const val DP_PER_MIN = 3

/** Grid guide from the provider's EPG (scroll sideways; tap on-now to watch, past to catch up, long-press upcoming to record). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridGuideMobile(sport: Sport?, settings: AppSettings) {
    val app = FitzApp.instance
    val context = LocalContext.current
    val epg by app.repo.epg.collectAsState()
    val channels by app.repo.channels.collectAsState()
    val now = System.currentTimeMillis()
    val slot = 30 * 60_000L
    var start by rememberSaveable { mutableLongStateOf(now - now % slot) }
    val end = start + WINDOW_MIN * 60_000L
    val rows = remember(epg, channels, sport, settings.hiddenSports) { app.repo.gridRows(sport, settings) }
    val zone = settings.zone()
    val h = rememberScrollState()
    val nameW = 110

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            chip("Earlier", false) { start -= slot * 2 }
            chip("Now", now in start until end) { start = now - now % slot }
            chip("Later", false) { start += slot * 2 }
            Text(Fmt.dayLabel(Fmt.localDate(start, zone), zone), color = Neon.TextFaint, fontSize = 12.sp)
        }
        if (rows.isEmpty()) {
            EmptyHint("No programme listings", "Your sources haven't provided an EPG for these channels yet.", Modifier.padding(16.dp))
        } else Box(Modifier.fillMaxSize().horizontalScroll(h)) {
            Column(Modifier.width((nameW + 8 + WINDOW_MIN * DP_PER_MIN + 16).dp)) {
                Row(Modifier.padding(start = (16 + nameW + 8).dp)) {
                    (0 until WINDOW_MIN / 30).forEach { i ->
                        Text(Fmt.time(start + i * slot, zone, settings.use24h), color = Neon.Cyan, fontSize = 11.sp, modifier = Modifier.width((30 * DP_PER_MIN).dp))
                    }
                }
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(rows, key = { it.channel.id }) { row ->
                        Row(Modifier.height(50.dp)) {
                            NeonCard(onClick = { PlaybackQueue.launch(context, row.variants, 0) }, modifier = Modifier.width(nameW.dp).fillMaxHeight()) {
                                Row(Modifier.padding(8.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                                    if (row.channel.logo != null) { AsyncImage(row.channel.logo, null, Modifier.size(24.dp)); Spacer(Modifier.width(4.dp)) }
                                    Text(row.channel.label, color = Neon.Text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width((WINDOW_MIN * DP_PER_MIN).dp).fillMaxHeight()) {
                                if (now in start until end) Box(Modifier.offset(x = (((now - start) / 60_000L) * DP_PER_MIN).toInt().dp).width(2.dp).fillMaxHeight().background(Neon.Magenta))
                                row.programs.filter { it.stopMillis > start && it.startMillis < end }.forEach { p ->
                                    val s0 = maxOf(p.startMillis, start); val e0 = minOf(p.stopMillis, end)
                                    val x = ((s0 - start) / 60_000L * DP_PER_MIN).toInt()
                                    val w = (((e0 - s0) / 60_000L) * DP_PER_MIN).toInt().coerceAtLeast(10)
                                    val live = now in p.startMillis until p.stopMillis
                                    val past = p.stopMillis <= now
                                    Box(
                                        Modifier.offset(x = x.dp).width((w - 3).coerceAtLeast(6).dp).fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (live) Neon.SurfaceHi else Neon.Surface)
                                            .combinedClickable(
                                                onClick = {
                                                    when {
                                                        live -> PlaybackQueue.launch(context, row.variants, 0)
                                                        past -> Catchup.url(row.channel, p.startMillis, ((p.stopMillis - p.startMillis) / 60_000L).toInt() + 10)?.let { url ->
                                                            PlaybackQueue.launch(context, listOf(row.channel.copy(id = "cu:${row.channel.id}:${p.startMillis}", streamUrl = url, vod = true, name = p.title)), 0)
                                                        } ?: Toast.makeText(context, "No catch-up on this channel", Toast.LENGTH_SHORT).show()
                                                        else -> Toast.makeText(context, "Starts ${Fmt.time(p.startMillis, zone, settings.use24h)} — long-press to record", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!past) {
                                                        com.fitztv.record.Recorder.schedule(context, p.title, row.channel.sport.name, row.variants, p.startMillis, p.stopMillis)
                                                        Toast.makeText(context, "Recording ${p.title}", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Column {
                                            Text(p.title, color = if (past) Neon.TextFaint else Neon.Text, fontSize = 11.sp, fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(Fmt.time(p.startMillis, zone, settings.use24h), color = Neon.TextDim, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = FilterChip(
    selected = selected, onClick = onClick, label = { Text(text) },
    colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.SurfaceHi, selectedLabelColor = Neon.Text),
)

/** Search teams, games, channels and replays. */
@Composable
fun SearchTab(app: FitzApp, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onTeam: (String) -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    LaunchedEffect(query) { delay(250); results = withContext(Dispatchers.Default) { app.repo.search(query, settings) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search teams, games, channels, replays") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple, unfocusedBorderColor = Neon.Line, focusedLabelColor = Neon.Purple, cursorColor = Neon.Purple),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.length >= 2 && results.isEmpty) item { Text("No matches for “$query”", color = Neon.TextDim) }
        if (results.teams.isNotEmpty()) {
            item { SectionHeader("Teams") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.teams, key = { it.name + it.league }) { t ->
                        val fav = settings.favoriteTeams.any { com.fitztv.data.sameTeam(it, t.name) }
                        NeonCard(onClick = { onTeam(t.name) }, onLongClick = {
                            app.settings.update { s -> s.copy(favoriteTeams = if (fav) s.favoriteTeams.filterNot { com.fitztv.data.sameTeam(it, t.name) }.toSet() else s.favoriteTeams + t.name) }
                        }, highlighted = fav, modifier = Modifier.width(220.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Logo(t.logo ?: app.repo.logos.get(t.name), t.name, 32); Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(t.name, color = if (fav) Neon.Purple else Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.league + " · long-press to " + if (fav) "remove from My teams" else "add to My teams", color = Neon.TextDim, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (results.games.isNotEmpty()) {
            item { SectionHeader("Games") }
            items(results.games, key = { "g:" + it.id }) { g -> GameCard(g, settings, onClick = { if (g.gameState == GameState.FINAL) onReplay(g) else onGame(g) }, onLongClick = { onPick(g) }) }
        }
        if (results.channels.isNotEmpty()) {
            item { SectionHeader("Channels") }
            items(results.channels, key = { "c:" + it.id }) { c -> ChannelCard(c, app.repo.nowPlaying(c), onClick = { PlaybackQueue.launch(context, listOf(c) + app.repo.variantsOf(c), 0) }) }
        }
        if (results.replays.isNotEmpty()) {
            item { SectionHeader("Replays") }
            items(results.replays, key = { "v:" + it.id }) { v ->
                NeonCard(onClick = { PlaybackQueue.launch(context, listOf(v.asChannel()), 0, null) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(v.label, color = Neon.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(v.category, color = Neon.TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
