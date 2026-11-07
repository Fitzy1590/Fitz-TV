package com.fitztv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.ChannelCard
import com.fitztv.ui.components.EmptyHint
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.GameCard
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.SectionTitle
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    games: List<Game>,
    channels: List<Channel>,
    sport: Sport?,
    settings: AppSettings,
    now: Long,
    onGame: (Game) -> Unit,
    onPick: (Game) -> Unit,
    onAddSource: () -> Unit,
    onEditFavourites: () -> Unit,
    onTeam: (String) -> Unit = {},
) {
    val zone = settings.zone()
    val context = LocalContext.current
    val epg by FitzApp.instance.repo.epg.collectAsState()

    val repo = FitzApp.instance.repo
    // Live now: every live game from every top league, even ones outside the guide.
    // Rows only need re-bucketing every 5 minutes, not on every 30-second clock tick.
    val bucket = now / 300_000
    val live = remember(games, sport, settings) { repo.liveNow(sport) }
    val nextUp = remember(games, sport, bucket) { if (live.isEmpty()) repo.nextUp(sport) else null }
    val filtered = remember(games, sport, settings) {
        games.filter { (sport == null || it.sport == sport) && settings.showInGuide(it) }
    }
    val rows = remember(filtered, bucket, zone, settings) {
        val t = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        // One pass: work out each game's local day once.
        val pre = filtered.filter { it.gameState == GameState.PRE }
        val day = pre.associateWith { Fmt.localDate(it.startUtcMillis, zone) }
        val favUpcoming = pre.filter { settings.isFavourite(it) }
        val soon = pre.filter { it.startUtcMillis - t in 0..TimeUnit.HOURS.toMillis(3) }
        val soonIds = soon.mapTo(HashSet()) { it.id }
        val todayRest = pre.filter { day[it] == today && it.id !in soonIds }
        val tomorrow = pre.filter { day[it] == today.plusDays(1) }
        val later = pre.filter { day[it]!! > today.plusDays(1) }
        val results = filtered.filter { it.gameState == GameState.FINAL && t - it.startUtcMillis < TimeUnit.HOURS.toMillis(30) }
            .sortedByDescending { it.startUtcMillis }
        listOf(
            "★  My teams — coming up" to favUpcoming,
            "Starting soon" to soon,
            "Later today" to todayRest,
            "Tomorrow" to tomorrow,
            "Upcoming" to later,
            "Recent results" to results,
        ).filter { it.second.isNotEmpty() }
    }
    val quick = remember(channels, sport) { repo.quickChannels(sport) }
    val chans = remember(channels, sport, settings.hiddenSports) {
        channels.filter { (sport == null || it.sport == sport) && settings.sportVisible(it.sport) }.sortedByDescending { it.quality }
    }

    Row(Modifier.fillMaxSize()) {
    if (settings.favoriteTeams.isNotEmpty()) FavouritesRail(games, settings, now, onGame, onEditFavourites, onTeam, sport)
    LazyColumn(Modifier.weight(1f).fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (settings.sources.isEmpty()) {
            item {
                EmptyHint(
                    "Add your IPTV source",
                    "Fitz TV needs an Xtream Codes login or an M3U playlist URL. Only sports channels are kept.",
                )
            }
            item { NeonButton("Add source", onClick = onAddSource, modifier = Modifier.padding(start = 48.dp)) }
        }
        // ---- Live now (always the first row) ----
        item(key = "t:live") { SectionTitle("🔴  Live now" + if (live.isNotEmpty()) "  (${live.size})" else "") }
        item(key = "r:live") {
            if (live.isEmpty()) {
                androidx.tv.material3.Text(
                    "Nothing live right now" + (nextUp?.let { " — next up: ${it.matchup}, ${Fmt.whenLabel(it, zone, settings.use24h)}" } ?: ""),
                    color = com.fitztv.ui.theme.Neon.TextDim, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                )
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(live, key = { "live:" + it.id }) { g ->
                        GameCard(g, zone, settings.use24h, onClick = { onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g))
                    }
                }
            }
        }
        // ---- Quick channels (always shown): Fox Footy / Fox Sports News, NBA TV / Rockets, NFL Network / RedZone ----
        if (quick.isNotEmpty()) {
            item(key = "t:quick") { SectionTitle("📺  Quick channels") }
            item(key = "r:quick") {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(quick, key = { "q:" + it.label }) { q ->
                        com.fitztv.ui.components.NeonSurface(
                            onClick = {
                                when {
                                    q.channels.isNotEmpty() -> PlaybackQueue.launch(context, q.channels, 0)
                                    else -> android.widget.Toast.makeText(context, "${q.label} isn't in your IPTV sources", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.width(260.dp).height(96.dp),
                        ) {
                            androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) {
                                androidx.tv.material3.Text(q.label, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                androidx.tv.material3.Text(
                                    when {
                                        q.channels.isNotEmpty() -> "${q.channels.size} stream${if (q.channels.size > 1) "s" else ""} • best ${Fmt.resolution(repo.probe.height(q.channels.first()))}"
                                        else -> "Not found in your sources"
                                    },
                                    color = com.fitztv.ui.theme.Neon.TextDim, fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (rows.isEmpty() && live.isEmpty()) {
            item { EmptyHint("No upcoming games", "The TV guide is updating, or there are no fixtures for this sport in the next week.") }
        }
        rows.forEach { (title, list) ->
            item(key = "t:$title") { SectionTitle("$title  (${list.size})") }
            item(key = "r:$title") {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(list, key = { it.id }) { g ->
                        GameCard(g, zone, settings.use24h, onClick = { onGame(g) }, onLongClick = { onPick(g) }, favourite = settings.isFavourite(g))
                    }
                }
            }
        }
        if (chans.isNotEmpty()) {
            item(key = "t:chans") { SectionTitle("${sport?.label ?: "Sports"} channels  (${chans.size})") }
            item(key = "r:chans") {
                val shown = chans.take(60)
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(shown, key = { it.id }) { c ->
                        val nowProg = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
                        ChannelCard(c, nowProg, onClick = { PlaybackQueue.launch(context, chans, chans.indexOf(c)) })
                    }
                }
            }
        }
    }
    }
}
