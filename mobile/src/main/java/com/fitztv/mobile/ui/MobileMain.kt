package com.fitztv.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    GUIDE("Guide", Icons.Filled.DateRange),
    REPLAYS("Replays", Icons.Filled.Replay),
    CHANNELS("Channels", Icons.Filled.LiveTv),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/** True on the unfolded Fold screen, tablets and phones in landscape. */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun MobileMain(app: FitzApp) {
    val repo = app.repo
    val settings by app.settings.state.collectAsState()
    val games by repo.games.collectAsState()
    val channels by repo.channels.collectAsState()
    val status by repo.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(if (settings.sources.isEmpty()) Tab.SETTINGS else Tab.HOME) }
    var sport by rememberSaveable { mutableStateOf<Sport?>(null) }
    var pickerFor by remember { mutableStateOf<Game?>(null) }
    var editFavs by remember { mutableStateOf(false) }
    var teamFor by remember { mutableStateOf<String?>(null) }
    var replayFor by remember { mutableStateOf<Game?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(tab) { while (tab == Tab.HOME || tab == Tab.GUIDE) { delay(90_000); repo.refreshLiveScores() } }

    val playGame: (Game) -> Unit = { game ->
        scope.launch {
            finding = true
            val r = repo.route(context, game)
            finding = false
            when (r) {
                com.fitztv.data.Repository.Route.Kayo -> com.fitztv.external.Kayo.open(context)
                is com.fitztv.data.Repository.Route.Streams -> PlaybackQueue.launch(context, r.ranked, 0, game.id)
                com.fitztv.data.Repository.Route.None -> pickerFor = game
            }
        }
    }
    val wide = isWide()

    Box(Modifier.fillMaxSize().background(Neon.background)) {
    TeamBackdrop()
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!wide) NavigationBar(containerColor = Neon.Bg2.copy(alpha = 0.92f)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && !searching, onClick = { tab = t; searching = false },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Neon.Purple, unselectedIconColor = Neon.TextDim, selectedTextColor = Neon.Purple, unselectedTextColor = Neon.TextDim),
                    )
                }
            }
        },
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            if (wide) NavigationRail(containerColor = Neon.Bg2.copy(alpha = 0.92f)) {
                Spacer(Modifier.padding(top = 8.dp))
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t && !searching, onClick = { tab = t; searching = false },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Neon.Purple, unselectedIconColor = Neon.TextDim, selectedTextColor = Neon.Purple, unselectedTextColor = Neon.TextDim),
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                // Header
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Neon.Text)) { append("FITZ") }
                            withStyle(SpanStyle(color = Neon.Purple)) { append(" TV") }
                        },
                        fontSize = 24.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = { searching = !searching }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search", tint = Neon.Text)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.time(now, settings.zone(), settings.use24h), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
                        Text(settings.zone().id, fontSize = 10.sp, color = Neon.TextFaint)
                    }
                }
                (if (finding) "Finding the best stream…" else status)?.let {
                    Text(it, color = Neon.Magenta, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 16.dp))
                }
                val liveBySport = remember(games) { games.filter { it.gameState == GameState.LIVE }.groupingBy { it.sport }.eachCount() }
                if (tab != Tab.SETTINGS && !searching) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SportChip("🏆 All", sport == null) { sport = null } }
                        items(Sport.tabs.filter { s -> settings.sportVisible(s) || (liveBySport[s] ?: 0) > 0 }, key = { it.name }) { s ->
                            val live = liveBySport[s] ?: 0
                            SportChip("${s.emoji} ${s.label}" + if (live > 0) " • $live" else "", sport == s) { sport = s }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    if (searching) SearchTab(app, onGame = playGame, onPick = { pickerFor = it }, onReplay = { replayFor = it }, onTeam = { teamFor = it })
                    else when (tab) {
                        Tab.HOME -> HomeTab(games, channels, sport, settings, now, playGame, onPick = { pickerFor = it }, onEditFavs = { editFavs = true }, onAddSource = { tab = Tab.SETTINGS }, onTeam = { teamFor = it })
                        Tab.GUIDE -> GuideTab(games, sport, settings, playGame) { pickerFor = it }
                        Tab.REPLAYS -> ReplaysTab(app, sport) { replayFor = it }
                        Tab.CHANNELS -> ChannelsTab(channels.filter { settings.sportVisible(it.sport) }, sport)
                        Tab.SETTINGS -> SettingsTab(app, onEditFavs = { editFavs = true })
                    }
                }
            }
        }
    }
    }
    pickerFor?.let { StreamPickerDialog(app, it) { pickerFor = null } }
    if (editFavs) FavouritesDialog(app) { editFavs = false }
    teamFor?.let { t ->
        TeamDialog(app, t, onPlay = { teamFor = null; playGame(it) }, onPick = { teamFor = null; pickerFor = it },
            onReplay = { teamFor = null; replayFor = it }, onDismiss = { teamFor = null })
    }
    replayFor?.let { g -> ReplayDialog(app, g) { replayFor = null } }
    UpdatePrompt(app)
    AlertBanner(app)
}

@Composable
private fun SportChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Neon.Surface, labelColor = Neon.TextDim,
            selectedContainerColor = Neon.Purple, selectedLabelColor = Color.White,
        ),
    )
}


/** Reminder / score alerts on the main screens (tap to watch); auto-switch if enabled. */
@Composable
fun AlertBanner(app: FitzApp) {
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
    // Android 13+: allow notifications so alerts also reach you outside the app.
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val a = current ?: return
    androidx.compose.material3.Snackbar(
        action = { androidx.compose.material3.TextButton(onClick = { current = null; scope.launch { com.fitztv.player.Launcher.play(context, a.game) } }) { Text("WATCH", color = Neon.Cyan) } },
        dismissAction = { androidx.compose.material3.TextButton(onClick = { current = null }) { Text("✕", color = Neon.TextDim) } },
        containerColor = Neon.Bg2, contentColor = Neon.Text,
        modifier = Modifier.padding(12.dp).statusBarsPaddingCompat(),
    ) { Text(a.text, fontWeight = FontWeight.Bold) }
}

private fun Modifier.statusBarsPaddingCompat() = this.then(Modifier.statusBarsPadding())
