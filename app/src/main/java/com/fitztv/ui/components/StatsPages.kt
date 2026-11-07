package com.fitztv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fitztv.sports.Lineup
import com.fitztv.sports.LineupPlayer
import com.fitztv.sports.TeamBox
import com.fitztv.ui.theme.Neon

private val statCols = listOf("PTS", "REB", "AST", "STL", "BLK", "FGM", "FGX", "+/-")

/** Basketball box score for one team, most points first. FGX = field goals missed. */
@Composable
fun PlayersTable(box: TeamBox, maxRows: Int = 13) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(box.team, color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            statCols.forEach { Text(it, color = Neon.TextFaint, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(31.dp)) }
        }
        box.players.take(maxRows).forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().background(if (i % 2 == 0) Neon.Text.copy(alpha = 0.04f) else Color.Transparent).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (p.starter) "" else "· ") + p.name, color = Neon.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (p.starter) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f),
                )
                listOf("${p.pts}", "${p.reb}", "${p.ast}", "${p.stl}", "${p.blk}", "${p.fgm}", "${p.fgMissed}", p.plusMinus).forEachIndexed { c, v ->
                    Text(
                        v, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(31.dp), style = Tabular,
                        color = when { c == 0 -> Neon.Purple; c == 7 && v.startsWith("-") -> Neon.Live; c == 7 && v.startsWith("+") -> Neon.Text; else -> Neon.TextDim },
                        fontWeight = if (c == 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** Formation view: starters on a pitch, goalkeeper at the bottom, attack at the top. */
@Composable
fun Pitch(lineup: Lineup, height: Int = 230) {
    val lines = lineup.lines()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lineup.team, color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            lineup.formation?.let { Pill(it, Neon.TextDim) }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(height.dp)
                .background(Color(0xFF0D2418).copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
        ) {
            // Halfway line + centre circle (attack is the top half).
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
            Box(Modifier.align(Alignment.BottomCenter).size(width = 90.dp, height = 28.dp).border(1.dp, Color.White.copy(alpha = 0.2f)))
            Column(Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                lines.reversed().forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { line.forEach { PlayerDot(it) } }
                }
            }
        }
        if (lineup.subs.isNotEmpty()) {
            Text(
                "Bench: " + lineup.subs.take(9).joinToString(", ") { it.name },
                color = Neon.TextFaint, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PlayerDot(p: LineupPlayer) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        Box(Modifier.size(22.dp).background(Neon.Purple, CircleShape).border(1.dp, Neon.Text.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Text(p.jersey, color = Neon.Bg, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(p.name, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Page names available for these stats. */
fun statPages(hasPlayers: Boolean, hasLineups: Boolean): List<String> =
    listOf("Summary") + (if (hasPlayers) listOf("Players") else emptyList()) + (if (hasLineups) listOf("Line-ups") else emptyList())
