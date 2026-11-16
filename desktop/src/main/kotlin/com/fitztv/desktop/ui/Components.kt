@file:OptIn(ExperimentalFoundationApi::class)

package com.fitztv.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.PointerMatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fitztv.data.Channel
import com.fitztv.data.EpgProgram
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Focusable card. Flat surface with a fine outline; hover or keyboard focus lifts it, draws a 2dp
 * accent ring and grows 3%. Right-click (or long-press) is the secondary action, like holding OK on the TV.
 */
@Composable
fun NeonSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    radius: Dp = 12.dp,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val lit = hovered || focused
    val scale by animateFloatAsState(if (lit) 1.03f else 1f)
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (selected || lit) Neon.SurfaceHi else Neon.Surface, shape)
            .border(if (focused) BorderStroke(2.dp, Neon.Purple) else BorderStroke(1.dp, if (hovered) Neon.TextFaint else Neon.Line), shape)
            .onFocusChanged { focused = it.isFocused }
            .hoverable(source)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
            .then(if (onLongClick != null) Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { onLongClick() } else Modifier)
            .focusable(),
        content = content,
    )
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    /** League / sport logo shown instead of [icon]. */
    iconUrl: String? = null,
) {
    NeonSurface(onClick = onClick, modifier = modifier, radius = 8.dp, selected = selected) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (selected) Neon.Purple else Neon.TextDim
            if (iconUrl != null) {
                Box(Modifier.size(24.dp).background(Color(0xFFF3F4F7), CircleShape), contentAlignment = Alignment.Center) {
                    AsyncImage(iconUrl, null, Modifier.size(19.dp), contentScale = ContentScale.Fit)
                }
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (text.isNotEmpty()) Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) Neon.Text else Neon.TextDim, maxLines = 1)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) { Box(modifier.size(8.dp).clip(CircleShape).background(Neon.Live)) }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, live: Boolean = false, icon: ImageVector? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = PAD, end = PAD, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live) { LiveDot(); Spacer(Modifier.width(12.dp)) }
        if (icon != null) { Icon(icon, null, tint = Neon.TextDim, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Text(text.uppercase(), fontFamily = SairaCondensed, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Page side margin. */
val PAD = 40.dp

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(PAD), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Text(body, fontSize = 15.sp, color = Neon.TextDim)
    }
}

/**
 * A horizontal row of cards. There is no D-pad on a PC, so it scrolls with the mouse wheel
 * (shift or trackpad), by dragging, and with the arrows at the right of the title.
 */
@Composable
fun CardRow(modifier: Modifier = Modifier, spacing: Dp = 16.dp, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = PAD), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
        if (state.canScrollBackward) ScrollArrow("‹", Alignment.CenterStart) { scope.launch { state.animateScrollBy(-700f) } }
        if (state.canScrollForward) ScrollArrow("›", Alignment.CenterEnd) { scope.launch { state.animateScrollBy(700f) } }
    }
}

@Composable
private fun BoxScope.ScrollArrow(glyph: String, at: Alignment, onClick: () -> Unit) {
    Box(
        Modifier.align(at).padding(horizontal = 6.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
            .border(1.dp, Neon.Line, CircleShape).combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 26.sp, color = Neon.Text) }
}

@Composable
fun TeamLogo(url: String?, fallback: String, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Neon.Bg2), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size(size * 0.8f), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
    }
}

// ---- Matchup posters (style D "stadium poster") ----------------------------------------------

/** Team colour from the feed ("0080c6"), or a neutral fallback. */
fun teamColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(("FF" + it.removePrefix("#")).toLong(16)) }.getOrNull() } ?: fallback

/** Two team colours that are too alike blur into one block: darken the home side. */
private fun apart(a: Color, b: Color): Color {
    val d = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
    return if (d > 0.5f) b else androidx.compose.ui.graphics.lerp(b, Color.Black, 0.55f)
}

/** The two teams' colours split on a diagonal with both logos on soft plates. */
@Composable
fun MatchupArt(game: Game, modifier: Modifier = Modifier, logoSize: Dp = 72.dp) {
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
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PosterLogo(game.awayLogo, game.awayShort.ifBlank { game.away }, logoSize) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PosterLogo(game.homeLogo, game.homeShort.ifBlank { game.home }, logoSize) }
            }
        } else {
            Column(Modifier.fillMaxSize().background(Neon.SurfaceHi).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(game.league, fontSize = 14.sp, color = Neon.Purple, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(game.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PosterLogo(url: String?, fallback: String, size: Dp) {
    Box(Modifier.size(size * 1.5f).background(Color.Black.copy(alpha = 0.24f), CircleShape), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size(size), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = (size.value / 3).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun BoxScope.PosterBadge(game: Game, zone: ZoneId, use24h: Boolean) {
    val live = game.gameState == GameState.LIVE
    val label = when (game.gameState) {
        GameState.LIVE -> "LIVE"
        GameState.FINAL -> "Final"
        GameState.PRE -> Fmt.whenLabel(game, zone, use24h)
    }
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = Tabular, color = if (live) Color.White else Color(0xFF111215),
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(if (live) Neon.Live else Color(0xFFF2F1EE), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

fun posterSubtitle(g: Game): String = buildList {
    add(g.league)
    if (g.gameState != GameState.PRE && g.home.isNotBlank() && (g.awayScore != null || g.homeScore != null)) add("${g.awayScore ?: 0}–${g.homeScore ?: 0}")
    when (g.gameState) {
        GameState.LIVE -> add(g.statusText.ifBlank { "Live" })
        GameState.FINAL -> add(g.statusText.ifBlank { "Final" })
        GameState.PRE -> Unit
    }
}.joinToString(" · ")

fun Game.vsTitle() = matchup.replace(" vs ", " v ").uppercase()

/** 16:9 matchup poster with the title and league underneath. */
@Composable
fun PosterCard(
    game: Game, zone: ZoneId, use24h: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, favourite: Boolean = false, width: Dp = 300.dp,
) {
    val repo = com.fitztv.FitzApp.instance.repo
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12.dp) {
            MatchupArt(game, Modifier.fillMaxSize(), logoSize = width * 0.22f)
            PosterBadge(game, zone, use24h)
            if (favourite) Icon(Glyph.Star, "My team", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp))
        }
        Text(game.vsTitle(), fontFamily = SairaCondensed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(posterSubtitle(game), fontSize = 13.sp, color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Tabular)
    }
}

/** Live channel tile: the channel's logo on a light plate, LIVE TV badge, what's on underneath. */
@Composable
fun ChannelTile(label: String, logo: String?, nowTitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 240.dp) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 12.dp) {
            Box(Modifier.fillMaxSize().background(Color(0xFF14233F)), contentAlignment = Alignment.Center) {
                if (logo != null) Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.56f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F7)), contentAlignment = Alignment.Center) {
                    AsyncImage(logo, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                } else Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, modifier = Modifier.padding(16.dp))
            }
            Text("LIVE TV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Neon.Live, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(nowTitle ?: "Live", fontSize = 13.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ChannelCard(channel: Channel, now: EpgProgram?, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    NeonSurface(onClick = onClick, modifier = modifier.width(240.dp).height(120.dp), selected = selected) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF3F4F7).copy(alpha = if (channel.logo != null) 1f else 0.0f)), contentAlignment = Alignment.Center) {
                    if (channel.logo != null) AsyncImage(channel.logo, null, Modifier.padding(4.dp), contentScale = ContentScale.Fit)
                    else Icon(Glyph.sport(channel.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(Fmt.quality(channel.quality), color = if (channel.quality >= 3) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(channel.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.title ?: channel.group, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Generic card for a finished or upcoming game (replays, search). */
@Composable
fun GameCard(game: Game, zone: ZoneId, use24h: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, favourite: Boolean = false) =
    PosterCard(game, zone, use24h, onClick, modifier, onLongClick, favourite)

@Composable
fun StatusBadge(live: Boolean, text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
        modifier = Modifier.background(if (live) Neon.Live else Neon.TextFaint, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp), textAlign = TextAlign.Center)
}
