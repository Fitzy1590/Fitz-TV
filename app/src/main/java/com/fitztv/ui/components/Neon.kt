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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.data.Channel
import com.fitztv.data.EpgProgram
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.theme.Neon
import java.time.ZoneId

/** Focusable card with the neon glow treatment. */
@Composable
fun NeonSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    radius: Dp = 14.dp,
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
            focusedContentColor = Color.White,
            pressedContainerColor = Neon.PurpleSoft,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) Neon.Purple else Neon.Purple.copy(alpha = 0.25f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Neon.Purple), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Neon.Purple, elevation = 18.dp)),
        content = content,
    )
}

@Composable
fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    NeonSurface(onClick = onClick, modifier = modifier, radius = 50.dp, selected = selected) {
        Text(
            text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Neon.TextDim,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(Neon.Live))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 48.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Neon.glowBar))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
    }
}

@Composable
private fun TeamLogo(url: String?, fallback: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Neon.Bg2),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
        } else {
            Text(fallback.take(3).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Neon.TextDim)
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
    NeonSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(300.dp).height(150.dp), selected = favourite) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favourite) { Text("★", color = Neon.Magenta, fontSize = 14.sp); Spacer(Modifier.width(6.dp)) }
                Pill(game.league.uppercase(), Neon.Purple)
                Spacer(Modifier.weight(1f))
                when (game.gameState) {
                    GameState.LIVE -> {
                        LiveDot(); Spacer(Modifier.width(5.dp))
                        Text(game.statusText.ifBlank { "LIVE" }, color = Neon.Live, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    GameState.FINAL -> Text(game.statusText.ifBlank { "Final" }, color = Neon.TextFaint, fontSize = 12.sp, maxLines = 1)
                    GameState.PRE -> Text(Fmt.whenLabel(game, zone, use24h), color = Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (game.home.isNotBlank()) {
                TeamLine(game.awayLogo, game.away, game.awayShort, game.awayScore.takeIf { game.gameState != GameState.PRE })
                Spacer(Modifier.height(6.dp))
                TeamLine(game.homeLogo, game.home, game.homeShort, game.homeScore.takeIf { game.gameState != GameState.PRE })
            } else {
                Text(game.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TeamLine(logo: String?, name: String, short: String, score: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(logo, short.ifBlank { name })
        Spacer(Modifier.width(10.dp))
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (score != null) Text(score, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
fun ChannelCard(channel: Channel, now: EpgProgram?, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    NeonSurface(onClick = onClick, modifier = modifier.width(240.dp).height(120.dp), selected = selected) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Neon.Bg2), contentAlignment = Alignment.Center) {
                    if (channel.logo != null) AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp))
                    else Text(channel.sport.emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.weight(1f))
                Pill(Fmt.quality(channel.quality), if (channel.quality >= 3) Neon.Cyan else Neon.TextDim)
            }
            Spacer(Modifier.height(8.dp))
            Text(channel.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                now?.title ?: channel.group, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
        Text(body, fontSize = 15.sp, color = Neon.TextDim)
    }
}
