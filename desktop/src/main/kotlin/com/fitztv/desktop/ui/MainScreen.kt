package com.fitztv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.Repository
import com.fitztv.player.Launcher
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Section(val label: String, val icon: ImageVector? = null) {
    HOME("Home"), SEARCH("Search", Glyph.Search), GUIDE("TV guide"), REPLAYS("Replays"), CHANNELS("Channels"), SETTINGS("Settings")
}

@Composable
fun MainScreen(app: FitzApp) {
    val repo = app.repo
    val settings by app.settings.state.collectAsState()
    val games by repo.games.collectAsState()
    val channels by repo.channels.collectAsState()
    val status by repo.status.collectAsState()
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(if (settings.sources.isEmpty()) Section.SETTINGS else Section.HOME) }
    var sport by remember { mutableStateOf<Sport?>(null) }
    var pickerFor by remember { mutableStateOf<Game?>(null) }
    var editFavs by remember { mutableStateOf(false) }
    var teamFor by remember { mutableStateOf<String?>(null) }
    var replayFor by remember { mutableStateOf<Game?>(null) }
    var leagueFor by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val zone = settings.zone()

    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    // Keep live scores fresh only while the user is looking at them.
    LaunchedEffect(section) {
        while (section == Section.HOME || section == Section.GUIDE) {
            delay(30_000)
            repo.refreshLiveScores()
            scope.launch { repo.refreshChannelListIfStale() }
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(3_500); toast = null } }

    val playGame: (Game) -> Unit = { game ->
        scope.launch {
            // Every matching stream, best resolution/fps first.
            when (val r = repo.route(app, game)) {
                is Repository.Route.Streams -> PlaybackQueue.launch(r.ranked, 0, game.id)
                Repository.Route.None -> pickerFor = game
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Neon.background)) {
        Column(Modifier.fillMaxSize()) {
            // ---- top bar -------------------------------------------------------------------
            Row(Modifier.fillMaxWidth().padding(start = PAD, end = PAD, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FITZ TV", fontFamily = SairaCondensed, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                Spacer(Modifier.width(32.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section.entries.forEach { s -> NeonButton(s.label, onClick = { section = s }, selected = section == s, icon = s.icon) }
                    NeonButton("Multiview", onClick = { com.fitztv.player.Multiview.open() }, icon = Glyph.Multiview)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.time(now, zone, settings.use24h), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Neon.Text, style = Tabular)
                    Text("Adelaide time", fontSize = 12.sp, color = Neon.TextFaint)
                }
            }
            status?.let { Text(it, color = Neon.TextDim, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = PAD)) }

            // ---- sport switcher ----------------------------------------------------------------
            if (section != Section.SETTINGS && section != Section.SEARCH) {
                // Hidden sports still get a tab while one of their games is live.
                val liveBySport = remember(games) { games.filter { it.gameState == GameState.LIVE }.groupingBy { it.sport }.eachCount() }
                val visible = Sport.tabs.filter { s -> s.name !in settings.hiddenSports || (liveBySport[s] ?: 0) > 0 }
                val sportLogo = remember(games) { games.filter { it.leagueLogo != null }.sortedByDescending { it.featured }.groupBy { it.sport }.mapValues { (_, v) -> v.first().leagueLogo } }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PAD, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonButton("All sports", onClick = { sport = null }, selected = sport == null)
                    visible.forEach { s ->
                        val liveCount = liveBySport[s] ?: 0
                        NeonButton(s.label + if (liveCount > 0) " · $liveCount live" else "", onClick = { sport = s }, selected = sport == s, icon = Glyph.sport(s), iconUrl = sportLogo[s].takeUnless { com.fitztv.sports.SportLogos.useGeneric(s) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            when (section) {
                Section.HOME -> HomeScreen(games, channels, sport, settings, now, playGame, onPick = { pickerFor = it }, onAddSource = { section = Section.SETTINGS }, onEditFavourites = { editFavs = true }, onTeam = { teamFor = it })
                Section.GUIDE -> GuideScreen(games, sport, settings, playGame, onPick = { pickerFor = it }, onReplay = { replayFor = it }, onLeague = { leagueFor = it }, onMessage = { toast = it })
                Section.CHANNELS -> ChannelsScreen(channels.filter { settings.sportVisible(it.sport) }, sport)
                Section.REPLAYS -> ReplaysScreen(app, sport, onReplay = { replayFor = it })
                Section.SEARCH -> SearchScreen(app, onGame = playGame, onPick = { pickerFor = it }, onReplay = { replayFor = it }, onTeam = { teamFor = it })
                Section.SETTINGS -> SettingsScreen(app)
            }
        }

        if (editFavs) FavouritesDialog(app, onDone = { editFavs = false })
        teamFor?.let { t -> TeamDialog(app, t, onPlay = { teamFor = null; playGame(it) }, onPick = { teamFor = null; pickerFor = it }, onReplay = { teamFor = null; replayFor = it }, onDismiss = { teamFor = null }) }
        leagueFor?.let { l -> LeagueHub(l, settings, onDismiss = { leagueFor = null }, onGame = { leagueFor = null; playGame(it) }, onPick = { pickerFor = it }, onReplay = { replayFor = it }) }
        replayFor?.let { g -> GameHub(app, g, startReplays = true, onGame = { replayFor = null; playGame(it) }, onDismiss = { replayFor = null }) }
        pickerFor?.let { g -> GameHub(app, g, startReplays = false, onGame = { pickerFor = null; playGame(it) }, onDismiss = { pickerFor = null }) }
        UpdatePrompt(app)
        AlertBanner(app)
        toast?.let { Text(it, color = Neon.Text, fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).background(Neon.SurfaceHi, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) }
        if (!com.fitztv.desktop.VlcLocator.available()) VlcMissing()
    }
}

/** libVLC does the playing: if neither the installer's copy nor a VLC install is found, say so plainly. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.VlcMissing() {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    NeonSurface(onClick = { dismissed = true }, modifier = Modifier.align(Alignment.BottomStart).padding(24.dp).width(520.dp), radius = 12.dp) {
        Column(Modifier.padding(16.dp)) {
            Text("VLC not found", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Live)
            Text("Fitz TV plays video with VLC's engine. Install the 64-bit VLC from videolan.org (free), then restart Fitz TV. Click to dismiss.", fontSize = 13.sp, color = Neon.TextDim)
        }
    }
}
