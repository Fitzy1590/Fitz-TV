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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
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

private val CardShape = RoundedCornerShape(12.dp)

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
            .border(1.dp, Neon.Line, CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) { content() }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun LiveDot() = Box(Modifier.size(8.dp).clip(CircleShape).background(Neon.Live))

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, live: Boolean = false, icon: ImageVector? = null) {
    Row(modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live) { LiveDot(); Spacer(Modifier.width(8.dp)) }
        if (icon != null) { Icon(icon, null, tint = Neon.TextDim, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text.uppercase(), fontFamily = SairaCondensed, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
    }
}

@Composable
fun Logo(url: String?, fallback: String, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Neon.Bg2), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.size((size * 0.8).dp), contentScale = ContentScale.Fit)
        else Text(fallback.take(3).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
    }
}

@Composable
fun GameCard(game: Game, settings: AppSettings, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val zone = settings.zone()
    val fav = settings.isFavourite(game)
    NeonCard(onClick, modifier.fillMaxWidth(), onLongClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) { Icon(Glyph.Star, "My team", tint = Neon.Purple, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
                Text(game.league, color = Neon.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                when (game.gameState) {
                    GameState.LIVE -> LiveClock(game.statusText)
                    GameState.FINAL -> Text(game.statusText.ifBlank { "Final" }, color = Neon.TextFaint, fontSize = 13.sp, maxLines = 1)
                    GameState.PRE -> Text(Fmt.whenLabel(game, zone, settings.use24h), color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = Tabular)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (game.home.isBlank()) {
                Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Neon.Text)
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
    return (-a.compareTo(h)).coerceIn(-1, 1)
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
            Logo(game.awayLogo, game.awayShort.ifBlank { game.away }, 40)
            Spacer(Modifier.weight(1f))
            Text(game.awayScore ?: "0", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
            Text("–", fontSize = 20.sp, color = Neon.TextFaint, modifier = Modifier.padding(horizontal = 12.dp))
            Text(game.homeScore ?: "0", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
            Spacer(Modifier.weight(1f))
            Logo(game.homeLogo, game.homeShort.ifBlank { game.home }, 40)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(game.away, fontSize = 13.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(game.home, fontSize = 13.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TeamLine(logo: String?, name: String, short: String, score: String?, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo(logo, short.ifBlank { name })
        Spacer(Modifier.width(12.dp))
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = if (dim) Neon.TextDim else Neon.Text)
        if (score != null) Text(score, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = if (dim) Neon.TextDim else Neon.Text, style = Tabular)
    }
}

@Composable
fun ChannelCard(channel: Channel, now: EpgProgram?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeonCard(onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp)).background(Neon.Bg2), contentAlignment = Alignment.Center) {
                if (channel.logo != null) AsyncImage(channel.logo, null, Modifier.padding(4.dp), contentScale = ContentScale.Fit)
                else Icon(Glyph.sport(channel.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Neon.Text)
                Text(now?.title ?: channel.group, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(Fmt.quality(channel.quality), color = if (channel.quality >= 3) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun FavouriteCard(e: FavouriteEntry, settings: AppSettings, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val zone = settings.zone()
    NeonCard(onClick, modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(e.logo, e.team, 40)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(e.team, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Neon.Text)
                val g = e.game
                if (g == null) Text("No games in the next 8 days", fontSize = 12.sp, color = Neon.TextFaint)
                else {
                    val isHome = com.fitztv.data.isTeam(e.team, g.home, g.sport)
                    val opp = if (isHome) g.awayShort.ifBlank { g.away } else g.homeShort.ifBlank { g.home }
                    val vs = if (isHome) "vs $opp" else "@ $opp"
                    val us = if (isHome) g.homeScore else g.awayScore
                    val them = if (isHome) g.awayScore else g.homeScore
                    when (g.gameState) {
                        GameState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(8.dp))
                            Text("${us ?: ""}–${them ?: ""} $vs · ${g.statusText}", fontSize = 12.sp, color = Neon.Live, style = Tabular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        GameState.PRE -> Text("$vs · ${Fmt.whenLabel(g, zone, settings.use24h)}", fontSize = 12.sp, color = Neon.TextDim, maxLines = 1)
                        GameState.FINAL -> Text("Final ${us ?: ""}–${them ?: ""} $vs", fontSize = 12.sp, color = Neon.TextFaint, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 24.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, color = Neon.TextDim)
    }
}
