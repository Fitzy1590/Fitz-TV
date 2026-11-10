package com.fitztv.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.launch

private enum class HubTab(val label: String) { STREAMS("Streams"), STATS("Box score"), REPLAYS("Replays") }

/**
 * Game page: scoreboard header with the main actions, then tabs — Streams (not finished),
 * Box score (started games with stats) and Replays (finished games).
 */
@Composable
fun GameHub(app: FitzApp, game: Game, startReplays: Boolean, onGame: (Game) -> Unit, onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs = remember(game.id, game.gameState) {
        buildList {
            if (game.gameState != GameState.FINAL) add(HubTab.STREAMS)
            if (game.gameState == GameState.FINAL) add(HubTab.REPLAYS)
            if (game.gameState != GameState.PRE && game.espnPath != null) add(HubTab.STATS)
        }
    }
    var tab by rememberSaveable(game.id) { mutableStateOf(if (startReplays && HubTab.REPLAYS in tabs) HubTab.REPLAYS else tabs.first()) }
    val zone = settings.zone()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Neon.Bg).statusBarsPadding()) {
            // Header: league + close.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(game.league.uppercase(), fontFamily = SairaCondensed, color = Neon.TextDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Close", color = Neon.Purple, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
            }
            // Scoreboard.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HubTeam(game.awayLogo, game.awayShort.ifBlank { game.away }, Modifier.weight(1f))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (game.gameState != GameState.PRE && game.home.isNotBlank())
                        Text("${game.awayScore ?: 0} – ${game.homeScore ?: 0}", fontFamily = SairaCondensed, color = Neon.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold, style = Tabular)
                    else Text("v", fontFamily = SairaCondensed, color = Neon.TextDim, fontSize = 32.sp)
                    val status = when (game.gameState) {
                        GameState.LIVE -> game.statusText.ifBlank { "LIVE" }
                        GameState.FINAL -> game.statusText.ifBlank { "Final" }
                        GameState.PRE -> Fmt.whenLabel(game, zone, settings.use24h)
                    }
                    Text(status, color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, fontSize = 14.sp, style = Tabular, textAlign = TextAlign.Center)
                }
                HubTeam(game.homeLogo, game.homeShort.ifBlank { game.home }, Modifier.weight(1f))
            }
            // Main actions.
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val primary = ButtonDefaults.buttonColors(containerColor = Neon.Purple, contentColor = Neon.Bg)
                when (game.gameState) {
                    GameState.LIVE -> {
                        Button(onClick = { onGame(game) }, colors = primary) { Icon(Glyph.Play, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Join live") }
                        OutlinedButton(onClick = { scope.launch { if (!com.fitztv.player.Launcher.play(context, game, fromStart = true)) tab = HubTab.STREAMS } }) { Text("From start", color = Neon.Text) }
                    }
                    GameState.PRE -> {
                        val reminded = game.id in settings.reminders
                        OutlinedButton(onClick = { app.alerts.toggleReminder(game) }) { Text(if (reminded) "Reminder set" else "Remind me", color = if (reminded) Neon.Purple else Neon.Text) }
                    }
                    GameState.FINAL -> Unit
                }
            }
            // Tabs.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                tabs.forEach { t ->
                    Column(Modifier.weight(1f).clickable { tab = t }.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.label.uppercase(), fontFamily = SairaCondensed, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (t == tab) Neon.Text else Neon.TextDim)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (t == tab) Neon.Purple else Color.Transparent))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Line))
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                when (tab) {
                    HubTab.STREAMS -> StreamsContent(app, game, onDismiss, listMax = 2000.dp)
                    HubTab.REPLAYS -> ReplayContent(app, game, onDismiss, listMax = 2000.dp)
                    HubTab.STATS -> LiveStatsCard(game, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun HubTeam(logo: String?, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, null, Modifier.size(58.dp), contentScale = ContentScale.Fit)
            else Text(name.take(3).uppercase(), color = Neon.TextDim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(name, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
