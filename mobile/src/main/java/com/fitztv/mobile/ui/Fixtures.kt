package com.fitztv.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.sports.Leagues
import com.fitztv.sports.Sport
import com.fitztv.sports.StandingsClient
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val chipColors @Composable get() = FilterChipDefaults.filterChipColors(
    containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.SurfaceHi, selectedLabelColor = Neon.Text,
)

/** Fixtures (phone): day strip, games grouped by league with Join live / From start / Full replay. */
@Composable
fun FixturesTab(
    games: List<Game>, sport: Sport?, settings: AppSettings,
    onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onLeague: (String) -> Unit,
) {
    val zone = settings.zone()
    val today = LocalDate.now(zone)
    var offset by rememberSaveable { mutableLongStateOf(0L) }
    val day = today.plusDays(offset)
    val byLeague = remember(games, sport, day, zone, settings) {
        games.filter { g ->
            (sport == null || g.sport == sport) && (settings.showInGuide(g) || g.gameState == GameState.LIVE) &&
                (Fmt.localDate(g.startUtcMillis, zone) == day || (offset == 0L && g.gameState == GameState.LIVE))
        }.sortedWith(compareBy<Game> { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
            .groupBy { it.league }.toList()
            .sortedWith(compareByDescending<Pair<String, List<Game>>> { (_, l) -> l.any { settings.isFavourite(it) } }.thenBy { it.first })
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((-2L..6L).toList()) { off ->
                val d = today.plusDays(off)
                val label = if (off == 0L) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " " + d.dayOfMonth
                FilterChip(selected = off == offset, onClick = { offset = off }, label = { Text(label) }, colors = chipColors)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(340.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (byLeague.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyHint("Nothing scheduled", "No ${sport?.label ?: ""} games on ${Fmt.dayLabel(day, zone)}.") }
            byLeague.forEach { (league, list) ->
                item(key = "h:$league", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        list.firstNotNullOfOrNull { it.leagueLogo }?.let { AsyncImage(it, null, Modifier.size(24.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(10.dp)) }
                        Text(league, color = Neon.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (Leagues.byName(league)?.espnPath != null) Text("Ladder ›", color = Neon.Purple, fontSize = 14.sp, modifier = Modifier.clickable { onLeague(league) }.padding(8.dp))
                    }
                }
                items(list, key = { it.id }) { g -> FixtureCard(g, settings, onGame, onPick, onReplay) }
            }
        }
    }
}

@Composable
fun FixtureCard(g: Game, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit) {
    val app = FitzApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.league, color = Neon.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            when (g.gameState) {
                GameState.LIVE -> Text("LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(Neon.Live, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                GameState.PRE -> Text(Fmt.whenLabel(g, settings.zone(), settings.use24h), color = Neon.Text, fontSize = 13.sp, style = Tabular)
                GameState.FINAL -> Text(g.statusText.ifBlank { "Final" }, color = Neon.TextDim, fontSize = 13.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crest(g.awayLogo, g.awayShort.ifBlank { g.away })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(g.matchup.replace(" vs ", " v "), color = Neon.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (g.gameState != GameState.PRE && g.home.isNotBlank()) Text("${g.awayScore ?: 0} – ${g.homeScore ?: 0}" + if (g.gameState == GameState.LIVE) "  ·  ${g.statusText}" else "",
                    color = if (g.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, fontSize = 14.sp, style = Tabular)
            }
            Crest(g.homeLogo, g.homeShort.ifBlank { g.home })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val primary = ButtonDefaults.buttonColors(containerColor = Neon.Purple, contentColor = Neon.Bg)
            when (g.gameState) {
                GameState.LIVE -> {
                    Button(onClick = { onGame(g) }, colors = primary, modifier = Modifier.weight(1f)) { Icon(Glyph.Play, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Join live") }
                    OutlinedButton(onClick = { scope.launch { if (!com.fitztv.player.Launcher.play(context, g, fromStart = true)) onPick(g) } }, modifier = Modifier.weight(1f)) { Text("From start", color = Neon.Text) }
                }
                GameState.PRE -> {
                    val reminded = g.id in settings.reminders
                    OutlinedButton(onClick = { onPick(g) }, modifier = Modifier.weight(1f)) { Text("Streams", color = Neon.Text) }
                    OutlinedButton(onClick = { app.alerts.toggleReminder(g) }, modifier = Modifier.weight(1f)) { Text(if (reminded) "Reminder set" else "Remind me", color = if (reminded) Neon.Purple else Neon.Text) }
                }
                GameState.FINAL -> OutlinedButton(onClick = { onReplay(g) }, modifier = Modifier.fillMaxWidth()) { Text("Full replay & highlights", color = Neon.Text) }
            }
        }
    }
}

@Composable
private fun Crest(url: String?, fallback: String) {
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size(46.dp), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), color = Neon.TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** League hub (phone): fixtures, results and the ladder. */
@Composable
fun LeagueHub(league: String, settings: AppSettings, onDismiss: () -> Unit, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit) {
    val repo = FitzApp.instance.repo
    val games by repo.games.collectAsStateWithLifecycle()
    val lg = Leagues.byName(league)
    var tab by rememberSaveable { mutableStateOf(0) }
    val now = System.currentTimeMillis()
    val upcoming = remember(games) { games.filter { it.league == league && it.gameState != GameState.FINAL && it.startUtcMillis < now + 8 * 86_400_000L }.sortedBy { it.startUtcMillis } }
    val results = remember(games) { games.filter { it.league == league && it.gameState == GameState.FINAL }.sortedByDescending { it.startUtcMillis } }
    val tables by produceState<List<StandingsClient.Table>?>(null, league) {
        value = lg?.let { withContext(Dispatchers.IO) { runCatching { StandingsClient.fetch(it) }.getOrDefault(emptyList()) } } ?: emptyList()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.Bg).padding(top = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Glyph.Remove, "Close", tint = Neon.Text) }
                (upcoming + results).firstNotNullOfOrNull { it.leagueLogo }?.let { AsyncImage(it, null, Modifier.size(32.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(10.dp)) }
                Text(league, color = Neon.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Fixtures", "Results", "Ladder").forEachIndexed { i, t -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t) }, colors = chipColors) }
            }
            when (tab) {
                0, 1 -> {
                    val list = if (tab == 0) upcoming else results
                    LazyVerticalGrid(GridCells.Adaptive(280.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyHint(if (tab == 0) "No fixtures this week" else "No recent results", "The guide covers the next 8 days and the last week.") }
                        items(list, key = { it.id }) { g ->
                            PosterCard(g, settings, onClick = { if (g.gameState == GameState.FINAL) onReplay(g) else onGame(g) }, onLongClick = { onPick(g) }, width = 300.dp)
                        }
                    }
                }
                else -> when (val t = tables) {
                    null -> Text("Loading ladder…", color = Neon.TextDim, modifier = Modifier.padding(16.dp))
                    else -> if (t.isEmpty()) EmptyHint("No ladder", "This league has no standings in the feed.", Modifier.padding(16.dp)) else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        t.forEach { table ->
                            item(key = "h:" + table.name) {
                                Row(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                                    Text(table.name, color = Neon.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    table.headers.forEach { Text(it, color = Neon.TextFaint, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp)) }
                                }
                            }
                            items(table.rows.withIndex().toList(), key = { (i, r) -> table.name + i + r.team }) { (i, r) ->
                                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) Neon.Surface else Neon.Bg).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", color = Neon.TextDim, fontSize = 13.sp, style = Tabular, modifier = Modifier.width(26.dp))
                                    r.logo?.let { AsyncImage(it, null, Modifier.size(22.dp), contentScale = ContentScale.Fit) } ?: Spacer(Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(r.team, color = Neon.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    r.values.forEach { v -> Text(v, color = Neon.Text, fontSize = 14.sp, style = Tabular, textAlign = TextAlign.End, modifier = Modifier.width(44.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
