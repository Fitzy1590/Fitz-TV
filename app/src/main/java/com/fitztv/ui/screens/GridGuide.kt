package com.fitztv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Catchup
import com.fitztv.data.gridRows
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.theme.Neon

private const val WINDOW_MIN = 180          // 3 hours visible
private const val DP_PER_MIN = 6

/**
 * Classic grid guide from your provider's EPG: channels down the side, time across the top.
 * Select: on now = watch; finished + catch-up = watch from the start; upcoming = hold to record.
 */
@Composable
fun GridGuide(sport: Sport?, settings: AppSettings) {
    val app = FitzApp.instance
    val context = LocalContext.current
    val epg by app.repo.epg.collectAsState()
    val channels by app.repo.channels.collectAsState()
    val now = System.currentTimeMillis()
    val slot = 30 * 60_000L
    var start by rememberSaveable { mutableLongStateOf(now - now % slot) }
    val end = start + WINDOW_MIN * 60_000L
    val rows = remember(epg, channels, sport, settings.hiddenSports) { app.repo.gridRows(sport, settings) }
    val zone = settings.zone()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("Earlier", onClick = { start -= slot * 2 }, icon = com.fitztv.ui.components.Glyph.Earlier)
            NeonButton("Now", onClick = { start = now - now % slot }, selected = now in start until end)
            NeonButton("Later", onClick = { start += slot * 2 }, icon = com.fitztv.ui.components.Glyph.Later)
            Spacer(Modifier.width(16.dp))
            Text("${Fmt.dayLabel(Fmt.localDate(start, zone), zone)} · ${rows.size} channels with listings", color = Neon.TextFaint, fontSize = 12.sp)
        }
        // Time ruler.
        Row(Modifier.padding(start = 48.dp + 210.dp)) {
            (0 until WINDOW_MIN / 30).forEach { i ->
                Text(Fmt.time(start + i * slot, zone, settings.use24h), color = Neon.Cyan, fontSize = 12.sp, modifier = Modifier.width((30 * DP_PER_MIN).dp))
            }
        }
        if (rows.isEmpty()) {
            com.fitztv.ui.components.EmptyHint("No programme listings", "Your sources haven't provided an EPG for these channels yet. Add an XMLTV URL to a source to fill this grid.")
        } else LazyColumn(contentPadding = PaddingValues(start = 48.dp, end = 24.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.channel.id }) { row ->
                Row(Modifier.height(58.dp)) {
                    NeonSurface(onClick = { PlaybackQueue.launch(context, row.variants, 0) }, modifier = Modifier.width(200.dp).fillMaxHeight(), radius = 8.dp) {
                        Row(Modifier.padding(horizontal = 8.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                            if (row.channel.logo != null) { AsyncImage(row.channel.logo, null, Modifier.size(width = 40.dp, height = 28.dp)); Spacer(Modifier.width(8.dp)) }
                            Text(row.channel.label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.width((WINDOW_MIN * DP_PER_MIN).dp).fillMaxHeight()) {
                        // Now line.
                        if (now in start until end) Box(Modifier.offset(x = (((now - start) / 60_000L) * DP_PER_MIN).toInt().dp).width(2.dp).fillMaxHeight().background(Neon.Live))
                        row.programs.filter { it.stopMillis > start && it.startMillis < end }.forEach { p ->
                            val s0 = maxOf(p.startMillis, start)
                            val e0 = minOf(p.stopMillis, end)
                            val x = ((s0 - start) / 60_000L * DP_PER_MIN).toInt()
                            val w = (((e0 - s0) / 60_000L) * DP_PER_MIN).toInt().coerceAtLeast(12)
                            val live = now in p.startMillis until p.stopMillis
                            val past = p.stopMillis <= now
                            NeonSurface(
                                onClick = {
                                    when {
                                        live -> PlaybackQueue.launch(context, row.variants, 0)
                                        past -> {
                                            val url = Catchup.url(row.channel, p.startMillis, ((p.stopMillis - p.startMillis) / 60_000L).toInt() + 10)
                                            if (url != null) PlaybackQueue.launch(context, listOf(row.channel.copy(id = "cu:${row.channel.id}:${p.startMillis}", streamUrl = url, vod = true, name = p.title)), 0)
                                            else Toast.makeText(context, "No catch-up on this channel", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> Toast.makeText(context, "Starts ${Fmt.time(p.startMillis, zone, settings.use24h)} — hold OK to record", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = {
                                    if (!past) {
                                        com.fitztv.record.Recorder.schedule(context, p.title, row.channel.sport.name, row.variants, p.startMillis, p.stopMillis)
                                        Toast.makeText(context, "Recording ${p.title}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.offset(x = x.dp).width((w - 4).coerceAtLeast(8).dp).fillMaxHeight(),
                                radius = 8.dp, selected = live,
                            ) {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(p.title, fontSize = 13.sp, fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (past) Neon.TextFaint else Neon.Text)
                                    Text("${Fmt.time(p.startMillis, zone, settings.use24h)}–${Fmt.time(p.stopMillis, zone, settings.use24h)}", fontSize = 12.sp, color = Neon.TextDim, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
