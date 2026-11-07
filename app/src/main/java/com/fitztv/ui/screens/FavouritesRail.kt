package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.data.AppSettings
import com.fitztv.data.Game
import com.fitztv.data.FavouriteEntry
import com.fitztv.data.GameState
import com.fitztv.data.favouriteEntries
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.LiveDot
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.theme.Neon

@Composable
fun FavouritesRail(games: List<Game>, settings: AppSettings, now: Long, onGame: (Game) -> Unit, onEdit: () -> Unit, onTeam: (String) -> Unit = {}, sport: com.fitztv.sports.Sport? = null) {
    val directory by com.fitztv.FitzApp.instance.repo.teams.teams.collectAsState()
    // Only teams that play the selected sport.
    val entries = remember(games, settings.favoriteTeams, now / 60_000, sport, directory) {
        favouriteEntries(games, settings, now, com.fitztv.FitzApp.instance.repo.logos, sport, directory)
    }
    Column(
        Modifier.width(290.dp).fillMaxHeight()
            .padding(start = 32.dp, top = 8.dp, bottom = 24.dp)
            .background(Neon.Surface.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("My teams", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(entries, key = { it.team }) { e -> FavouriteCard(e, settings, onClick = {
                // Live: straight into the game. Otherwise: the team's next 7 days and recent results.
                val g = e.game
                if (g != null && g.gameState == GameState.LIVE) onGame(g) else onTeam(e.team)
            }) }
            item { NeonSurface(onClick = onEdit, modifier = Modifier.fillMaxWidth(), radius = 8.dp) {
                Text("Edit teams", color = Neon.TextDim, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            } }
        }
    }
}

@Composable
fun FavouriteCard(e: FavouriteEntry, settings: AppSettings, onClick: () -> Unit) {
    val zone = settings.zone()
    NeonSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Neon.Bg2), contentAlignment = Alignment.Center) {
                if (e.logo != null) AsyncImage(e.logo, null, Modifier.size(32.dp))
                else Text(e.team.take(2).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.team, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val g = e.game
                if (g == null) {
                    Text("No games in the next 8 days", fontSize = 12.sp, color = Neon.TextFaint)
                } else {
                    val isHome = com.fitztv.data.isTeam(e.team, g.home, g.sport)
                    val opp = if (isHome) g.awayShort.ifBlank { g.away } else g.homeShort.ifBlank { g.home }
                    val vs = if (isHome) "vs $opp" else "@ $opp"
                    when (g.gameState) {
                        GameState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(8.dp))
                            val us = if (isHome) g.homeScore else g.awayScore
                            val them = if (isHome) g.awayScore else g.homeScore
                            Text("${us ?: ""}–${them ?: ""} $vs · ${g.statusText}", fontSize = 12.sp, color = Neon.Live, style = com.fitztv.ui.components.Tabular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GameState.PRE -> Text("$vs · ${Fmt.whenLabel(g, zone, settings.use24h)}", fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        GameState.FINAL -> {
                            val us = if (isHome) g.homeScore else g.awayScore
                            val them = if (isHome) g.awayScore else g.homeScore
                            Text("Final ${us ?: ""}–${them ?: ""} $vs", fontSize = 12.sp, color = Neon.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
