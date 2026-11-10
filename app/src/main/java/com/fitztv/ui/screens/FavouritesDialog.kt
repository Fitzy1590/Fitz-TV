package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.fitztv.data.Team
import com.fitztv.data.sameTeam
import com.fitztv.sports.Leagues
import com.fitztv.sports.Text as T
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.Neon
import androidx.compose.material3.Text as M3Text

/** Pick favourite teams from every team in every supported league. */
@Composable
fun FavouritesDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val directory by app.repo.teams.teams.collectAsState()
    val games by app.repo.games.collectAsState()
    LaunchedEffect(Unit) { app.repo.teams.load() }

    var league by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val favs = settings.favoriteTeams

    // Directory plus any team seen in fixtures (in case a league's team list couldn't load).
    val all = remember(directory, games) {
        val fromGames = games.flatMap { listOf(Team(it.home, it.league, it.sport, it.homeLogo), Team(it.away, it.league, it.sport, it.awayLogo)) }
            .filter { it.name.isNotBlank() }
        (directory + fromGames).distinctBy { it.name + "|" + it.league }
    }
    val leagues = remember(all) { Leagues.all.map { it.name }.filter { n -> all.any { it.league == n } } }
    val list = remember(all, favs, league, query) {
        val q = T.norm(query).trim()
        val mine = favs.map { f -> all.firstOrNull { sameTeam(it.name, f) } ?: Team(f, "", com.fitztv.sports.Sport.GENERAL, app.repo.logos.get(f)) }
        val rest = all.filter { t -> favs.none { sameTeam(it, t.name) } }
            .filter { league == null || it.league == league }
            .filter { q.isEmpty() || T.norm(it.name).contains(q) }
            .distinctBy { it.name }
            .sortedWith(compareBy<Team> { it.league }.thenBy { it.name })
        (if (league == null && q.isEmpty()) mine else mine.filter { league == null || it.league == league }) + rest
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(720.dp)
                .background(Neon.Bg2, RoundedCornerShape(16.dp))
                .border(1.dp, Neon.Line, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text("My teams", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Text("Pick a league or search, then select a team to add or remove it.", color = Neon.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { M3Text("Search teams") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple,
                    unfocusedBorderColor = Neon.Line, focusedLabelColor = Neon.Purple, unfocusedLabelColor = Neon.TextDim,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                item { NeonButton("All leagues", onClick = { league = null }, selected = league == null) }
                items(leagues) { l -> NeonButton(l, onClick = { league = l }, selected = league == l) }
            }
            Spacer(Modifier.height(8.dp))
            if (all.isEmpty()) Text("Loading teams…", color = Neon.TextDim)
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.name + "|" + it.league }) { t ->
                    val on = favs.any { sameTeam(it, t.name) }
                    NeonSurface(
                        onClick = {
                            app.settings.update { st ->
                                st.copy(favoriteTeams = if (on) st.favoriteTeams.filterNot { sameTeam(it, t.name) }.toSet() else st.favoriteTeams + t.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().then(if (t == list.firstOrNull()) Modifier.focusRequester(focus) else Modifier),
                        radius = 10.dp, selected = on,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.tv.material3.Icon(if (on) com.fitztv.ui.components.Glyph.Star else com.fitztv.ui.components.Glyph.StarOff, if (on) "In My teams" else "Not in My teams", tint = if (on) Neon.Purple else Neon.TextFaint, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            val logo = t.logo ?: app.repo.logos.get(t.name)
                            if (logo != null) AsyncImage(logo, null, Modifier.size(30.dp)) else Spacer(Modifier.size(30.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(t.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (t.league.isNotBlank()) Pill(t.league, Neon.Purple)
                        }
                    }
                }
            }
        }
    }
}
