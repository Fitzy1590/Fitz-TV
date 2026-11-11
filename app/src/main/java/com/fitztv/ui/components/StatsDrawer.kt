package com.fitztv.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.fitztv.data.Game
import com.fitztv.data.adapters.EspnLive
import com.fitztv.data.adapters.LiveSnapshot
import com.fitztv.sports.LiveStats
import com.fitztv.sports.StatsClient
import com.fitztv.ui.screens.focusRestorerCompat
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val DRAWER_TABS = listOf("Box", "Shooting", "Momentum")

/**
 * Slide-over stats drawer for the player (about 30% of the width, the video keeps playing).
 * Tabs take focus with ◀ ▶ and the list scrolls with ▲▼ (driven by [scroll]); numbers come from
 * ESPN's public feed and refresh every 10 s while the app is in the foreground.
 */
@Composable
fun StatsDrawer(game: Game, tab: Int, scroll: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    var snap by remember(game.id) { mutableStateOf<LiveSnapshot?>(null) }
    var stats by remember(game.id) { mutableStateOf<LiveStats?>(null) }
    var loaded by remember(game.id) { mutableStateOf(false) }
    val owner = LocalContext.current as? LifecycleOwner

    LaunchedEffect(game.id) {
        val poll: suspend () -> Unit = {
            var n = 0
            while (true) {
                withContext(Dispatchers.IO) { EspnLive.latest(game) }?.let { snap = it }
                if (n++ % 2 == 0) withContext(Dispatchers.IO) { StatsClient.fetch(game) }?.let { stats = it }
                loaded = true
                delay(if (snap?.live == false) 60_000 else 10_000)
            }
        }
        if (owner != null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { poll() } else poll()
    }

    val frs = remember { List(DRAWER_TABS.size) { FocusRequester() } }
    LaunchedEffect(Unit) { delay(50); runCatching { frs[tab.coerceIn(0, frs.lastIndex)].requestFocus() } }

    Row(modifier.fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(Neon.Line))
        Column(Modifier.fillMaxHeight().weight(1f).background(Neon.Bg.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 20.dp)) {
            val s = snap
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s != null) "${s.awayName} ${s.awayScore} – ${s.homeScore} ${s.homeName}" else game.matchup,
                    color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = Tabular, modifier = Modifier.weight(1f),
                )
                if (s?.live == true) { LiveDot(); Spacer(Modifier.width(6.dp)) }
            }
            Text(s?.statusDetail ?: game.statusText, color = if (s?.live == true) Neon.Live else Neon.TextDim, fontSize = 12.sp, maxLines = 1, style = Clock)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.focusRestorerCompat(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DRAWER_TABS.forEachIndexed { i, label ->
                    NeonSurface(
                        onClick = {}, radius = 8.dp, selected = i == tab,
                        modifier = Modifier.focusRequester(frs[i]).onFocusChanged { if (it.hasFocus) onTab(i) },
                    ) {
                        Text(label, color = if (i == tab) Neon.Text else Neon.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val sc = rememberScrollState()
            LaunchedEffect(scroll, tab) { sc.animateScrollTo((scroll.coerceAtLeast(0) * 140).coerceAtMost(sc.maxValue)) }
            Column(Modifier.weight(1f).verticalScroll(sc), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    s == null && stats == null -> Text(if (loaded) "No live data for this game" else "Loading…", color = Neon.TextDim, fontSize = 13.sp)
                    tab == 0 -> BoxTab(stats)
                    tab == 1 -> ShootingTab(s)
                    else -> MomentumTab(s)
                }
            }
            Text("◀ ▶ tabs · ▲▼ scroll · Back closes", color = Neon.TextFaint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BoxTab(stats: LiveStats?) {
    if (stats == null) { Text("Box score not available yet", color = Neon.TextDim, fontSize = 13.sp); return }
    stats.teamBoxes.forEach { box ->
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(box.team, color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                listOf("PTS", "REB", "AST").forEach { Text(it, color = Neon.TextFaint, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(32.dp)) }
            }
            box.players.take(6).forEach { p ->
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = Neon.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${p.pts}", color = Neon.Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, style = Tabular, modifier = Modifier.width(32.dp))
                    Text("${p.reb}", color = Neon.TextDim, fontSize = 12.sp, textAlign = TextAlign.End, style = Tabular, modifier = Modifier.width(32.dp))
                    Text("${p.ast}", color = Neon.TextDim, fontSize = 12.sp, textAlign = TextAlign.End, style = Tabular, modifier = Modifier.width(32.dp))
                }
            }
        }
    }
    if (stats.teamBoxes.isEmpty()) {
        // Other sports: top lists (passing, rushing…) or the feed's leaders.
        stats.categories.take(4).forEach { c ->
            Column {
                Text(c.title, color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                c.rows.take(3).forEach { r ->
                    Text("${r.name}  ${r.values.take(3).joinToString(" · ")}", color = Neon.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (stats.categories.isEmpty()) stats.leaders.take(8).forEach { Text(it, color = Neon.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ShootingTab(s: LiveSnapshot?) {
    if (s == null || s.shooting.isEmpty()) { Text("Shooting splits aren't published for this game", color = Neon.TextDim, fontSize = 13.sp); return }
    Row(Modifier.fillMaxWidth()) {
        Text(s.awayName, color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(s.homeName, color = Neon.Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
    s.shooting.forEach { sp ->
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${sp.awayPct}  ${sp.away}", color = Neon.Text, fontSize = 12.sp, style = Tabular, modifier = Modifier.weight(1f))
                Text(sp.label, color = Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("${sp.home}  ${sp.homePct}", color = Neon.Text, fontSize = 12.sp, textAlign = TextAlign.End, style = Tabular, modifier = Modifier.weight(1f))
            }
            val a = pct(sp.awayPct); val h = pct(sp.homePct)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) {
                Box(Modifier.weight(a.coerceAtLeast(1f)).fillMaxHeight().background(Neon.Cyan))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.weight(h.coerceAtLeast(1f)).fillMaxHeight().background(Neon.Purple))
            }
        }
    }
}

private fun pct(s: String) = s.trimEnd('%').toFloatOrNull() ?: 0f

@Composable
private fun MomentumTab(s: LiveSnapshot?) {
    val m = s?.momentum.orEmpty()
    if (s == null || m.size < 2) { Text("ESPN has no win probability for this game yet", color = Neon.TextDim, fontSize = 13.sp); return }
    val home = ((s.homeWinProb ?: 0.5) * 100).roundToInt()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${s.awayName} ${100 - home}%", color = Neon.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Tabular, modifier = Modifier.weight(1f))
        Text("${s.homeName} $home%", color = Neon.Purple, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, style = Tabular, modifier = Modifier.weight(1f))
    }
    val line = Neon.Purple
    val mid = Neon.Line
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        drawLine(mid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        val path = Path()
        m.forEachIndexed { i, v ->
            val x = size.width * i / (m.size - 1)
            val y = size.height * (1f - v.toFloat().coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(2.dp.toPx()))
    }
    val back = m.getOrNull(m.size - 6)
    if (back != null) {
        val swing = ((m.last() - back) * 100).roundToInt()
        Text(
            if (swing == 0) "Steady over the last few minutes" else "${if (swing > 0) s.homeName else s.awayName} ▲ ${kotlin.math.abs(swing)}% recently",
            color = Neon.TextDim, fontSize = 12.sp,
        )
    }
    Text("Probability of a home win, start to now", color = Neon.TextFaint, fontSize = 12.sp)
}
