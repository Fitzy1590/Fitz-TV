package com.fitztv.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.delay
import java.time.ZoneId

fun teamColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull() } ?: fallback

/** Two team colours that are too alike (Pacers v Mavericks) blur into one block: darken the home side. */
private fun apart(a: Color, b: Color): Color {
    val d = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
    return if (d > 0.5f) b else androidx.compose.ui.graphics.lerp(b, Color.Black, 0.55f)
}

/** Team colours on a diagonal with both logos, or the fixture's thumbnail when there is one. */
@Composable
fun MatchupArt(game: Game, modifier: Modifier = Modifier, useThumb: Boolean = true, logoSize: Dp = 64.dp) {
    // One style for every game: the two team colours split on a diagonal (no fixture banners).
    val away = teamColor(game.awayColor, Color(0xFF1C2230))
    val home = apart(away, teamColor(game.homeColor, Color(0xFF2A2230)))
    Box(modifier.background(Neon.Bg2)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(home)
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.58f, 0f); lineTo(w * 0.42f, h); lineTo(0f, h); close() }, away)
            drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, h * 0.72f))
        }
        if (game.home.isNotBlank()) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ArtLogo(game.awayLogo, game.awayShort.ifBlank { game.away }, logoSize) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ArtLogo(game.homeLogo, game.homeShort.ifBlank { game.home }, logoSize) }
            }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxSize().background(Neon.SurfaceHi).padding(16.dp), verticalArrangement = Arrangement.Center) {
                Text(game.league, fontSize = 12.sp, color = Neon.Purple, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(game.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtLogo(url: String?, fallback: String, size: Dp) {
    // A soft dark plate keeps a red logo readable on a red card, and gives flags a consistent frame.
    Box(Modifier.size(size * 1.5f).background(Color.Black.copy(alpha = 0.24f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size(size), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = (size.value / 3).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun BoxScope.PosterBadge(game: Game, zone: ZoneId, use24h: Boolean) {
    val live = game.gameState == GameState.LIVE
    val label = when (game.gameState) { GameState.LIVE -> "LIVE"; GameState.FINAL -> "Final"; GameState.PRE -> Fmt.whenLabel(game, zone, use24h) }
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Tabular, color = if (live) Color.White else Color(0xFF111215),
        modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(if (live) Neon.Live else Color(0xFFF2F1EE), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
}

fun posterSubtitle(g: Game): String = buildList {
    add(g.league)
    if (g.gameState != GameState.PRE && g.home.isNotBlank() && (g.awayScore != null || g.homeScore != null)) add("${g.awayScore ?: 0}–${g.homeScore ?: 0}")
    when (g.gameState) { GameState.LIVE -> add(g.statusText.ifBlank { "Live" }); GameState.FINAL -> add(g.statusText.ifBlank { "Final" }); else -> Unit }
}.joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(game: Game, settings: AppSettings, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 280.dp) {
    val zone = settings.zone()
    Column(modifier.width(width).combinedClickable(onClick = onClick, onLongClick = onLongClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            MatchupArt(game, Modifier.fillMaxSize(), logoSize = width * 0.22f)
            PosterBadge(game, zone, settings.use24h)
            if (settings.isFavourite(game)) Icon(Glyph.Star, "My team", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(16.dp))
        }
        Text(game.matchup.replace(" vs ", " v ").uppercase(), fontFamily = SairaCondensed, color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(posterSubtitle(game), color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, fontSize = 12.sp, maxLines = 1, style = Tabular)
    }
}

@Composable
fun ChannelTile(label: String, logo: String?, nowTitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 220.dp) {
    Column(modifier.width(width).combinedClickableCompat(onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14233F)), contentAlignment = Alignment.Center) {
            if (logo != null) Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.56f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F7)), contentAlignment = Alignment.Center) {
                AsyncImage(logo, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            }
            else Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            Text("LIVE TV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Neon.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(label, color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(nowTitle ?: "Live", color = Neon.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = combinedClickable(onClick = onClick)

/** Swipeable hero: matchup art over team fanart, Watch / Streams, auto-advances every 8 s. */
@Composable
fun HeroPager(items: List<Game>, settings: AppSettings, onWatch: (Game) -> Unit, onStreams: (Game) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    LaunchedEffect(items.size) {
        while (items.size > 1) { delay(8_000); pager.animateScrollToPage((pager.currentPage + 1) % items.size) }
    }
    Column(modifier) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { i ->
            val g = items[i]
            val repo = FitzApp.instance.repo
            val team = if (settings.favoriteTeams.any { com.fitztv.data.isTeam(it, g.away, g.sport) }) g.away else g.home
            val fanart by produceState<String?>(null, team) { value = runCatching { repo.logos.fanart(team).firstOrNull() }.getOrNull() }
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Neon.Bg2)) {
                    MatchupArt(g, Modifier.fillMaxSize(), logoSize = 96.dp)
                    fanart?.takeIf { repo.art.thumb(g) == null }?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                    PosterBadge(g, settings.zone(), settings.use24h)
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(g.matchup.replace(" vs ", " v ").uppercase(), fontFamily = SairaCondensed, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(posterSubtitle(g), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, style = Tabular)
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { if (g.gameState == GameState.LIVE) onWatch(g) else onStreams(g) },
                        colors = ButtonDefaults.buttonColors(containerColor = Neon.Purple, contentColor = Neon.Bg)) {
                        Icon(Glyph.Play, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (g.gameState == GameState.LIVE) "Watch live" else "Streams")
                    }
                    OutlinedButton(onClick = { onStreams(g) }) { Text("More streams", color = Neon.Text) }
                }
            }
        }
        if (items.size > 1) Row(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(items.size) { i ->
                val on = i == pager.currentPage
                Box(Modifier.height(6.dp).width(if (on) 20.dp else 6.dp).clip(CircleShape).background(if (on) Neon.Text else Neon.TextFaint))
            }
        }
    }
}
