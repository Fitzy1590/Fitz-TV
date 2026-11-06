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
        (app.repo.channelsFor(game.sport) + app.repo.channelsFor(Sport.GENERAL))
            .filter { it.id !in ids }.sortedByDescending { it.quality }
    }
    val all = m + others
    val focus = remember { FocusRequester() }
    LaunchedEffect(all.isNotEmpty()) { if (all.isNotEmpty()) runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(640.dp)
                .background(Neon.Bg2, RoundedCornerShape(20.dp))
                .border(2.dp, Neon.Purple, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(if (game.home.isNotBlank()) "${game.away} vs ${game.home}" else game.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
            Text("${game.league} • ${Fmt.whenLabel(game, settings.zone(), settings.use24h)}", color = Neon.Cyan, fontSize = 14.sp)
            if (game.broadcasts.isNotEmpty()) Text("Broadcast on: ${game.broadcasts.joinToString(", ")}", color = Neon.TextFaint, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            // Remind / record / multiview for this game.
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val reminded = game.id in settings.reminders
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (game.gameState == com.fitztv.data.GameState.PRE) {
                    com.fitztv.ui.components.NeonButton(if (reminded) "🔔 Reminder set" else "🔔 Remind me", selected = reminded, onClick = { app.alerts.toggleReminder(game) })
                }
                if (game.gameState != com.fitztv.data.GameState.FINAL) {
                    com.fitztv.ui.components.NeonButton("⏺ Record", onClick = {
                        if (m.isNotEmpty()) {
                            val r = com.fitztv.record.Recorder.scheduleGame(context, game, m)
                            android.widget.Toast.makeText(context, if (r.status == "scheduled" && r.startMillis > System.currentTimeMillis() + 60_000) "Recording scheduled" else "Recording started", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else android.widget.Toast.makeText(context, "No stream found to record", android.widget.Toast.LENGTH_SHORT).show()
                    })
                    com.fitztv.ui.components.NeonButton("⊞ Multiview", onClick = {
                        onDismiss(); scope.launch { com.fitztv.player.Launcher.addToMultiview(context, game) }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (com.fitztv.external.Kayo.isInstalled(context) && com.fitztv.external.Kayo.covers(game)) {
                NeonSurface(onClick = { onDismiss(); com.fitztv.external.Kayo.open(context) }, modifier = Modifier.fillMaxWidth(), radius = 10.dp, selected = true) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("KAYO", Neon.Cyan)
                        Spacer(Modifier.width(10.dp))
                        Text("Watch on Kayo (opens the Kayo app, 1080p 50fps)", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (all.isEmpty()) {
                Text("No matching channels in your sources.", color = Neon.TextDim)
            }
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(all, key = { _, c -> c.id }) { i, c ->
                    NeonSurface(
                        onClick = { onDismiss(); PlaybackQueue.launch(context, all, i, game.id) },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                        radius = 10.dp,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (i < m.size) Pill("MATCH", Neon.Magenta) else Pill(c.sport.label, Neon.TextFaint)
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            val h = app.repo.probe.height(c)
                            val f = app.repo.probe.fps(c)
                            Pill(Fmt.resolution(h) + if (f >= 49f) " • ${f.toInt()}fps" else "", Neon.Cyan)
                        }
                    }
                }
            }
        }
    }
}
