package com.fitztv.ui.screens

import com.fitztv.ui.theme.SairaCondensed
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Section(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    HOME("Home"), SEARCH("", com.fitztv.ui.components.Glyph.Search), GUIDE("TV guide"), REPLAYS("Replays"), CHANNELS("Channels"), SETTINGS("Settings")
}

@Composable
fun MainScreen(app: FitzApp) {
    val repo = app.repo
    val settings by app.settings.state.collectAsState()
    val games by repo.games.collectAsState()
    val channels by repo.channels.collectAsState()
    val status by repo.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by rememberSaveable { mutableStateOf(if (settings.sources.isEmpty()) Section.SETTINGS else Section.HOME) }
    var sport by rememberSaveable { mutableStateOf<Sport?>(null) }
    var pickerFor by remember { mutableStateOf<Game?>(null) }
    var editFavs by remember { mutableStateOf(false) }
    var teamFor by remember { mutableStateOf<String?>(null) }
    var replayFor by remember { mutableStateOf<Game?>(null) }
    var leagueFor by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val zone = settings.zone()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(section) {
        // Keep live scores fresh only while the user is looking at them.
        while (section == Section.HOME || section == Section.GUIDE) {
            delay(30_000)
            repo.refreshLiveScores()
            scope.launch { repo.refreshChannelListIfStale() }
        }
    }

    val playGame: (Game) -> Unit = { game ->
        scope.launch {
            // Every matching stream, best resolution/fps first.
            when (val r = repo.route(context, game)) {
                is com.fitztv.data.Repository.Route.Streams -> PlaybackQueue.launch(context, r.ranked, 0, game.id)
                com.fitztv.data.Repository.Route.None -> pickerFor = game
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Neon.background)) {
        com.fitztv.ui.components.TeamBackdrop()
        Column(Modifier.fillMaxSize()) {
            // ---- top bar -----------------------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("FITZ TV", fontFamily = SairaCondensed, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                Spacer(Modifier.width(40.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section.entries.forEachIndexed { i, s ->
                        NeonButton(
                            s.label, onClick = { section = s }, selected = section == s, icon = s.icon,
                            modifier = if (s == section) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.time(now, zone, settings.use24h), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Neon.Text, style = com.fitztv.ui.components.Tabular)
                    Text("Adelaide time", fontSize = 12.sp, color = Neon.TextFaint)
                }
            }
            status?.let {
                Text(it, color = Neon.TextDim, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 48.dp))
            }

            // ---- sport switcher --------------------------------------------------------------
            if (section != Section.SETTINGS && section != Section.SEARCH) {
                // Hidden sports still get a tab while one of their games is live.
                // Live counts per sport, computed once per guide update (not per chip, per redraw).
                val liveBySport = remember(games) { games.filter { it.gameState == com.fitztv.data.GameState.LIVE }.groupingBy { it.sport }.eachCount() }
                val visible = Sport.tabs.filter { s -> s.name !in settings.hiddenSports || (liveBySport[s] ?: 0) > 0 }
                // League logo per sport (from the schedule feed), featured leagues first.
                val sportLogo = remember(games) {
                    games.filter { it.leagueLogo != null }.sortedByDescending { it.featured }
                        .groupBy { it.sport }.mapValues { (_, v) -> v.first().leagueLogo }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { NeonButton("All sports", onClick = { sport = null }, selected = sport == null) }
                    items(visible, key = { it.name }) { s ->
                        val liveCount = liveBySport[s] ?: 0
                        NeonButton(
                            s.label + if (liveCount > 0) " · $liveCount live" else "",
                            onClick = { sport = s }, selected = sport == s, icon = com.fitztv.ui.components.Glyph.sport(s), iconUrl = sportLogo[s],
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            when (section) {
                Section.HOME -> HomeScreen(games, channels, sport, settings, now, playGame, onPick = { pickerFor = it }, onAddSource = { section = Section.SETTINGS }, onEditFavourites = { editFavs = true }, onTeam = { teamFor = it })
                Section.GUIDE -> GuideScreen(games, sport, settings, playGame, onPick = { pickerFor = it }, onReplay = { replayFor = it }, onLeague = { leagueFor = it })
                Section.CHANNELS -> ChannelsScreen(channels.filter { settings.sportVisible(it.sport) }, sport)
                Section.REPLAYS -> ReplaysScreen(app, sport, onReplay = { replayFor = it })
                Section.SEARCH -> SearchScreen(app, onGame = playGame, onPick = { pickerFor = it }, onReplay = { replayFor = it }, onTeam = { teamFor = it })
                Section.SETTINGS -> SettingsScreen(app)
            }
        }

        if (editFavs) FavouritesDialog(app, onDone = { editFavs = false })
        teamFor?.let { t ->
            TeamDialog(app, t, onPlay = { teamFor = null; playGame(it) }, onPick = { teamFor = null; pickerFor = it },
                onReplay = { teamFor = null; replayFor = it }, onDismiss = { teamFor = null })
        }
        leagueFor?.let { l -> LeagueHub(l, settings, onDismiss = { leagueFor = null }, onGame = { leagueFor = null; playGame(it) }, onPick = { pickerFor = it }, onReplay = { replayFor = it }) }
        replayFor?.let { g -> GameHub(app, g, startReplays = true, onGame = { replayFor = null; playGame(it) }, onDismiss = { replayFor = null }) }
        UpdatePrompt(app)
        AlertBanner(app, Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
        pickerFor?.let { g ->
            GameHub(app, g, startReplays = false, onGame = { pickerFor = null; playGame(it) }, onDismiss = { pickerFor = null })
        }
    }
}


/** Reminder / score alerts on the home screens; select to watch. Auto-switches if enabled. */
@Composable
fun AlertBanner(app: FitzApp, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<com.fitztv.alerts.AppAlert?>(null) }
    LaunchedEffect(Unit) {
        app.alerts.events.collect { a ->
            if (a.kind == com.fitztv.alerts.AppAlert.Kind.TEAM_STARTING && app.settings.value.autoSwitchToTeam) {
                com.fitztv.player.Launcher.play(context, a.game); return@collect
            }
            current = a
            delay(15_000)
            if (current == a) current = null
        }
    }
    val a = current ?: return
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(a) { runCatching { focus.requestFocus() } }
    com.fitztv.ui.components.NeonSurface(
        onClick = { current = null; scope.launch { com.fitztv.player.Launcher.play(context, a.game) } },
        modifier = modifier.focusRequester(focus), radius = 12.dp, selected = true,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Spacer(Modifier.width(16.dp))
            Text("OK to watch · Back to dismiss", fontSize = 13.sp, color = Neon.TextDim)
        }
    }
    androidx.activity.compose.BackHandler { current = null }
}
