package com.fitztv.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.Glyph
import com.fitztv.ui.components.LiveStatsPanel
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.Tabular
import com.fitztv.ui.theme.Neon
import com.fitztv.ui.theme.SairaCondensed
import kotlinx.coroutines.launch

private enum class HubTab(val label: String) { STREAMS("Streams"), STATS("Box score"), REPLAYS("Replays") }

/**
 * Game page: scoreboard, main actions, then tabs — Streams (not finished), Replays (finished)
 * and Box score (started games with stats).
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
    var tab by remember(game.id) { mutableStateOf(if (startReplays && HubTab.REPLAYS in tabs) HubTab.REPLAYS else tabs.first()) }
    var userSwitched by remember(game.id) { mutableStateOf(false) }
    val zone = settings.zone()
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(tab) { if (tab == HubTab.STATS) runCatching { tabFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(900.dp).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(game.league.uppercase(), fontFamily = SairaCondensed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HubTeam(game.awayLogo, game.awayShort.ifBlank { game.away }, Modifier.weight(1f))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (game.gameState != GameState.PRE && game.home.isNotBlank())
                        Text("${game.awayScore ?: 0} – ${game.homeScore ?: 0}", fontFamily = SairaCondensed, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Neon.Text, style = Tabular)
                    else Text("v", fontFamily = SairaCondensed, fontSize = 34.sp, color = Neon.TextDim)
                    val status = when (game.gameState) {
                        GameState.LIVE -> game.statusText.ifBlank { "LIVE" }
                        GameState.FINAL -> game.statusText.ifBlank { "Final" }
                        GameState.PRE -> Fmt.whenLabel(game, zone, settings.use24h)
                    }
                    Text(status, fontSize = 15.sp, color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, style = Tabular, textAlign = TextAlign.Center)
                }
                HubTeam(game.homeLogo, game.homeShort.ifBlank { game.home }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (game.gameState) {
                    GameState.LIVE -> {
                        NeonButton("Join live", onClick = { onGame(game) }, selected = true, icon = Glyph.Play)
                        NeonButton("From start", icon = Glyph.Start, onClick = { scope.launch { if (!com.fitztv.player.Launcher.play(context, game, fromStart = true)) tab = HubTab.STREAMS } })
                    }
                    GameState.PRE -> {
                        val reminded = game.id in settings.reminders
                        NeonButton(if (reminded) "Reminder set" else "Remind me", onClick = { app.alerts.toggleReminder(game) }, selected = reminded, icon = if (reminded) Glyph.BellOn else Glyph.Bell)
                    }
                    GameState.FINAL -> Unit
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs.forEach { t -> NeonButton(t.label, onClick = { tab = t; userSwitched = true }, selected = t == tab, modifier = if (t == tab) Modifier.focusRequester(tabFocus) else Modifier) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Line))
            Box(Modifier.fillMaxWidth()) {
                when (tab) {
                    HubTab.STREAMS -> StreamsPane(app, game, onDismiss, takeFocus = !userSwitched)
                    HubTab.REPLAYS -> ReplayPane(app, game, onDismiss, takeFocus = !userSwitched)
                    HubTab.STATS -> LiveStatsPanel(game)
                }
            }
        }
    }
}

@Composable
private fun HubTeam(logo: String?, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            if (logo != null) AsyncImage(logo, null, Modifier.size(66.dp), contentScale = ContentScale.Fit)
            else Text(name.take(3).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.TextDim)
        }
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
