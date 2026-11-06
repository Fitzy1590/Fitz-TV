package com.fitztv.ui.screens

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

enum class Section(val label: String) { HOME("Home"), GUIDE("TV Guide"), CHANNELS("Channels"), SETTINGS("Settings") }

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
            delay(90_000)
            repo.refreshLiveScores()
        }
    }

    val playGame: (Game) -> Unit = { game ->
        scope.launch {
            val matches = repo.rankedStreams(game)   // every matching stream, best resolution/fps first
            if (matches.isNotEmpty()) PlaybackQueue.launch(context, matches, 0, game.id) else pickerFor = game
        }
    }

    Box(Modifier.fillMaxSize().background(Neon.background)) {
        Column(Modifier.fillMaxSize()) {
            // ---- top bar -----------------------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Neon.Text)) { append("FITZ") }
                        withStyle(SpanStyle(color = Neon.Purple)) { append(" TV") }
                    },
                    fontSize = 30.sp, fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Section.entries.forEachIndexed { i, s ->
                        NeonButton(
                            s.label, onClick = { section = s }, selected = section == s,
                            modifier = if (s == section) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.time(now, zone, settings.use24h), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
                    Text(zone.id, fontSize = 11.sp, color = Neon.TextFaint)
                }
            }
            status?.let {
                Text(it, color = Neon.Magenta, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 48.dp))
            }

            // ---- sport switcher --------------------------------------------------------------
            if (section != Section.SETTINGS) {
                val visible = Sport.tabs.filter { it.name !in settings.hiddenSports }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { NeonButton("🏆 All", onClick = { sport = null }, selected = sport == null) }
                    items(visible, key = { it.name }) { s ->
                        val liveCount = games.count { it.sport == s && it.gameState == com.fitztv.data.GameState.LIVE }
                        NeonButton(
                            "${s.emoji} ${s.label}" + if (liveCount > 0) "  • $liveCount live" else "",
                            onClick = { sport = s }, selected = sport == s,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            when (section) {
                Section.HOME -> HomeScreen(games, channels, sport, settings, now, playGame, onPick = { pickerFor = it }, onAddSource = { section = Section.SETTINGS }, onEditFavourites = { editFavs = true })
                Section.GUIDE -> GuideScreen(games, sport, settings, playGame, onPick = { pickerFor = it })
                Section.CHANNELS -> ChannelsScreen(channels.filter { settings.sportVisible(it.sport) }, sport)
                Section.SETTINGS -> SettingsScreen(app)
            }
        }

        if (editFavs) FavouritesDialog(app, onDone = { editFavs = false })
        pickerFor?.let { g ->
            ChannelPickerDialog(app, g, onDismiss = { pickerFor = null })
        }
    }
}
