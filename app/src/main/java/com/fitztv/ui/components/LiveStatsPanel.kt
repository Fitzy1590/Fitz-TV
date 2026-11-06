package com.fitztv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.data.Game
import com.fitztv.sports.LiveStats
import com.fitztv.sports.StatsClient
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Live box score that refreshes itself (every 15s while the game is live). */
@Composable
fun LiveStatsPanel(game: Game, page: Int = 0) {
    var stats by remember(game.id) { mutableStateOf<LiveStats?>(null) }
    var loading by remember(game.id) { mutableStateOf(true) }
    LaunchedEffect(game.id) {
        while (true) {
            withContext(Dispatchers.IO) { StatsClient.fetch(game) }?.let { stats = it }
            loading = false
            delay(if (stats?.live != false) 15_000 else 60_000)
        }
    }

    // Full-height, see-through side panel: the game keeps playing and stays visible behind it.
    Row(Modifier.fillMaxHeight()) {
    Box(Modifier.width(2.dp).fillMaxHeight().background(Neon.glowBar))
    Column(
        Modifier.width(430.dp).fillMaxHeight()
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Neon.Bg.copy(alpha = 0.45f), Neon.Bg.copy(alpha = 0.72f))
                )
            )
            .padding(horizontal = 22.dp, vertical = 28.dp)
    ) {
        val s = stats
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE STATS", color = Neon.Purple, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            if (s?.live == true) { LiveDot(); Spacer(Modifier.width(5.dp)) }
            Text(s?.status ?: game.statusText, color = if (s?.live == true) Neon.Live else Neon.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        if (s == null) {
            Text(if (loading) "Loading…" else "No live stats for this game", color = Neon.TextDim, fontSize = 14.sp)
        } else {
            val pages = statPages(s.teamBoxes.any { it.players.isNotEmpty() }, s.lineups.isNotEmpty())
            val current = pages[page.mod(pages.size)]
            if (pages.size > 1) {
                Text(
                    "◀  " + pages.joinToString("   ") { if (it == current) "[$it]" else it } + "  ▶",
                    color = Neon.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
            }
            when (current) {
                "Players" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { s.teamBoxes.forEach { PlayersTable(it, maxRows = 11) } }
                "Line-ups" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { s.lineups.forEach { Pitch(it, height = 210) } }
                else -> StatsBody(s)
            }
        }
    }
    }
}

@Composable
private fun StatsBody(s: LiveStats) {
    Column {
        // Scoreboard
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamBlock(s.awayLogo, s.awayName, Modifier.weight(1f))
            Text("${s.awayScore}  –  ${s.homeScore}", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Neon.Text)
            TeamBlock(s.homeLogo, s.homeName, Modifier.weight(1f))
        }
        if (s.periods.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Grid(listOf("") + s.periods, header = true)
            Grid(listOf(s.awayName.take(4)) + s.awayPeriods)
            Grid(listOf(s.homeName.take(4)) + s.homePeriods)
        }
        if (s.rows.isNotEmpty()) {
            Divider()
            s.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(r.away, fontSize = 13.sp, color = Neon.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
                    Text(r.label, fontSize = 12.sp, color = Neon.TextDim, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(r.home, fontSize = 13.sp, color = Neon.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                }
            }
        }
        if (s.leaders.isNotEmpty()) {
            Divider()
            Text("Leaders", color = Neon.Magenta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            s.leaders.forEach { Text(it, fontSize = 12.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (s.plays.isNotEmpty()) {
            Divider()
            Text("Latest", color = Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            s.plays.forEach { Text("• $it", fontSize = 12.sp, color = Neon.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Updated ${com.fitztv.ui.components.Fmt.time(s.updatedAt, com.fitztv.data.AppSettings.ZONE, false)} • OK to hide", color = Neon.TextFaint, fontSize = 10.sp)
    }
}

@Composable
private fun TeamBlock(logo: String?, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (logo != null) AsyncImage(logo, null, Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Grid(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        cells.forEachIndexed { i, c ->
            Text(
                c, fontSize = 12.sp, maxLines = 1,
                color = if (header) Neon.TextFaint else if (i == 0) Neon.TextDim else Neon.Text,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                modifier = if (i == 0) Modifier.width(54.dp) else Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Purple.copy(alpha = 0.3f)))
    Spacer(Modifier.height(8.dp))
}
