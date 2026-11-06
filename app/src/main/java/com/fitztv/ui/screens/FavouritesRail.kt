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
fun FavouritesRail(games: List<Game>, settings: AppSettings, now: Long, onGame: (Game) -> Unit, onEdit: () -> Unit) {
    val entries = remember(games, settings.favoriteTeams, now) { favouriteEntries(games, settings, now) }
    Column(
        Modifier.width(290.dp).fillMaxHeight()
            .padding(start = 32.dp, top = 8.dp, bottom = 24.dp)
            .background(Neon.Surface.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text("★  My teams", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Neon.Magenta)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Neon.glowBar))
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(entries, key = { it.team }) { e -> FavouriteCard(e, settings, onClick = { e.game?.let(onGame) ?: onEdit() }) }
            item { NeonSurface(onClick = onEdit, modifier = Modifier.fillMaxWidth(), radius = 10.dp) {
                Text("+ Edit favourites", color = Neon.TextDim, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            } }
        }
    }
}

@Composable
fun FavouriteCard(e: FavouriteEntry, settings: AppSettings, onClick: () -> Unit) {
    val zone = settings.zone()
    NeonSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Neon.Bg2), contentAlignment = Alignment.Center) {
                if (e.logo != null) AsyncImage(e.logo, null, Modifier.size(34.dp))
                else Text(e.team.take(2).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neon.TextDim)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.team, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val g = e.game
                if (g == null) {
                    Text("No games in the next 8 days", fontSize = 12.sp, color = Neon.TextFaint)
                } else {
                    val isHome = g.home.equals(e.team, true)
                    val opp = if (isHome) g.awayShort.ifBlank { g.away } else g.homeShort.ifBlank { g.home }
                    val vs = if (isHome) "vs $opp" else "@ $opp"
                    when (g.gameState) {
                        GameState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(5.dp))
                            val us = if (isHome) g.homeScore else g.awayScore
                            val them = if (isHome) g.awayScore else g.homeScore
                            Text("${us ?: ""}-${them ?: ""} $vs • ${g.statusText}", fontSize = 12.sp, color = Neon.Live, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GameState.PRE -> Text("$vs • ${Fmt.whenLabel(g, zone, settings.use24h)}", fontSize = 12.sp, color = Neon.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        GameState.FINAL -> {
                            val us = if (isHome) g.homeScore else g.awayScore
                            val them = if (isHome) g.awayScore else g.homeScore
                            Text("Final ${us ?: ""}-${them ?: ""} $vs", fontSize = 12.sp, color = Neon.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
