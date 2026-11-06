package com.fitztv.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitztv.data.AppSettings
import com.fitztv.data.Channel
import com.fitztv.data.EpgProgram
import com.fitztv.data.FavouriteEntry
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeonCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(CardShape)
            .background(if (highlighted) Neon.SurfaceHi else Neon.Surface)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) Neon.Purple else Neon.Purple.copy(alpha = 0.25f), CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) { content() }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun LiveDot() = Box(Modifier.size(8.dp).clip(CircleShape).background(Neon.Live))

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Neon.glowBar))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
    }
}

@Composable
fun Logo(url: String?, fallback: String, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Neon.Bg2), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size((size * 0.8).dp), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Neon.TextDim)
    }
}

@Composable
fun GameCard(game: Game, settings: AppSettings, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val zone = settings.zone()
    val fav = settings.isFavourite(game)
    NeonCard(onClick, modifier.fillMaxWidth(), onLongClick, highlighted = fav) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) { Text("★", color = Neon.Magenta, fontSize = 14.sp); Spacer(Modifier.width(6.dp)) }
                Pill(game.league.uppercase(), Neon.Purple)
                Spacer(Modifier.weight(1f))
                when (game.gameState) {
                    GameState.LIVE -> {
                        LiveDot(); Spacer(Modifier.width(5.dp))
                        Text(game.statusText.ifBlank { "LIVE" }, color = Neon.Live, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    GameState.FINAL -> Text(game.statusText.ifBlank { "Final" }, color = Neon.TextFaint, fontSize = 12.sp)
                    GameState.PRE -> Text(Fmt.whenLabel(game, zone, settings.use24h), color = Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (game.home.isNotBlank()) {
                TeamLine(game.awayLogo, game.away, game.awayShort, game.awayScore.takeIf { game.gameState != GameState.PRE })
                Spacer(Modifier.height(6.dp))
                TeamLine(game.homeLogo, game.home, game.homeShort, game.homeScore.takeIf { game.gameState != GameState.PRE })
            } else {
                Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TeamLine(logo: String?, name: String, short: String, score: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo(logo, short.ifBlank { name })
        Spacer(Modifier.width(10.dp))
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = Neon.Text)
        if (score != null) Text(score, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
fun ChannelCard(channel: Channel, now: EpgProgram?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeonCard(onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(Neon.Bg2), contentAlignment = Alignment.Center) {
                if (channel.logo != null) AsyncImage(channel.logo, null, Modifier.padding(3.dp), contentScale = ContentScale.Fit)
                else Text(channel.sport.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Neon.Text)
                Text(now?.title ?: channel.group, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill(Fmt.quality(channel.quality), if (channel.quality >= 3) Neon.Cyan else Neon.TextDim)
        }
    }
}

@Composable
fun FavouriteCard(e: FavouriteEntry, settings: AppSettings, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val zone = settings.zone()
    NeonCard(onClick, modifier) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(e.logo, e.team, 40)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(e.team, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Neon.Text)
                val g = e.game
                if (g == null) Text("No games this week", fontSize = 12.sp, color = Neon.TextFaint)
                else {
                    val isHome = g.home.equals(e.team, true)
                    val opp = if (isHome) g.awayShort.ifBlank { g.away } else g.homeShort.ifBlank { g.home }
                    val vs = if (isHome) "vs $opp" else "@ $opp"
                    val us = if (isHome) g.homeScore else g.awayScore
                    val them = if (isHome) g.awayScore else g.homeScore
                    when (g.gameState) {
                        GameState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(5.dp))
                            Text("${us ?: ""}-${them ?: ""} $vs • ${g.statusText}", fontSize = 12.sp, color = Neon.Live, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GameState.PRE -> Text("$vs • ${Fmt.whenLabel(g, zone, settings.use24h)}", fontSize = 12.sp, color = Neon.Cyan, maxLines = 1)
                        GameState.FINAL -> Text("Final ${us ?: ""}-${them ?: ""} $vs", fontSize = 12.sp, color = Neon.TextFaint, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 24.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, color = Neon.TextDim)
    }
}
