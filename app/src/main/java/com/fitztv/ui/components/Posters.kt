package com.fitztv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.theme.Neon
import java.time.ZoneId

/** Team colour from the feed ("0080c6"), or a neutral fallback. */
fun teamColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull() } ?: fallback

/**
 * Matchup art for any game: the two teams' colours split on a diagonal with both logos —
 * or the fixture's own thumbnail when TheSportsDB has one (drawn over the art as it loads).
 */
@Composable
fun MatchupArt(game: Game, modifier: Modifier = Modifier, useThumb: Boolean = true, logoSize: Dp = 72.dp) {
    val art = FitzApp.instance.repo.art
    val v by art.version.collectAsState()
    val thumb = remember(game.id, v) { if (useThumb) art.thumb(game) else null }
    val away = teamColor(game.awayColor, Color(0xFF1C2230))
    val home = teamColor(game.homeColor, Color(0xFF2A2230))
    Box(modifier.background(Neon.Bg2)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(home)
            val left = Path().apply {
                moveTo(0f, 0f); lineTo(w * 0.58f, 0f); lineTo(w * 0.42f, h); lineTo(0f, h); close()
            }
            drawPath(left, away)
            // A soft floor so logos and badges read on any team colour.
            drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, h * 0.72f))
        }
        if (game.home.isNotBlank()) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Logo(game.awayLogo, game.awayShort.ifBlank { game.away }, logoSize) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Logo(game.homeLogo, game.homeShort.ifBlank { game.home }, logoSize) }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Neon.SurfaceHi).padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(game.league, fontSize = 14.sp, color = Neon.Purple, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(game.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Logo(url: String?, fallback: String, size: Dp) {
    // A soft dark plate keeps a red logo readable on a red card, and gives flags a consistent frame.
    Box(Modifier.size(size * 1.5f).background(Color.Black.copy(alpha = 0.24f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size(size), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = (size.value / 3).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** LIVE (red) or the start time (light), top-left on a poster. */
@Composable
fun BoxScope.PosterBadge(game: Game, zone: ZoneId, use24h: Boolean) {
    val live = game.gameState == GameState.LIVE
    val label = when (game.gameState) {
        GameState.LIVE -> "LIVE"
        GameState.FINAL -> "Final"
        GameState.PRE -> Fmt.whenLabel(game, zone, use24h)
    }
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = Tabular,
        color = if (live) Color.White else Color(0xFF111215),
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            .background(if (live) Neon.Live else Color(0xFFF2F1EE), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** One-line status under a poster: league · score · clock. */
fun posterSubtitle(g: Game, zone: ZoneId, use24h: Boolean): String = buildList {
    add(g.league)
    if (g.gameState != GameState.PRE && g.home.isNotBlank() && (g.awayScore != null || g.homeScore != null)) add("${g.awayScore ?: 0}–${g.homeScore ?: 0}")
    when (g.gameState) {
        GameState.LIVE -> add(g.statusText.ifBlank { "Live" })
        GameState.FINAL -> add(g.statusText.ifBlank { "Final" })
        GameState.PRE -> Unit
    }
}.joinToString(" · ")

/** 16:9 matchup poster with the title and league underneath (Kayo-style row item). */
@Composable
fun PosterCard(
    game: Game, zone: ZoneId, use24h: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, favourite: Boolean = false, width: Dp = 320.dp,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12.dp) {
            MatchupArt(game, Modifier.fillMaxSize(), logoSize = width * 0.22f)
            PosterBadge(game, zone, use24h)
            if (favourite) androidx.tv.material3.Icon(Glyph.Star, "My team", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp))
        }
        Text(game.matchup.replace(" vs ", " v "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(posterSubtitle(game, zone, use24h), fontSize = 13.sp, color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Tabular)
    }
}

/** Live channel tile: the channel's logo on a dark tile, LIVE TV badge, what's on underneath. */
@Composable
fun ChannelTile(label: String, logo: String?, nowTitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 260.dp) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12.dp) {
            Box(Modifier.fillMaxSize().background(Color(0xFF14233F)), contentAlignment = Alignment.Center) {
                if (logo != null) Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.56f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F7)), contentAlignment = Alignment.Center) {
                    AsyncImage(logo, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
                else Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, modifier = Modifier.padding(16.dp))
            }
            Text("LIVE TV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Neon.Live, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(nowTitle ?: "Live", fontSize = 13.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Channel to show for a quick-channel / live-channel tile: its first stream. */
fun Channel.tileLogo(): String? = logo
