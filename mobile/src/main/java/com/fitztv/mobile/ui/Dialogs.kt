package com.fitztv.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.fitztv.data.Team
import com.fitztv.data.sameTeam
import com.fitztv.sports.Leagues
import com.fitztv.sports.Text as TextNorm
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.launch

/** Ranked streams for a game (best first), plus every other channel for the sport. */
@Composable
fun StreamPickerDialog(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var ranked by remember { mutableStateOf<List<Channel>?>(null) }
    LaunchedEffect(game.id) { ranked = app.repo.rankedStreams(game) }
    val m = ranked.orEmpty()
    val others = remember(m) {
        val ids = m.map { it.id }.toSet()
        (app.repo.channelsFor(game.sport) + app.repo.channelsFor(Sport.GENERAL)).filter { it.id !in ids }.sortedByDescending { it.quality }
    }
    val all = m + others
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Neon.Bg2, modifier = Modifier.widthIn(max = 600.dp),
        title = {
            Column {
                Text(if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title, fontWeight = FontWeight.Bold)
                Text("${game.league} • ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}", color = Neon.Cyan, fontSize = 13.sp)
            }
        },
        text = {
          Column {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val reminded = game.id in settings.reminders
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (game.gameState == com.fitztv.data.GameState.PRE) {
                    androidx.compose.material3.FilterChip(selected = reminded, onClick = { app.alerts.toggleReminder(game) },
                        label = { Text(if (reminded) "🔔 Reminder set" else "🔔 Remind me", fontSize = 12.sp) })
                }
                if (game.gameState != com.fitztv.data.GameState.FINAL) {
                    androidx.compose.material3.FilterChip(selected = false, onClick = {
                        if (m.isNotEmpty()) {
                            com.fitztv.record.Recorder.scheduleGame(context, game, m)
                            android.widget.Toast.makeText(context, "Recording set — see Replays › My recordings", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }, label = { Text("⏺ Record", fontSize = 12.sp) })
                    androidx.compose.material3.FilterChip(selected = false, onClick = {
                        onDismiss(); scope.launch { com.fitztv.player.Launcher.addToMultiview(context, game) }
                    }, label = { Text("⊞ Multiview", fontSize = 12.sp) })
                }
            }
            if (ranked == null) Text("Ranking streams…", color = Neon.TextDim)
            else if (all.isEmpty()) Text("No matching channels in your sources.", color = Neon.TextDim)
            else LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(all, key = { _, c -> c.id }) { i, c ->
                    NeonCard(onClick = { onDismiss(); PlaybackQueue.launch(context, all, i, game.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (i < m.size) Pill("#${i + 1}", Neon.Magenta) else Pill(c.sport.label, Neon.TextFaint)
                            Spacer(Modifier.width(8.dp))
                            Text(c.label, fontSize = 14.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            val f = app.repo.probe.fps(c)
                            Pill(Fmt.resolution(app.repo.probe.height(c)) + if (f >= 49f) " • ${f.toInt()}fps" else "", Neon.Cyan)
                        }
                    }
                }
            }
          }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Pick favourite teams from every team in every supported league, with search. */
@Composable
fun FavouritesDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val directory by app.repo.teams.teams.collectAsState()
    val games by app.repo.games.collectAsState()
    LaunchedEffect(Unit) { app.repo.teams.load() }
    var league by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val favs = settings.favoriteTeams
    val all = remember(directory, games) {
        val fromGames = games.flatMap { listOf(Team(it.home, it.league, it.sport, it.homeLogo), Team(it.away, it.league, it.sport, it.awayLogo)) }
            .filter { it.name.isNotBlank() }
        (directory + fromGames).distinctBy { it.name + "|" + it.league }
    }
    val leagues = remember(all) { Leagues.all.map { it.name }.filter { n -> all.any { it.league == n } } }
    val list = remember(all, favs, league, query) {
        val q = TextNorm.norm(query).trim()
        val mine = favs.map { f -> all.firstOrNull { sameTeam(it.name, f) } ?: Team(f, "", Sport.GENERAL, app.repo.logos.get(f)) }
            .filter { league == null || it.league == league }
            .filter { q.isEmpty() || TextNorm.norm(it.name).contains(q) }
        val rest = all.filter { t -> favs.none { sameTeam(it, t.name) } }
            .filter { league == null || it.league == league }
            .filter { q.isEmpty() || TextNorm.norm(it.name).contains(q) }
            .distinctBy { it.name }
            .sortedWith(compareBy<Team> { it.league }.thenBy { it.name })
        mine + rest
    }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Neon.Bg2, modifier = Modifier.widthIn(max = 600.dp),
        title = { Text("My teams") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search teams") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple,
                        unfocusedBorderColor = Neon.PurpleSoft, focusedLabelColor = Neon.Purple, unfocusedLabelColor = Neon.TextDim, cursorColor = Neon.Magenta,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    item { LeagueChip("All", league == null) { league = null } }
                    items(leagues) { l -> LeagueChip(l, league == l) { league = l } }
                }
                if (all.isEmpty()) Text("Loading teams…", color = Neon.TextDim)
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.name + "|" + it.league }) { t ->
                        val on = favs.any { sameTeam(it, t.name) }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                app.settings.update { st ->
                                    st.copy(favoriteTeams = if (on) st.favoriteTeams.filterNot { sameTeam(it, t.name) }.toSet() else st.favoriteTeams + t.name)
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (on) "★" else "☆", color = if (on) Neon.Magenta else Neon.TextFaint, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Logo(t.logo ?: app.repo.logos.get(t.name), t.name, 30)
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, color = Neon.Text, modifier = Modifier.weight(1f))
                            if (t.league.isNotBlank()) Pill(t.league, Neon.Purple)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun LeagueChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.Purple, selectedLabelColor = androidx.compose.ui.graphics.Color.White),
    )
}
