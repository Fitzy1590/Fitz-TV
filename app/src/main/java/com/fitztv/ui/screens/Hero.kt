package com.fitztv.ui.screens

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
import com.fitztv.ui.components.Tabular
import com.fitztv.ui.components.posterSubtitle
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.delay

/**
 * Home hero: your teams' and the biggest games as a large poster — team fanart behind, the
 * fixture's matchup art on the right, Watch / Streams on the left. Advances every 8 s until
 * focused; ◀ ▶ at the ends of the button row move between games.
 */
@Composable
fun HeroCarousel(
    items: List<Game>,
    settings: AppSettings,
    onWatch: (Game) -> Unit,
    onStreams: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val g = items[index.mod(items.size)]
    LaunchedEffect(items.size, focused) {
        while (!focused && items.size > 1) { delay(8_000); index = (index + 1).mod(items.size) }
    }
    val zone = settings.zone()
    val repo = FitzApp.instance.repo
    val fanTeam = when {
        settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.home, g.sport) } -> g.home
        settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.away, g.sport) } -> g.away
        else -> g.home
    }
    val fanart by produceState<String?>(null, fanTeam) { value = runCatching { repo.logos.fanart(fanTeam).firstOrNull() }.getOrNull() }

    Box(modifier.fillMaxWidth().height(360.dp).padding(horizontal = 48.dp).clip(RoundedCornerShape(16.dp)).background(Neon.Bg2)) {
        fanart?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f) }
        // Matchup art on the right two thirds.
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().aspectRatio(16f / 9f)) {
            MatchupArt(g, Modifier.fillMaxSize(), logoSize = 120.dp)
        }
        // Legibility scrim for the text on the left (functional, not decoration).
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Neon.Bg2, 0.42f to Neon.Bg2.copy(alpha = 0.92f), 0.62f to Color.Transparent)))
        Column(Modifier.fillMaxHeight().width(560.dp).padding(40.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val live = g.gameState == GameState.LIVE
                Text(
                    if (live) "LIVE" else Fmt.whenLabel(g, zone, settings.use24h), fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tabular,
                    color = if (live) Color.White else Color(0xFF111215),
                    modifier = Modifier.background(if (live) Neon.Live else Color(0xFFF2F1EE), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(g.league, fontSize = 15.sp, color = Neon.TextDim)
                if (settings.isFavourite(g)) Text("Your team", fontSize = 15.sp, color = Neon.Purple)
            }
            Spacer(Modifier.height(16.dp))
            Text(g.matchup.replace(" vs ", " v "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(posterSubtitle(g, zone, settings.use24h), fontSize = 18.sp, color = Neon.TextDim, style = Tabular, maxLines = 1)
            Spacer(Modifier.height(24.dp))
            var btn by remember { mutableIntStateOf(0) }
            Row(
                Modifier
                    .onFocusChanged { focused = it.hasFocus }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.DirectionLeft && btn == 0 -> { index = (index - 1).mod(items.size); true }
                            e.key == Key.DirectionRight && btn == 1 -> { index = (index + 1).mod(items.size); true }
                            else -> false
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeonButton(if (g.gameState == GameState.LIVE) "Watch live" else "Streams", onClick = { if (g.gameState == GameState.LIVE) onWatch(g) else onStreams(g) },
                    selected = true, icon = Glyph.Play, modifier = Modifier.onFocusChanged { if (it.isFocused) btn = 0 })
                NeonButton("More streams", onClick = { onStreams(g) }, icon = Glyph.Tv, modifier = Modifier.onFocusChanged { if (it.isFocused) btn = 1 })
            }
        }
        // Page dots.
        if (items.size > 1) Row(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.indices.forEach { i ->
                val on = i == index.mod(items.size)
                Box(Modifier.height(6.dp).width(if (on) 24.dp else 6.dp).clip(CircleShape).background(if (on) Neon.Text else Neon.TextFaint))
            }
        }
    }
}
