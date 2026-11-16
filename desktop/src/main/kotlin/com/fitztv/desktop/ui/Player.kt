@file:OptIn(ExperimentalComposeUiApi::class)

package com.fitztv.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.Repository
import com.fitztv.player.PlayMode
import com.fitztv.player.PlaybackController
import com.fitztv.player.PlaybackQueue
import com.fitztv.player.Multiview
import com.fitztv.player.VideoView
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Panel { NONE, CONTROLS, GAMES, CHANNELS }

/** The live picture from libVLC, letter-boxed, on black. */
@Composable
fun VideoSurface(engine: com.fitztv.player.PlaybackEngine?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        val frame = (engine?.view as? VideoView)?.frame
        if (frame != null) Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/**
 * Full-screen player. Keys: Space pause · ←/→ back/forward 10 s · ↑/↓ previous/next stream ·
 * G live games · S streams · I live stats · L go live · M mute · F or F11 full screen · Esc back.
 */
@Composable
fun PlayerScreen(fullscreen: Boolean, onToggleFullscreen: () -> Unit, onClose: () -> Unit) {
    val app = FitzApp.instance
    val scope = rememberCoroutineScope()
    val controller = remember { PlaybackController(app, app.repo, app.settings, scope) }
    val st by controller.state.collectAsState()
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val zone = settings.zone()
    var panel by remember { mutableStateOf(Panel.NONE) }
    var info by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var queueVersion by remember { mutableIntStateOf(0) }
    var infoJob by remember { mutableStateOf<Job?>(null) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }

    val game = remember(games, st.channel?.id, queueVersion) {
        PlaybackQueue.gameId?.let { id -> games.firstOrNull { it.id == id } } ?: st.channel?.let { app.repo.gameFor(it) }
    }

    fun showInfo() {
        info = true; lastMove = System.currentTimeMillis()
        infoJob?.cancel()
        infoJob = scope.launch { delay(4_500); info = false }
    }

    fun playIndex(i: Int) {
        val q = PlaybackQueue.channels
        if (q.isEmpty()) { onClose(); return }
        val idx = i.mod(q.size)
        PlaybackQueue.index = idx
        // Watching a game: the queue is the ranked stream list, so failures fall through it in order.
        controller.play(q[idx], candidates = if (PlaybackQueue.gameId != null) q else emptyList())
        showInfo()
    }

    fun playGame(g: Game) {
        scope.launch {
            controller.flash("Finding the best stream…")
            val matches = (app.repo.route(app, g) as? Repository.Route.Streams)?.ranked.orEmpty()
            if (matches.isEmpty()) { panel = Panel.NONE; controller.flash("No channel found for ${g.title}"); return@launch }
            PlaybackQueue.replace(matches, 0, g.id)
            panel = Panel.NONE; queueVersion++
            playIndex(0)
        }
    }

    fun openMultiview() {
        val q = PlaybackQueue.channels
        if (q.isEmpty()) return
        val at = q.indexOfFirst { it.id == st.channel?.id }.takeIf { it >= 0 } ?: PlaybackQueue.index.coerceIn(0, q.lastIndex)
        Multiview.slotFor(game, q, at)?.let { Multiview.add(it) }
        controller.release()   // free this connection before the multiview windows start
        Multiview.open()
        onClose()
    }

    fun recordNow() {
        val q = PlaybackQueue.channels
        val cur = st.channel ?: return
        val list = listOf(cur) + q.filter { it.id != cur.id }
        val rec = if (game != null) com.fitztv.record.Recorder.scheduleGame(app, game, list)
        else com.fitztv.record.Recorder.schedule(app, cur.label, cur.sport.name, list, System.currentTimeMillis(), System.currentTimeMillis() + 3 * 3_600_000L)
        controller.flash("Recording ${rec.title} — find it in Replays › My recordings")
    }

    DisposableEffect(Unit) {
        onDispose { controller.release(); app.alerts.watchingGameId = null }   // never keep streaming in the background
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; playIndex(PlaybackQueue.index) }
    LaunchedEffect(st.channel) { app.alerts.watchingGameId = PlaybackQueue.gameId }
    // Controls fade away after a few seconds without mouse or key activity.
    LaunchedEffect(panel, lastMove) { if (panel == Panel.CONTROLS) { delay(5_000); if (System.currentTimeMillis() - lastMove >= 4_900) panel = Panel.NONE } }

    fun toggleStats() { if (game != null) stats = !stats else controller.flash("No live stats for this channel"); showInfo() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusTarget()
            .onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis(); if (panel == Panel.NONE) panel = Panel.CONTROLS; showInfo() }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastMove = System.currentTimeMillis()
                when (e.key) {
                    Key.Escape, Key.Backspace -> { if (panel == Panel.GAMES || panel == Panel.CHANNELS) panel = Panel.NONE else if (stats) stats = false else onClose(); true }
                    Key.Spacebar, Key.K -> { controller.togglePause(); showInfo(); true }
                    Key.DirectionLeft -> { controller.skip(-10_000); showInfo(); true }
                    Key.DirectionRight -> { controller.skip(10_000); showInfo(); true }
                    Key.DirectionUp, Key.PageUp -> { playIndex(PlaybackQueue.index - 1); true }
                    Key.DirectionDown, Key.PageDown -> { playIndex(PlaybackQueue.index + 1); true }
                    Key.G -> { panel = if (panel == Panel.GAMES) Panel.NONE else Panel.GAMES; true }
                    Key.S -> { panel = if (panel == Panel.CHANNELS) Panel.NONE else Panel.CHANNELS; true }
                    Key.I -> { toggleStats(); true }
                    Key.L -> { controller.goLive(); showInfo(); true }
                    Key.M -> { muted = !muted; controller.setVolume(if (muted) 0f else 1f); true }
                    Key.F, Key.F11 -> { onToggleFullscreen(); true }
                    Key.Enter -> { panel = if (panel == Panel.CONTROLS) Panel.NONE else Panel.CONTROLS; showInfo(); true }
                    else -> false
                }
            },
    ) {
        VideoSurface(st.engine, Modifier.fillMaxSize())
        if (st.buffering) CircularProgressIndicator(color = Neon.Purple, trackColor = Neon.Purple.copy(alpha = 0.15f), strokeWidth = 5.dp, modifier = Modifier.align(Alignment.Center).size(72.dp))

        // ---- info bar -------------------------------------------------------------------------
        if ((info || st.message != null) && panel != Panel.CONTROLS) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Neon.Bg.copy(alpha = 0.92f))))
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 28.dp),
            ) { InfoBar(st, game, zone, settings.use24h) }
        }

        // ---- live stats (right, auto-updating) -----------------------------------------------
        if (stats && game != null) {
            Box(Modifier.align(Alignment.CenterEnd).width(460.dp).fillMaxHeight().background(Neon.Bg.copy(alpha = 0.78f)).padding(horizontal = 22.dp, vertical = 28.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Live stats", color = Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        NeonButton("", onClick = { stats = false }, icon = Glyph.Remove)
                    }
                    LiveStatsPanel(game, Modifier.fillMaxWidth(), onClip = { c ->
                        PlaybackQueue.launch(listOf(com.fitztv.data.VodItem("clip:" + c.url.hashCode(), c.title, "Highlights", game.sport, c.url, c.thumb).asChannel()), 0, null)
                        playIndex(0)
                    })
                }
            }
        }

        if (st.paused) {
            Row(Modifier.align(Alignment.Center).background(Neon.Bg.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Glyph.Pause, null, tint = Neon.Text, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Paused", color = Neon.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (panel == Panel.CONTROLS) {
            TransportBar(
                st, game, controller, fullscreen, muted,
                onMute = { muted = !muted; controller.setVolume(if (muted) 0f else 1f) },
                onPrev = { playIndex(PlaybackQueue.index - 1) }, onNext = { playIndex(PlaybackQueue.index + 1) },
                onGames = { panel = Panel.GAMES }, onStreams = { panel = Panel.CHANNELS },
                onStats = ::toggleStats, onMultiview = ::openMultiview, onRecord = ::recordNow,
                onFullscreen = onToggleFullscreen, onClose = onClose,
                onFromStart = { controller.playFromStart(game) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ---- other live games (left) --------------------------------------------------------
        if (panel == Panel.GAMES) {
            val list = remember(games, settings) { app.repo.liveNow(null).filter { it.id != PlaybackQueue.gameId } }
            SidePanel("Live now", Alignment.CenterStart, onClose = { panel = Panel.NONE }) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (list.isEmpty()) item { Text("No other games are live right now", color = Neon.TextDim) }
                    itemsIndexed(list, key = { _, g -> g.id }) { _, g ->
                        NeonSurface(onClick = { playGame(g) }, modifier = Modifier.fillMaxWidth(), radius = 10.dp) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(Glyph.sport(g.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(g.league, color = Neon.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (g.gameState == GameState.LIVE) { LiveDot(); Spacer(Modifier.width(6.dp)); Text(g.statusText.ifBlank { "Live" }, color = Neon.Live, fontSize = 13.sp, style = Clock) }
                                    else Text(Fmt.time(g.startUtcMillis, zone, settings.use24h), color = Neon.TextDim, fontSize = 13.sp, style = Tabular)
                                }
                                val line = if (g.home.isNotBlank() && g.gameState == GameState.LIVE) "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}" else g.matchup
                                Text(line, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = Tabular)
                            }
                        }
                    }
                }
            }
        }

        // ---- streams / channels (right) -----------------------------------------------------
        if (panel == Panel.CHANNELS) {
            val q: List<Channel> = remember(queueVersion, st.channel) { PlaybackQueue.channels }
            SidePanel(if (game != null) "Streams for ${game.matchup} — best first" else "Channels", Alignment.CenterEnd, onClose = { panel = Panel.NONE }) {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (PlaybackQueue.index - 2).coerceAtLeast(0))
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(q, key = { _, c -> c.id }) { i, c ->
                        val current = i == PlaybackQueue.index
                        NeonSurface(onClick = { panel = Panel.NONE; playIndex(i) }, modifier = Modifier.fillMaxWidth(), radius = 10.dp, selected = current) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = Neon.TextFaint, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = remember(c.id, game?.id) { com.fitztv.data.StreamNotes.line(app.repo, c, game) }
                                    val off = remember(c.id, game?.id) { com.fitztv.data.StreamNotes.contradicted(app.repo, c, game) }
                                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = if (off) Neon.TextFaint else Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(app.repo.probe.label(c), color = if (app.repo.probe.isMeasured(c)) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = Tabular)
                            }
                        }
                    }
                }
            }
        }

        AlertOverlay(app, onWatch = { g -> playGame(g) })
    }
}

@Composable
private fun InfoBar(st: com.fitztv.player.PlayerState, game: Game?, zone: java.time.ZoneId, use24h: Boolean) {
    val app = FitzApp.instance
    st.message?.let { Text(it, color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
    if (game != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (game.gameState == GameState.LIVE) { LiveDot(); Spacer(Modifier.width(8.dp)) }
            val score = if (game.home.isNotBlank() && game.gameState != GameState.PRE) "${game.away} ${game.awayScore ?: ""}  –  ${game.homeScore ?: ""} ${game.home}" else game.matchup
            Text(score, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = Tabular)
            Spacer(Modifier.width(12.dp))
            Text(if (game.gameState == GameState.PRE) Fmt.whenLabel(game, zone, use24h) else game.statusText, color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, fontSize = 15.sp, style = Clock)
        }
    }
    st.channel?.let { ch ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ch.label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(16.dp))
            Pill(if (st.height > 0) Fmt.resolution(st.height) else Fmt.quality(ch.quality), Neon.Text)
            Spacer(Modifier.width(8.dp))
            if (st.fps > 0) { Pill("${(st.fps * 100).roundToInt() / 100f} fps", Neon.TextDim); Spacer(Modifier.width(8.dp)) }
            Pill(st.engine?.name ?: "", Neon.TextDim)
            Spacer(Modifier.width(8.dp))
            if (com.fitztv.data.StreamLanguage.tier(ch) == com.fitztv.data.StreamLanguage.OTHER) { Pill("Other language", Neon.TextDim); Spacer(Modifier.width(8.dp)) }
            if (st.degraded) Pill("Safe mode", Neon.TextDim)
        }
        app.repo.nowPlaying(ch)?.let { p -> Text("Now: ${p.title}", color = Neon.TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(
            (if (PlaybackQueue.gameId != null) "Stream ${PlaybackQueue.index + 1} of ${PlaybackQueue.channels.size} (best first)" else "${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}") +
                "   ·   ↑↓ switch   ←→ ±10 s   G live games   S streams   I stats   Enter controls",
            color = Neon.TextFaint, fontSize = 13.sp,
        )
    }
}

@Composable
private fun BoxScope.SidePanel(title: String, at: Alignment, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.align(at).width(440.dp).fillMaxHeight().background(Neon.Bg.copy(alpha = 0.86f)).padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontFamily = SairaCondensed, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            NeonButton("", onClick = onClose, icon = Glyph.Remove)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun TransportBar(
    st: com.fitztv.player.PlayerState, game: Game?, controller: PlaybackController, fullscreen: Boolean, muted: Boolean,
    onMute: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onGames: () -> Unit, onStreams: () -> Unit, onStats: () -> Unit,
    onMultiview: () -> Unit, onRecord: () -> Unit, onFullscreen: () -> Unit, onClose: () -> Unit, onFromStart: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Neon.Bg.copy(alpha = 0.95f)))).padding(horizontal = 40.dp, vertical = 24.dp).padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column { InfoBar(st, game, FitzApp.instance.settings.value.zone(), FitzApp.instance.settings.value.use24h) }
        // Progress through the stream's own window (catch-up / replay / DVR).
        val t = st.timeline
        if (t.durationMs > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(clock(t.positionMs), color = Neon.TextDim, fontSize = 12.sp, style = Clock)
                Box(Modifier.weight(1f).height(4.dp).background(Neon.Line, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth((t.positionMs.toFloat() / t.durationMs).coerceIn(0f, 1f)).height(4.dp).background(Neon.Purple, RoundedCornerShape(2.dp)))
                }
                Text(clock(t.durationMs), color = Neon.TextDim, fontSize = 12.sp, style = Clock)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportButton("Previous", onPrev, icon = Glyph.Earlier)
            TransportButton(if (st.paused) "Play" else "Pause", { controller.togglePause() }, icon = if (st.paused) Glyph.Play else Glyph.Pause, selected = true)
            TransportButton("10s", { controller.skip(-10_000) }, icon = Glyph.Back)
            TransportButton("10s", { controller.skip(10_000) }, icon = Glyph.Forward)
            TransportButton(if (st.mode == PlayMode.LIVE && st.timeline.let { !it.seekable || it.durationMs - it.positionMs < 5_000 }) "Live" else "Go live", { controller.goLive() }, icon = Glyph.GoLive)
            if (game != null && game.gameState != GameState.PRE) TransportButton("From start", onFromStart, icon = Glyph.Start)
            TransportButton("Next", onNext, icon = Glyph.Later)
            Spacer(Modifier.width(8.dp))
            var preferred by remember(st.channel?.id) { mutableStateOf(controller.isUpvoted()) }
            TransportButton(if (preferred) "Preferred category" else "Prefer this category", { val msg = controller.toggleUpvote(); preferred = controller.isUpvoted(); controller.flash(msg) }, icon = if (preferred) Glyph.Star else Glyph.StarOff, selected = preferred)
            Spacer(Modifier.width(8.dp))
            var offset by remember { mutableIntStateOf(controller.audioOffsetMs()) }
            TransportButton("Audio earlier", { controller.adjustAudioOffset(-20); offset = controller.audioOffsetMs() }, icon = Glyph.Back)
            Text("Audio sync ${if (offset > 0) "+" else ""}$offset ms", color = Neon.TextDim, fontSize = 12.sp, style = Tabular)
            TransportButton("later", { controller.adjustAudioOffset(20); offset = controller.audioOffsetMs() }, icon = Glyph.Forward)
            if (offset != 0) TransportButton("Reset", { controller.adjustAudioOffset(0); offset = 0 })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportButton("Live games", onGames, icon = Glyph.Games)
            TransportButton("Streams", onStreams, icon = Glyph.Tv)
            TransportButton("Live stats", onStats, icon = Glyph.Stats)
            TransportButton("Multiview", onMultiview, icon = Glyph.Multiview)
            TransportButton("Record", onRecord, icon = Glyph.Record)
            TransportButton(if (muted) "Unmute" else "Mute", onMute, icon = Glyph.Sound, selected = muted)
            TransportButton(if (fullscreen) "Exit full screen" else "Full screen", onFullscreen, icon = Glyph.Fullscreen)
            Spacer(Modifier.weight(1f))
            TransportButton("Back", onClose, icon = Glyph.Remove)
        }
    }
}

@Composable
private fun TransportButton(label: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, selected: Boolean = false) =
    NeonButton(label, onClick = onClick, icon = icon, selected = selected)

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Score / reminder banner over the picture; click to switch to that game. */
@Composable
private fun BoxScope.AlertOverlay(app: FitzApp, onWatch: (Game) -> Unit) {
    var current by remember { mutableStateOf<com.fitztv.alerts.AppAlert?>(null) }
    LaunchedEffect(Unit) {
        app.alerts.events.collect { a ->
            if (a.game.id == PlaybackQueue.gameId) return@collect
            if (a.kind == com.fitztv.alerts.AppAlert.Kind.TEAM_STARTING && app.settings.value.autoSwitchToTeam) { onWatch(a.game); return@collect }
            current = a
            delay(12_000)
            if (current == a) current = null
        }
    }
    val a = current ?: return
    NeonSurface(onClick = { current = null; onWatch(a.game) }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp), radius = 12.dp, selected = true) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.text, color = Neon.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Text("Click to watch", color = Neon.TextDim, fontSize = 13.sp)
        }
    }
}

// ---- Multiview ---------------------------------------------------------------------------------

/** Up to four games at once; slot 0 is the big one in 3-up. Click a tile for its sound. */
@Composable
fun MultiviewScreen(fullscreen: Boolean, onToggleFullscreen: () -> Unit, onClose: () -> Unit) {
    val app = FitzApp.instance
    val slots by Multiview.slots.collectAsState()
    val warning by com.fitztv.data.ConnectionGuard.warning.collectAsState()
    var sound by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(slots.size) { if (slots.isEmpty()) onClose() }
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusTarget().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.Escape, Key.Backspace -> { onClose(); true }
                Key.F, Key.F11 -> { onToggleFullscreen(); true }
                Key.One -> { sound = 0; true }; Key.Two -> { sound = 1; true }; Key.Three -> { sound = 2; true }; Key.Four -> { sound = 3; true }
                else -> false
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Layouts: 1 = full, 2 = side by side, 3 = one big + two small, 4 = 2x2.
            val rows: List<List<Int>> = when (slots.size) { 0, 1 -> listOf(listOf(0)); 2 -> listOf(listOf(0, 1)); 3 -> listOf(listOf(0), listOf(1, 2)); else -> listOf(listOf(0, 1), listOf(2, 3)) }
            rows.forEach { r ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.forEach { i ->
                        slots.getOrNull(i)?.let { slot ->
                            androidx.compose.runtime.key(slot.id) {
                                MultiviewTile(slot, selected = sound == i, onSelect = { sound = i }, onRemove = { Multiview.remove(i); if (sound >= Multiview.slots.value.size) sound = 0 },
                                    onFullscreen = { PlaybackQueue.launch(slot.channels, 0, slot.game?.id); Multiview.close() }, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Back", onClick = onClose, icon = Glyph.Back)
            NeonButton(if (fullscreen) "Exit full screen" else "Full screen", onClick = onToggleFullscreen, icon = Glyph.Fullscreen)
            if (slots.size < Multiview.MAX) Text("Add games from any game page › Streams › Multiview", color = Neon.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
        }
        warning?.let { Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(Neon.Live.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) }
    }
}

@Composable
private fun MultiviewTile(slot: com.fitztv.player.MultiviewSlot, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit, onFullscreen: () -> Unit, modifier: Modifier) {
    val app = FitzApp.instance
    val scope = rememberCoroutineScope()
    val controller = remember { PlaybackController(app, app.repo, app.settings, scope, multiview = true) }
    val st by controller.state.collectAsState()
    DisposableEffect(Unit) { onDispose { controller.release() } }
    LaunchedEffect(slot.id) { controller.play(slot.channels.first(), candidates = if (slot.game != null) slot.channels else emptyList()) }
    LaunchedEffect(selected) { controller.setVolume(if (selected) 1f else 0f) }
    val game = slot.game?.let { g -> app.repo.game(g.id) ?: g }
    Box(modifier.border(if (selected) androidx.compose.foundation.BorderStroke(2.dp, Neon.Purple) else androidx.compose.foundation.BorderStroke(1.dp, Neon.Line)).background(Color.Black)) {
        VideoSurface(st.engine, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().combinedClick(onSelect))
        if (st.buffering) CircularProgressIndicator(color = Neon.Purple, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(40.dp))
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))).padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) androidx.compose.material3.Icon(Glyph.Sound, null, tint = Neon.Purple, modifier = Modifier.size(16.dp).padding(end = 4.dp))
            val line = if (game != null && game.home.isNotBlank() && game.gameState != GameState.PRE) "${game.awayShort} ${game.awayScore ?: ""} – ${game.homeScore ?: ""} ${game.homeShort}  ${game.statusText}" else slot.title
            Text(line, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = Tabular)
            NeonButton("", onClick = onFullscreen, icon = Glyph.Big)
            Spacer(Modifier.width(4.dp))
            NeonButton("", onClick = onRemove, icon = Glyph.Remove)
        }
    }
}

private fun Modifier.combinedClick(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures { onClick() } }
