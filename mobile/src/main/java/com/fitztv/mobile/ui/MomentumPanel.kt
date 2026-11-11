package com.fitztv.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitztv.data.Game
import com.fitztv.data.adapters.EspnLive
import com.fitztv.data.adapters.LiveSnapshot
import com.fitztv.data.adapters.SportsDbArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Phone version of the TV stats drawer's Shooting + Momentum tabs, from ESPN's public feed (refreshes every 10 s). */
@Composable
fun MomentumPanel(game: Game, modifier: Modifier = Modifier) {
    var snap by remember(game.id) { mutableStateOf<LiveSnapshot?>(null) }
    var loaded by remember(game.id) { mutableStateOf(false) }
    LaunchedEffect(game.id) {
        while (true) {
            withContext(Dispatchers.IO) { EspnLive.latest(game) }?.let { snap = it }
            loaded = true
            delay(if (snap?.live == false) 60_000 else 10_000)
        }
    }
    val s = snap
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (s == null) { Text(if (loaded) "No live data for this game" else "Loading…", color = Neon.TextDim, fontSize = 14.sp); return@Column }
        Text("${s.awayName} ${s.awayScore} – ${s.homeScore} ${s.homeName}   ${s.statusDetail}", color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        Text("Win probability", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        val m = s.momentum
        if (m.size < 2) Text("ESPN has no win probability for this game yet", color = Neon.TextFaint, fontSize = 13.sp)
        else {
            val home = ((s.homeWinProb ?: 0.5) * 100).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Crest(game.awayLogo, game.away)
                Text(" ${100 - home}%", color = Neon.Cyan, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("$home% ", color = Neon.Purple, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Crest(game.homeLogo, game.home)
            }
            val line = Neon.Purple
            val mid = Neon.Line
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                drawLine(mid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                val path = Path()
                m.forEachIndexed { i, v ->
                    val x = size.width * i / (m.size - 1)
                    val y = size.height * (1f - v.toFloat().coerceIn(0f, 1f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line, style = Stroke(2.dp.toPx()))
            }
            m.getOrNull(m.size - 6)?.let { back ->
                val swing = ((m.last() - back) * 100).roundToInt()
                if (swing != 0) Text("${if (swing > 0) s.homeName else s.awayName} ▲ ${abs(swing)}% recently", color = Neon.TextDim, fontSize = 13.sp)
            }
        }

        if (s.shooting.isNotEmpty()) {
            Text("Shooting", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            s.shooting.forEach { sp ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${sp.awayPct}  ${sp.away}", color = Neon.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(sp.label, color = Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("${sp.home}  ${sp.homePct}", color = Neon.Text, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) {
                        Box(Modifier.weight(pct(sp.awayPct).coerceAtLeast(1f)).fillMaxHeight().background(Neon.Cyan))
                        Spacer(Modifier.width(2.dp))
                        Box(Modifier.weight(pct(sp.homePct).coerceAtLeast(1f)).fillMaxHeight().background(Neon.Purple))
                    }
                }
            }
        }
    }
}

private fun pct(s: String) = s.trimEnd('%').toFloatOrNull() ?: 0f

/** The feed's team logo, or TheSportsDB's transparent crest when the feed has none. */
@Composable
private fun Crest(logo: String?, team: String) {
    val url by produceState(logo, logo, team) { if (logo == null) value = withContext(Dispatchers.IO) { SportsDbArt.teamCrest(team) } }
    url?.let { AsyncImage(it, team, Modifier.size(22.dp)) }
}
