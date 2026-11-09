package com.fitztv.ui.screens

import com.fitztv.ui.theme.SairaCondensed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.Glyph
import com.fitztv.ui.components.MatchupArt
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.PosterBadge
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Tabular
import com.fitztv.ui.components.posterSubtitle
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.delay

/**
 * Home featured trio: one big game on the left (fanart, matchup art, Watch / Streams) and two
 * smaller ones stacked on the right. Your teams' and the biggest games, in that order.
 */
@Composable
fun FeaturedTrio(
    items: List<Game>,
    settings: AppSettings,
    onWatch: (Game) -> Unit,
    onStreams: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val g = items[0]
    val side = items.drop(1).take(2)
    val zone = settings.zone()
    val repo = FitzApp.instance.repo
    val fanTeam = when {
        settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.home, g.sport) } -> g.home
        settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.away, g.sport) } -> g.away
        else -> g.home
    }
    val fanart by produceState<String?>(null, fanTeam) { value = runCatching { repo.logos.fanart(fanTeam).firstOrNull() }.getOrNull() }

    Row(modifier.fillMaxWidth().height(360.dp).padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NeonSurface(
            onClick = { if (g.gameState == GameState.LIVE) onWatch(g) else onStreams(g) },
            modifier = Modifier.weight(1.9f).fillMaxHeight(), radius = 16.dp,
        ) {
            Box(Modifier.fillMaxSize().background(Neon.Bg2))
            fanart?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.5f) }
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().aspectRatio(16f / 9f)) {
                MatchupArt(g, Modifier.fillMaxSize(), logoSize = 110.dp)
            }
            // Legibility scrim for the text on the left (functional, not decoration).
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Neon.Bg2, 0.46f to Neon.Bg2.copy(alpha = 0.92f), 0.7f to Color.Transparent)))
            Column(Modifier.fillMaxHeight().width(420.dp).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val live = g.gameState == GameState.LIVE
                    Text(
                        if (live) "LIVE" else Fmt.whenLabel(g, zone, settings.use24h), fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tabular,
                        color = if (live) Color.White else Color(0xFF111215),
                        modifier = Modifier.background(if (live) Neon.Live else Color(0xFFF2F1EE), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Text(g.league, fontSize = 15.sp, color = Neon.TextDim, maxLines = 1)
                    if (settings.isFavourite(g)) Text("Your team", fontSize = 15.sp, color = Neon.Purple, maxLines = 1)
                }
                Spacer(Modifier.height(14.dp))
                Text(g.matchup.replace(" vs ", " v ").uppercase(), fontFamily = SairaCondensed, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 42.sp)
                Spacer(Modifier.height(6.dp))
                Text(posterSubtitle(g, zone, settings.use24h), fontSize = 17.sp, color = Neon.TextDim, style = Tabular, maxLines = 1)
                Spacer(Modifier.height(18.dp))
                Text(if (g.gameState == GameState.LIVE) "Select to watch" else "Select for streams", fontSize = 14.sp, color = Neon.TextFaint)
            }
        }
        if (side.isNotEmpty()) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            side.forEach { sg ->
                NeonSurface(onClick = { if (sg.gameState == GameState.LIVE) onWatch(sg) else onStreams(sg) }, modifier = Modifier.weight(1f).fillMaxWidth(), radius = 12.dp) {
                    MatchupArt(sg, Modifier.fillMaxSize(), logoSize = 52.dp)
                    PosterBadge(sg, zone, settings.use24h)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Column {
                            Text(sg.matchup.replace(" vs ", " v ").uppercase(), fontFamily = SairaCondensed, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(posterSubtitle(sg, zone, settings.use24h), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), style = Tabular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
