package com.fitztv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.data.Channel
import com.fitztv.data.EpgProgram
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.theme.Neon
import java.time.ZoneId

/**
 * Focusable card. Flat surface with a fine neutral outline; focus lifts the surface,
 * draws a 2dp accent ring and grows 3% so it reads from the sofa. No glow.
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
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Neon.SurfaceHi else Neon.Surface,
            contentColor = Neon.Text,
            focusedContainerColor = Neon.SurfaceHi,
            focusedContentColor = Neon.Text,
            pressedContainerColor = Neon.SurfaceHi,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Neon.Line), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Neon.Purple), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
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
    /** League / sport logo shown instead of [icon] when available. */
    iconUrl: String? = null,
) {
    NeonSurface(onClick = onClick, modifier = modifier, radius = 8.dp, selected = selected) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = if (selected) Neon.Purple else Neon.TextDim
            if (iconUrl != null) {
                AsyncImage(iconUrl, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (text.isNotEmpty()) Text(
                text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (selected) Neon.Text else Neon.TextDim,
            )
        }
    }
}

/** Small label. Neutral unless a meaning colour (live red) is passed. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(Neon.Live))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, live: Boolean = false, icon: ImageVector? = null) {
    Row(modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live) { LiveDot(); Spacer(Modifier.width(12.dp)) }
        if (icon != null) { Icon(icon, null, tint = Neon.TextDim, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
    }
}

@Composable
fun TeamLogo(url: String?, fallback: String, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Neon.Bg2),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.8f))
        } else {
            Text(fallback.take(3).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
        }
    }
}

@Composable
fun GameCard(
    game: Game,
    zone: ZoneId,
    use24h: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    favourite: Boolean = false,
) {
    NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(300.dp).height(152.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favourite) { Icon(Glyph.Star, "My team", tint = Neon.Purple, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
                Text(game.league, color = Neon.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                when (game.gameState) {
                    GameState.LIVE -> LiveClock(game.statusText)
                    GameState.FINAL -> Text(game.statusText.ifBlank { "Final" }, color = Neon.TextFaint, fontSize = 13.sp, maxLines = 1)
                    GameState.PRE -> Text(Fmt.whenLabel(game, zone, use24h), color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = Tabular)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (game.home.isBlank()) {
                Text(game.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else if (game.gameState == GameState.LIVE) {
                Scorebug(game)
            } else {
                val showScore = game.gameState != GameState.PRE
                val lead = leader(game)
                TeamLine(game.awayLogo, game.away, game.awayShort, game.awayScore.takeIf { showScore }, dim = showScore && lead == 1)
                Spacer(Modifier.height(8.dp))
                TeamLine(game.homeLogo, game.home, game.homeShort, game.homeScore.takeIf { showScore }, dim = showScore && lead == -1)
            }
        }
    }
}

/** -1 = away leads, 1 = home leads, 0 = level / unknown. */
private fun leader(g: Game): Int {
    val a = g.awayScore?.trim()?.toIntOrNull() ?: return 0
    val h = g.homeScore?.trim()?.toIntOrNull() ?: return 0
    return a.compareTo(h).let { -it }.coerceIn(-1, 1)
}

/** Red live tick and the game clock in monospaced figures. */
@Composable
fun LiveClock(status: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LiveDot(); Spacer(Modifier.width(8.dp))
        Text(status.ifBlank { "Live" }, color = Neon.Live, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = Clock)
    }
}

/** The signature: crest · score – score · crest, like a broadcast scorebug. Live games only. */
@Composable
fun Scorebug(game: Game, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(game.awayLogo, game.awayShort.ifBlank { game.away }, 44.dp)
            Spacer(Modifier.weight(1f))
            Text(game.awayScore ?: "0", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
            Text("–", fontSize = 22.sp, color = Neon.TextFaint, modifier = Modifier.padding(horizontal = 12.dp))
            Text(game.homeScore ?: "0", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
            Spacer(Modifier.weight(1f))
            TeamLogo(game.homeLogo, game.homeShort.ifBlank { game.home }, 44.dp)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(game.awayShort.ifBlank { game.away }, fontSize = 13.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(game.homeShort.ifBlank { game.home }, fontSize = 13.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun TeamLine(logo: String?, name: String, short: String, score: String?, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(logo, short.ifBlank { name })
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (dim) Neon.TextDim else Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (score != null) Text(score, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (dim) Neon.TextDim else Neon.Text, style = Tabular)
    }
}

@Composable
fun ChannelCard(channel: Channel, now: EpgProgram?, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    NeonSurface(onClick = onClick, modifier = modifier.width(240.dp).height(120.dp), selected = selected) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Neon.Bg2), contentAlignment = Alignment.Center) {
                    if (channel.logo != null) AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp))
                    else Icon(Glyph.sport(channel.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(Fmt.quality(channel.quality), color = if (channel.quality >= 3) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(channel.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                now?.title ?: channel.group, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Text(body, fontSize = 15.sp, color = Neon.TextDim)
    }
}
