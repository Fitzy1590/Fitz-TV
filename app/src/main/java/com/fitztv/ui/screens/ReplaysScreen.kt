package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.EmptyHint
import com.fitztv.ui.components.GameCard
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.SectionTitle
import com.fitztv.ui.theme.Neon

/** Replay centre: finished games (catch-up / VOD) and the provider's sport replay library. */
@Composable
fun ReplaysScreen(app: FitzApp, sport: Sport?, onReplay: (Game) -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val vod by app.repo.vod.collectAsState()
    val recent = remember(games, sport, settings.favoriteTeams) { app.repo.replayGames(sport) }
    val library = remember(vod, sport) { vod.filter { sport == null || it.sport == sport || it.sport == Sport.GENERAL }.take(200) }
    val zone = settings.zone()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { SectionTitle("Recent games  (${recent.size})") }
        item {
            if (recent.isEmpty()) EmptyHint("No finished games yet", "Results from the past week appear here once games finish.")
            else LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(recent, key = { it.id }) { g ->
                    GameCard(g, zone, settings.use24h, onClick = { onReplay(g) }, favourite = settings.isFavourite(g))
                }
            }
        }
        item { SectionTitle("Replay library  (${library.size})") }
        if (library.isEmpty()) item {
            EmptyHint("No replays in your provider's library", "Sport categories in your Xtream / M3U on-demand section show up here.")
        }
        items(library.chunked(5), key = { row -> row.first().id }) { row ->
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(row, key = { it.id }) { v ->
                    NeonSurface(onClick = { PlaybackQueue.launch(context, listOf(v.asChannel()), 0, null) }, modifier = Modifier.width(300.dp).height(120.dp)) {
                        Box(Modifier.fillMaxSize()) {
                            if (v.poster != null) AsyncImage(v.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
                            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                                Text(v.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${v.sport.emoji} ${v.category}", fontSize = 11.sp, color = Neon.TextDim, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
