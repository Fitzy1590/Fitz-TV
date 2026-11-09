package com.fitztv.ui.screens

import com.fitztv.ui.theme.SairaCondensed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.sports.Leagues
import com.fitztv.sports.Sport
import com.fitztv.sports.StandingsClient
import com.fitztv.ui.components.EmptyHint
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.Glyph
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.PosterCard
import com.fitztv.ui.components.Tabular
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Fixtures: day strip, games grouped by league as cards with Join live / From start / Full replay. */
@Composable
fun FixturesScreen(
    games: List<Game>,
    sport: Sport?,
    settings: AppSettings,
    onGame: (Game) -> Unit,
    onPick: (Game) -> Unit,
    onReplay: (Game) -> Unit,
    onLeague: (String) -> Unit,
) {
    val zone = settings.zone()
    val today = LocalDate.now(zone)
    var dayOffset by rememberSaveable { mutableLongStateOf(0L) }
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
        LazyRow(Modifier.focusRestorerCompat(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((-2L..6L).toList()) { off ->
                val d = today.plusDays(off)
                val label = if (off == 0L) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " " + d.dayOfMonth
                NeonButton(label, onClick = { dayOffset = off }, selected = off == dayOffset)
            }
        }
        if (byLeague.isEmpty()) {
            EmptyHint("Nothing scheduled", "No ${sport?.label ?: ""} games on ${Fmt.dayLabel(day, zone)}.")
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            byLeague.forEach { (league, list) ->
                item(key = "h:$league", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        list.firstNotNullOfOrNull { it.leagueLogo }?.let { AsyncImage(it, null, Modifier.size(28.dp), contentScale = ContentScale.Fit) }
                        Text(league, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                        if (Leagues.byName(league)?.espnPath != null) NeonButton("Ladder & fixtures", onClick = { onLeague(league) }, icon = Glyph.Later)
                    }
                }
                items(list, key = { it.id }) { g ->
                    val openPage = { if (g.gameState == GameState.FINAL) onReplay(g) else onPick(g) }
                    FixtureRow(g, settings, onLongClick = openPage) { if (g.gameState == GameState.LIVE) onGame(g) else openPage() }
                }
            }
        }
    }
}

/** Dense fixture row: time or status, both teams with crests, score. Select opens the game page. */
@Composable
fun FixtureRow(g: Game, settings: AppSettings, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val live = g.gameState == GameState.LIVE
    com.fitztv.ui.components.NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (g.gameState) {
                    GameState.LIVE -> Text("LIVE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.background(Neon.Live, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    GameState.PRE -> Text(Fmt.time(g.startUtcMillis, settings.zone(), settings.use24h), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
                    GameState.FINAL -> Text("FT", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
                }
                if (live && g.statusText.isNotBlank()) Text(g.statusText, fontSize = 12.sp, color = Neon.Live, maxLines = 1, style = Tabular)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RowTeam(g.awayLogo, g.away.ifBlank { g.title }, g.awayShort, if (g.gameState != GameState.PRE) g.awayScore else null, live, g.home.isNotBlank())
                if (g.home.isNotBlank()) RowTeam(g.homeLogo, g.home, g.homeShort, if (g.gameState != GameState.PRE) g.homeScore else null, live, true)
            }
        }
    }
}

@Composable
private fun RowTeam(logo: String?, name: String, short: String, score: String?, live: Boolean, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, null, Modifier.size(30.dp), contentScale = ContentScale.Fit)
            else Text(short.ifBlank { name }.take(3).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
        }
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showScore && !score.isNullOrBlank()) Text(score, fontFamily = SairaCondensed, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (live) Neon.Live else Neon.Text, style = Tabular)
    }
}

@Composable
fun FixtureCard(g: Game, settings: AppSettings, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit) {
    val app = FitzApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = settings.zone()
    Column(
        Modifier.fillMaxWidth().background(Neon.Surface, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.league, fontSize = 14.sp, color = Neon.TextDim, modifier = Modifier.weight(1f))
            when (g.gameState) {
                GameState.LIVE -> Text("LIVE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(Neon.Live, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                GameState.PRE -> Text(Fmt.whenLabel(g, zone, settings.use24h), fontSize = 14.sp, color = Neon.Text, style = Tabular)
                GameState.FINAL -> Text(g.statusText.ifBlank { "Final" }, fontSize = 14.sp, color = Neon.TextDim)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crest(g.awayLogo, g.awayShort.ifBlank { g.away })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(g.matchup.replace(" vs ", " v ").uppercase(), fontFamily = SairaCondensed, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (g.gameState != GameState.PRE && g.home.isNotBlank()) Text("${g.awayScore ?: 0} – ${g.homeScore ?: 0}" + if (g.gameState == GameState.LIVE) "  ·  ${g.statusText}" else "",
                    fontSize = 16.sp, color = if (g.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, style = Tabular)
            }
            Crest(g.homeLogo, g.homeShort.ifBlank { g.home })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (g.gameState) {
                GameState.LIVE -> {
                    NeonButton("Join live", onClick = { onGame(g) }, selected = true, icon = Glyph.Play)
                    NeonButton("From start", onClick = { scope.launch { if (!com.fitztv.player.Launcher.play(context, g, fromStart = true)) onPick(g) } }, icon = Glyph.Start)
                    NeonButton("Streams", onClick = { onPick(g) }, icon = Glyph.Tv)
                }
                GameState.PRE -> {
                    val reminded = g.id in settings.reminders
                    NeonButton("Streams", onClick = { onPick(g) }, icon = Glyph.Tv)
                    NeonButton(if (reminded) "Reminder set" else "Remind me", onClick = { app.alerts.toggleReminder(g) }, selected = reminded, icon = if (reminded) Glyph.BellOn else Glyph.Bell)
                }
                GameState.FINAL -> NeonButton("Full replay & highlights", onClick = { onReplay(g) }, icon = Glyph.Play)
            }
        }
    }
}

@Composable
private fun Crest(url: String?, fallback: String) {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size(56.dp), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
    }
}

/** League hub: upcoming fixtures, recent results and the ladder for one league. */
@Composable
fun LeagueHub(league: String, settings: AppSettings, onDismiss: () -> Unit, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit) {
    val repo = FitzApp.instance.repo
    val games by repo.games.collectAsStateWithLifecycleCompat()
    val lg = Leagues.byName(league)
    var tab by rememberSaveable { mutableStateOf(0) }
    val now = System.currentTimeMillis()
    val upcoming = remember(games) { games.filter { it.league == league && it.gameState != GameState.FINAL && it.startUtcMillis < now + 8 * 86_400_000L }.sortedBy { it.startUtcMillis } }
    val results = remember(games) { games.filter { it.league == league && it.gameState == GameState.FINAL }.sortedByDescending { it.startUtcMillis } }
    val tables by produceState<List<StandingsClient.Table>?>(null, league) {
        value = lg?.let { withContext(Dispatchers.IO) { runCatching { StandingsClient.fetch(it) }.getOrDefault(emptyList()) } } ?: emptyList()
    }
    val zone = settings.zone()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.Bg).padding(top = 32.dp)) {
            Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                (upcoming + results).firstNotNullOfOrNull { it.leagueLogo }?.let { AsyncImage(it, null, Modifier.size(48.dp), contentScale = ContentScale.Fit) }
                Text(league, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
                Spacer(Modifier.weight(1f))
                listOf("Fixtures", "Results", "Ladder").forEachIndexed { i, t -> NeonButton(t, onClick = { tab = i }, selected = tab == i) }
            }
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0, 1 -> {
                    val list = if (tab == 0) upcoming else results
                    if (list.isEmpty()) EmptyHint(if (tab == 0) "No fixtures this week" else "No recent results", "The guide covers the next 8 days and the last week.")
                    LazyVerticalGrid(GridCells.Adaptive(320.dp), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(list, key = { it.id }) { g ->
                            PosterCard(g, zone, settings.use24h, onClick = { if (g.gameState == GameState.FINAL) onReplay(g) else onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g))
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
        tables == null -> Text("Loading ladder…", color = Neon.TextDim, modifier = Modifier.padding(horizontal = 48.dp))
        tables.isEmpty() -> EmptyHint("No ladder", "This league has no standings in the feed.")
        else -> LazyColumn(contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tables.forEach { t ->
                item(key = "h:" + t.name) {
                    Row(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                        Text(t.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, modifier = Modifier.weight(1f))
                        t.headers.forEach { Text(it, fontSize = 13.sp, color = Neon.TextFaint, textAlign = TextAlign.End, modifier = Modifier.width(72.dp)) }
                    }
                }
                items(t.rows.withIndex().toList(), key = { (i, r) -> t.name + i + r.team }) { (i, r) ->
                    androidx.tv.material3.Surface(
                        onClick = {}, modifier = Modifier.fillMaxWidth(),
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = if (i % 2 == 0) Neon.Surface else Neon.Bg, focusedContainerColor = Neon.SurfaceHi),
                        border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Neon.Purple))),
                        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", fontSize = 15.sp, color = Neon.TextDim, style = Tabular, modifier = Modifier.width(36.dp))
                            r.logo?.let { AsyncImage(it, null, Modifier.size(28.dp), contentScale = ContentScale.Fit) } ?: Spacer(Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(r.team, fontSize = 16.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            r.values.forEach { v -> Text(v, fontSize = 16.sp, color = Neon.Text, style = Tabular, textAlign = TextAlign.End, modifier = Modifier.width(72.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> = collectAsStateWithLifecycle()
