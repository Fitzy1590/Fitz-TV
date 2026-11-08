package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lets the user choose which channel to watch a game on (matched channels first). */
@Composable
fun ChannelPickerDialog(app: FitzApp, game: Game, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsState()
    var matched by remember { mutableStateOf<List<Channel>?>(null) }
    LaunchedEffect(game.id) {
        matched = app.repo.rankedStreams(game)
    }
    val m = matched.orEmpty()
    val others = remember(m) {
        val ids = m.map { it.id }.toSet()
        // Nothing else names the game: likely channels first, going by what their guide says is on now.
        app.repo.fallbackChannels(game).filter { it.id !in ids }
    }
    val all = m + others
    val focus = remember { FocusRequester() }
    LaunchedEffect(all.isNotEmpty()) { if (all.isNotEmpty()) runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(640.dp)
                .background(Neon.Bg2, RoundedCornerShape(16.dp))
                .border(1.dp, Neon.Line, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Text("${game.league} · ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}", color = Neon.TextDim, fontSize = 14.sp)
            if (game.broadcasts.isNotEmpty()) Text("Broadcast on: ${game.broadcasts.joinToString(", ")}", color = Neon.TextFaint, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            // Remind / record / multiview for this game.
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val reminded = game.id in settings.reminders
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game.gameState == com.fitztv.data.GameState.PRE) {
                    com.fitztv.ui.components.NeonButton(if (reminded) "Reminder set" else "Remind me", selected = reminded, icon = if (reminded) com.fitztv.ui.components.Glyph.BellOn else com.fitztv.ui.components.Glyph.Bell, onClick = { app.alerts.toggleReminder(game) })
                }
                if (game.gameState != com.fitztv.data.GameState.FINAL) {
                    com.fitztv.ui.components.NeonButton("Record", icon = com.fitztv.ui.components.Glyph.Record, onClick = {
                        if (m.isNotEmpty()) {
                            val r = com.fitztv.record.Recorder.scheduleGame(context, game, m)
                            android.widget.Toast.makeText(context, if (r.status == "scheduled" && r.startMillis > System.currentTimeMillis() + 60_000) "Recording scheduled" else "Recording started", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else android.widget.Toast.makeText(context, "No stream found to record", android.widget.Toast.LENGTH_SHORT).show()
                    })
                    com.fitztv.ui.components.NeonButton("Multiview", icon = com.fitztv.ui.components.Glyph.Multiview, onClick = {
                        onDismiss(); scope.launch { com.fitztv.player.Launcher.addToMultiview(context, game) }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (all.isEmpty()) {
                Text("No matching channels in your sources.", color = Neon.TextDim)
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(all, key = { _, c -> c.id }) { i, c ->
                    NeonSurface(
                        onClick = { onDismiss(); if (i < m.size) PlaybackQueue.launch(context, m, i, game.id) else PlaybackQueue.launch(context, listOf(all[i]) + m, 0, game.id) },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                        radius = 10.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (i < m.size) Pill("Match", Neon.Purple) else Pill(c.sport.label, Neon.TextFaint)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = remember(c.id) { com.fitztv.data.StreamNotes.line(app.repo, c, game) }
                                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(app.repo.probe.label(c), color = if (app.repo.probe.isMeasured(c)) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = com.fitztv.ui.components.Tabular)
                        }
                    }
                }
            }
        }
    }
}
