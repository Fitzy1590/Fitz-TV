package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.Neon

private data class Team(val name: String, val league: String, val logo: String?)

/** Pick favourite teams from every team in the downloaded schedule. */
@Composable
fun FavouritesDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val teams = remember(games) {
        games.flatMap { g ->
            listOf(Team(g.home, g.league, g.homeLogo), Team(g.away, g.league, g.awayLogo))
        }.filter { it.name.isNotBlank() }.distinctBy { it.name }
    }
    val favs = settings.favoriteTeams
    // Favourites first (including ones with no fixtures this week), then everyone else by league.
    val list = remember(teams, favs) {
        val known = teams.map { it.name }.toSet()
        favs.filter { it !in known }.map { Team(it, "", null) } +
            teams.sortedWith(compareByDescending<Team> { it.name in favs }.thenBy { it.league }.thenBy { it.name })
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(600.dp)
                .background(Neon.Bg2, RoundedCornerShape(20.dp))
                .border(2.dp, Neon.Purple, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text("Favourite teams", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
            Text("Select a team to add or remove it. Favourites appear in their own panel and float to the top.", color = Neon.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.name }) { t ->
                    val on = t.name in favs
                    NeonSurface(
                        onClick = {
                            app.settings.update { it.copy(favoriteTeams = if (on) it.favoriteTeams - t.name else it.favoriteTeams + t.name) }
                        },
                        modifier = Modifier.fillMaxWidth().then(if (t == list.firstOrNull()) Modifier.focusRequester(focus) else Modifier),
                        radius = 10.dp, selected = on,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (on) "★" else "☆", color = if (on) Neon.Magenta else Neon.TextFaint, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            if (t.logo != null) { AsyncImage(t.logo, null, Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)) }
                            Text(t.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (t.league.isNotBlank()) Pill(t.league, Neon.Purple)
                        }
                    }
                }
            }
        }
    }
}
