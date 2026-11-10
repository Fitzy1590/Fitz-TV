package com.fitztv.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.fitztv.data.Game
import com.fitztv.sports.LiveStats
import com.fitztv.sports.StatsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Auto-refreshing live box score (every 15s while live, 60s otherwise). */
@Composable
fun LiveStatsCard(game: Game, modifier: Modifier = Modifier, onClip: ((com.fitztv.sports.Clip) -> Unit)? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // In the player: play over the game. Elsewhere: open the clip on its own.
    val playClip: (com.fitztv.sports.Clip) -> Unit = onClip ?: { c ->
        com.fitztv.player.PlaybackQueue.launch(ctx, listOf(com.fitztv.data.VodItem("clip:" + c.url.hashCode(), c.title, "Highlights", game.sport, c.url, c.thumb).asChannel()), 0, null)
    }
    var stats by remember(game.id) { mutableStateOf<LiveStats?>(null) }
    var loading by remember(game.id) { mutableStateOf(true) }
    var failure by remember(game.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(game.id) {
        while (true) {
            val got = withContext(Dispatchers.IO) { StatsClient.fetch(game) }
            got?.let { stats = it }
            failure = if (got == null) StatsClient.lastError else null
            loading = false
            // Keep the last good stats on screen; retry a failed load quickly.
            delay(if (got == null) 6_000 else if (stats?.live != false) 15_000 else 60_000)
        }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        val s = stats
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Live stats", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (s?.live == true) { LiveDot(); Spacer(Modifier.width(4.dp)) }
            Text(s?.status ?: game.statusText, color = if (s?.live == true) Neon.Live else Neon.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        if (s == null) Column {
            Text(if (loading) "Loading…" else "No live stats for this game", color = Neon.TextDim, fontSize = 14.sp)
            if (!loading && failure != null) Text(failure!!, color = Neon.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        else {
            val pages = if (s.categories.isNotEmpty()) listOf("Box score", "Highlights")
                else statPages(s.teamBoxes.any { it.players.isNotEmpty() }, s.lineups.isNotEmpty())
            var page by remember(game.id) { mutableStateOf(pages.first()) }
            if (pages.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    pages.forEach { p ->
                        FilterChip(
                            selected = page == p, onClick = { page = p }, label = { Text(p, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.SurfaceHi, selectedLabelColor = Neon.Text),
                        )
                    }
                }
            }
            when (if (page in pages) page else pages.first()) {
                "Box score" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.categories.forEach { BoxCategoryList(it, maxRows = if (it.title == "Players") 20 else 8) }
                }
                "Highlights" -> ClipList(s.clips, playClip)
                "Players" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { s.teamBoxes.forEach { PlayersTable(it, maxRows = 15) } }
                "Line-ups" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { s.lineups.forEach { Pitch(it, height = 300) } }
                else -> StatsBody(s)
            }
        }
    }
}

@Composable
private fun StatsBody(s: LiveStats) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamBlock(s.awayLogo, s.awayName, Modifier.weight(1f))
            Text("${s.awayScore}  –  ${s.homeScore}", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            TeamBlock(s.homeLogo, s.homeName, Modifier.weight(1f))
        }
        if (s.periods.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Grid(listOf("") + s.periods, header = true)
            Grid(listOf(s.awayName.take(4)) + s.awayPeriods)
            Grid(listOf(s.homeName.take(4)) + s.homePeriods)
        }
        if (s.rows.isNotEmpty()) {
            Line()
            s.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(r.away, fontSize = 13.sp, color = Neon.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(76.dp))
                    Text(r.label, fontSize = 12.sp, color = Neon.TextDim, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(r.home, fontSize = 13.sp, color = Neon.Text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
                }
            }
        }
        if (s.leaders.isNotEmpty()) {
            Line()
            Text("Leaders", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            s.leaders.forEach { Text(it, fontSize = 12.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (s.plays.isNotEmpty()) {
            Line()
            Text("Latest", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            s.plays.forEach { Text(it, fontSize = 12.sp, color = Neon.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Updated ${com.fitztv.ui.components.Fmt.time(s.updatedAt, com.fitztv.data.AppSettings.ZONE, false)}", color = Neon.TextFaint, fontSize = 10.sp)
    }
}

@Composable
private fun TeamBlock(logo: String?, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (logo != null) AsyncImage(logo, null, Modifier.size(38.dp)) else Box(Modifier.size(38.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                modifier = if (i == 0) Modifier.width(50.dp) else Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Line() {
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Line))
    Spacer(Modifier.height(8.dp))
}
