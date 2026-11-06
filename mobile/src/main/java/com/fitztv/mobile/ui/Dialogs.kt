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
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt

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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private data class Team(val name: String, val league: String, val logo: String?)

@Composable
fun FavouritesDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val favs = settings.favoriteTeams
    val list = remember(games, favs) {
        val teams = games.flatMap { listOf(Team(it.home, it.league, it.homeLogo), Team(it.away, it.league, it.awayLogo)) }
            .filter { it.name.isNotBlank() }.distinctBy { it.name }
        val known = teams.map { it.name }.toSet()
        favs.filter { it !in known }.map { Team(it, "", null) } +
            teams.sortedWith(compareByDescending<Team> { it.name in favs }.thenBy { it.league }.thenBy { it.name })
    }
    AlertDialog(
        onDismissRequest = onDone, containerColor = Neon.Bg2, modifier = Modifier.widthIn(max = 560.dp),
        title = { Text("Favourite teams") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(list, key = { it.name }) { t ->
                    val on = t.name in favs
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            app.settings.update { it.copy(favoriteTeams = if (on) it.favoriteTeams - t.name else it.favoriteTeams + t.name) }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (on) "★" else "☆", color = if (on) Neon.Magenta else Neon.TextFaint, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        if (t.logo != null) { AsyncImage(t.logo, null, Modifier.size(26.dp)); Spacer(Modifier.width(10.dp)) }
                        Text(t.name, color = Neon.Text, modifier = Modifier.weight(1f))
                        if (t.league.isNotBlank()) Pill(t.league, Neon.Purple)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}
