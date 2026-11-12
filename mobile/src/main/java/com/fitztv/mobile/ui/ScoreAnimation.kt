package com.fitztv.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.sports.ScoreEvent
import com.fitztv.sports.ScoreWatcher

import kotlinx.coroutines.delay

private val Fire = Color(0xFFFF7A2A)
private val FireGold = Color(0xFFFFD24A)

/**
 * A short card when someone scores: points, name, current stats; a flame and a longer stay when
 * they're on fire. Only the window you're watching or hearing shows it.
 */
@Composable
fun ScoreAnimationHost(game: Game?, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (game == null) return
    val settings by FitzApp.instance.settings.state.collectAsState()
    if (!settings.scoreAnimations) return
    var shown by remember(game.id) { mutableStateOf<ScoreEvent?>(null) }
    var visible by remember(game.id) { mutableStateOf(false) }
    LaunchedEffect(game.id, settings.scoreDelaySeconds) {
        ScoreWatcher.events(game, settings.scoreDelaySeconds * 1000L).collect { ev ->
            shown = ev; visible = true
            delay(if (ev.hot) 3_300 else 2_300)
            visible = false
            delay(350)
        }
    }
    AnimatedVisibility(
        visible, modifier = modifier,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) { shown?.let { ScoreCard(it, compact) } }
}

@Composable
private fun ScoreCard(e: ScoreEvent, compact: Boolean) {
    val team = e.teamColor?.let { hex -> runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrNull() } ?: Neon.Purple
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    Row(
        Modifier.background(Neon.Bg.copy(alpha = 0.9f), shape)
            .border(if (e.hot) 2.dp else 1.dp, if (e.hot) Fire else team.copy(alpha = 0.8f), shape)
            .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val size = if (compact) 34.dp else 56.dp
        Box(Modifier.size(size).clip(CircleShape).background(team.copy(alpha = 0.35f)).border(2.dp, team, CircleShape), contentAlignment = Alignment.Center) {
            val pic = e.headshot ?: e.teamLogo
            if (pic != null) AsyncImage(pic, null, Modifier.size(size), contentScale = if (e.headshot != null) ContentScale.Crop else ContentScale.Fit)
            else Text(e.team.take(3), color = Color.White, fontSize = (size.value / 3).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.headline, color = if (e.hot) FireGold else Neon.Purple, fontFamily = SairaCondensed, fontWeight = FontWeight.Bold, fontSize = if (compact) 18.sp else 30.sp)
                Spacer(Modifier.width(8.dp))
                Text(e.name.uppercase(), color = Color.White, fontFamily = SairaCondensed, fontWeight = FontWeight.Bold, fontSize = if (compact) 15.sp else 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.hot) { Spacer(Modifier.width(8.dp)); Flame(if (compact) 16.dp else 24.dp); Spacer(Modifier.width(4.dp)); Text("ON FIRE", color = Fire, fontFamily = SairaCondensed, fontWeight = FontWeight.Bold, fontSize = if (compact) 12.sp else 16.sp) }
            }
            if (e.line.isNotBlank()) Text(e.line, color = Color(0xFFDDE5E7), fontSize = if (compact) 11.sp else 15.sp, maxLines = 1, style = Tabular)
        }
    }
}

/** A flickering flame: two overlapping drops, static. */
@Composable
private fun Flame(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        fun drop(top: Float, wide: Float, color: Color) {
            val p = Path().apply {
                moveTo(w * 0.5f, h * top)
                cubicTo(w * (0.5f + wide), h * (top + 0.3f), w * (0.5f + wide), h * 0.98f, w * 0.5f, h * 0.98f)
                cubicTo(w * (0.5f - wide), h * 0.98f, w * (0.5f - wide), h * (top + 0.3f), w * 0.5f, h * top)
                close()
            }
            drawPath(p, color)
        }
        drop(0.0f, 0.46f, Fire)
        drop(0.42f, 0.26f, FireGold)
    }
}
