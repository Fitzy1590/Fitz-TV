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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fitztv.data.AppSettings
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.sports.Sport
import com.fitztv.ui.components.EmptyHint
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.LiveDot
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.Neon
import java.time.LocalDate

/** Guide: fixtures by day, or the classic channel grid from your provider's EPG. */
@Composable
fun GuideScreen(
    games: List<Game>,
    sport: Sport?,
    settings: AppSettings,
    onGame: (Game) -> Unit,
    onPick: (Game) -> Unit,
) {
    var grid by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 48.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Games", onClick = { grid = false }, selected = !grid, icon = com.fitztv.ui.components.Glyph.Games)
            NeonButton("Channel grid", onClick = { grid = true }, selected = grid, icon = com.fitztv.ui.components.Glyph.Grid)
        }
        if (grid) GridGuide(sport, settings) else GamesGuide(games, sport, settings, onGame, onPick)
    }
}

@Composable
private fun GamesGuide(
    games: List<Game>,
    sport: Sport?,
    settings: AppSettings,
    onGame: (Game) -> Unit,
    onPick: (Game) -> Unit,
) {
    val zone = settings.zone()
    val today = LocalDate.now(zone)
    var dayOffset by rememberSaveable { mutableLongStateOf(0L) }
    val day = today.plusDays(dayOffset)
    val list = remember(games, sport, day, zone) {
        games.filter { (sport == null || it.sport == sport) && settings.showInGuide(it) && Fmt.localDate(it.startUtcMillis, zone) == day }
            .sortedBy { it.startUtcMillis }
    }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items((-1L..6L).toList()) { off ->
                NeonButton(Fmt.dayLabel(today.plusDays(off), zone), onClick = { dayOffset = off }, selected = off == dayOffset)
            }
        }
        Text(
            "All times in your local time zone (${zone.id})",
            color = Neon.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 8.dp),
        )
        if (list.isEmpty()) {
            EmptyHint("Nothing scheduled", "No ${sport?.label ?: ""} games on ${Fmt.dayLabel(day, zone)}.")
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { g -> GuideRow(g, settings, onClick = { onGame(g) }, onLongClick = { onPick(g) }) }
        }
    }
}

@Composable
private fun GuideRow(g: Game, settings: AppSettings, onClick: () -> Unit, onLongClick: () -> Unit) {
    val zone = settings.zone()
    NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().height(72.dp), radius = 12.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.time(g.startUtcMillis, zone, settings.use24h),
                color = Neon.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, style = com.fitztv.ui.components.Tabular, modifier = Modifier.width(112.dp),
            )
            Row(Modifier.width(172.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(com.fitztv.ui.components.Glyph.sport(g.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(g.league, color = Neon.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            val matchup = if (g.home.isNotBlank()) "${g.away}  vs  ${g.home}" else g.title
            Column(Modifier.weight(1f)) {
                if (settings.isFavourite(g)) Text("My team", color = Neon.Purple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(matchup, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(g.venue, g.broadcasts.takeIf { it.isNotEmpty() }?.joinToString(", ")).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = Neon.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (g.gameState) {
                GameState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(); Spacer(Modifier.width(8.dp))
                    Text("${g.awayScore ?: ""}–${g.homeScore ?: ""}  ${g.statusText}", color = Neon.Live, fontWeight = FontWeight.Medium, fontSize = 14.sp, style = com.fitztv.ui.components.Clock)
                }
                GameState.FINAL -> Text("${g.awayScore ?: ""}-${g.homeScore ?: ""}  ${g.statusText}", color = Neon.TextFaint, fontSize = 14.sp)
                GameState.PRE -> Unit
            }
        }
    }
}
