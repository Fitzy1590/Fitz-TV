package com.fitztv.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.SearchResults
import com.fitztv.data.search
import com.fitztv.player.PlaybackQueue
import com.fitztv.ui.components.ChannelCard
import com.fitztv.ui.components.GameCard
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.SectionTitle
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.material3.Text as M3Text

/** One search box for teams, games, channels and replays (the remote's mic works in the keyboard). */
@Composable
fun SearchScreen(app: FitzApp, onGame: (Game) -> Unit, onPick: (Game) -> Unit, onReplay: (Game) -> Unit, onTeam: (String) -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(250)
        results = withContext(Dispatchers.Default) { app.repo.search(query, settings) }
    }
    val zone = settings.zone()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { M3Text("Search teams, games, channels, replays") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple,
                    unfocusedBorderColor = Neon.PurpleSoft, focusedLabelColor = Neon.Purple, unfocusedLabelColor = Neon.TextDim, cursorColor = Neon.Magenta,
                ),
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp).fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.length >= 2 && results.isEmpty) item {
            Text("No matches for “$query”", color = Neon.TextDim, modifier = Modifier.padding(horizontal = 48.dp))
        }
        if (results.teams.isNotEmpty()) {
            item { SectionTitle("Teams") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(results.teams, key = { it.name + it.league }) { t ->
                        val fav = settings.favoriteTeams.any { com.fitztv.data.sameTeam(it, t.name) }
                        NeonSurface(onClick = { onTeam(t.name) }, onLongClick = {
                            app.settings.update { s -> s.copy(favoriteTeams = if (fav) s.favoriteTeams.filterNot { com.fitztv.data.sameTeam(it, t.name) }.toSet() else s.favoriteTeams + t.name) }
                        }, modifier = Modifier.width(250.dp), selected = fav) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                (t.logo ?: app.repo.logos.get(t.name))?.let { AsyncImage(it, null, Modifier.size(36.dp)); Spacer(Modifier.width(10.dp)) }
                                Column {
                                    Text((if (fav) "★ " else "") + t.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.league + " • hold OK to " + (if (fav) "unfavourite" else "add to My teams"), fontSize = 11.sp, color = Neon.TextDim, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (results.games.isNotEmpty()) {
            item { SectionTitle("Games") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.games, key = { it.id }) { g ->
                        GameCard(g, zone, settings.use24h, onClick = { if (g.gameState == GameState.FINAL) onReplay(g) else onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g))
                    }
                }
            }
        }
        if (results.channels.isNotEmpty()) {
            item { SectionTitle("Channels") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.channels, key = { it.id }) { c ->
                        ChannelCard(c, app.repo.nowPlaying(c), onClick = { PlaybackQueue.launch(context, listOf(c) + app.repo.variantsOf(c), 0) })
                    }
                }
            }
        }
        if (results.replays.isNotEmpty()) {
            item { SectionTitle("Replays") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.replays, key = { it.id }) { v ->
                        NeonSurface(onClick = { PlaybackQueue.launch(context, listOf(v.asChannel()), 0, null) }, modifier = Modifier.width(280.dp).height(90.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(v.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(v.category, fontSize = 11.sp, color = Neon.TextDim, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
