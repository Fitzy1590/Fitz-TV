package com.fitztv.player

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.Fmt
import com.fitztv.ui.components.LiveStatsPanel
import com.fitztv.ui.components.LiveDot
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.FitzTheme
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Panel { NONE, GAMES, CHANNELS, CONTROLS }

data class Overlay(
    val panel: Panel = Panel.NONE,
    val info: Boolean = true,
    val queueVersion: Int = 0,
    /** Live stats panel (non-focusable, so ▲▼ still switch channels while it's open). */
    val stats: Boolean = false,
    /** Stats page: summary / players / line-ups (◀ ▶ while the stats panel is open). */
    val statsPage: Int = 0,
)

class PlayerActivity : ComponentActivity() {
    private val app get() = FitzApp.instance
    private lateinit var controller: PlaybackController
    private val overlay = MutableStateFlow(Overlay())
    private var infoJob: Job? = null
    private var lastChannelIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller = PlaybackController(this, app.repo, app.settings, lifecycleScope)
        lifecycleScope.launch {
            app.alerts.events.collect { a ->
                if (a.game.id == PlaybackQueue.gameId) return@collect
                if (a.kind == com.fitztv.alerts.AppAlert.Kind.TEAM_STARTING && app.settings.value.autoSwitchToTeam) { playGame(a.game); return@collect }
                alert.value = a
                delay(12_000)
                if (alert.value == a) alert.value = null
            }
        }
        lifecycleScope.launch {
            controller.state.collect { app.alerts.watchingGameId = PlaybackQueue.gameId }
        }
        lifecycleScope.launch {
            controller.state.distinctUntilChangedBy { it.fps }.collect { if (it.fps > 0) matchDisplayMode(it.fps) }
        }
        setContent {
            FitzTheme {
                PlayerScreen(
                    controller = controller,
                    overlayFlow = overlay,
                    onClosePanel = { overlay.update { it.copy(panel = Panel.NONE) } },
                    onPickChannel = { i -> overlay.update { it.copy(panel = Panel.NONE) }; playIndex(i) },
                    onPickGame = { g -> playGame(g) },
                    controlsTick = controlsTick,
                    onToggleStats = ::toggleStats,
                    onFromStart = { controller.playFromStart(currentGame()) },
                    onMultiview = ::openMultiview,
                    onRecord = ::recordNow,
                    alertFlow = alert,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playIndex(PlaybackQueue.index)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        playIndex(PlaybackQueue.index)
    }

    override fun onStop() {
        super.onStop()
        controller.release()   // never keep streaming in the background
    }

    override fun onDestroy() {
        super.onDestroy()
        resetDisplayMode()
    }

    private fun playIndex(i: Int) {
        val q = PlaybackQueue.channels
        if (q.isEmpty()) { finish(); return }
        val idx = i.mod(q.size)
        if (idx != PlaybackQueue.index) lastChannelIndex = PlaybackQueue.index
        PlaybackQueue.index = idx
        // Watching a game: the queue is the ranked stream list, so failures fall through it in order.
        controller.play(q[idx], candidates = if (PlaybackQueue.gameId != null) q else emptyList())
        showInfo()
    }

    private fun zap(delta: Int) = playIndex(PlaybackQueue.index + delta)

    private fun playGame(game: Game) {
        lifecycleScope.launch {
            controller.flash("Finding the best stream…")
            val route = app.repo.route(this@PlayerActivity, game)
            val matches = (route as? com.fitztv.data.Repository.Route.Streams)?.ranked.orEmpty()
            if (matches.isEmpty()) {
                overlay.update { it.copy(panel = Panel.NONE) }
                controller.flash("No channel found for ${game.title}")
                return@launch
            }
            PlaybackQueue.replace(matches, 0, game.id)
            lastChannelIndex = -1
            overlay.update { it.copy(panel = Panel.NONE, queueVersion = it.queueVersion + 1) }
            playIndex(0)
        }
    }

    /** Score / reminder banner: OK while it's showing switches to that game. */
    private val alert = MutableStateFlow<com.fitztv.alerts.AppAlert?>(null)

    private fun openMultiview() {
        val q = PlaybackQueue.channels
        if (q.isEmpty()) return
        val game = currentGame()
        Multiview.slotFor(game, if (game != null) q else listOf(q[PlaybackQueue.index]) + q.filterIndexed { i, _ -> i != PlaybackQueue.index })?.let { Multiview.add(it) }
        controller.release()   // free this connection before the multiview windows start
        Multiview.open(this)
        finish()
    }

    private fun recordNow() {
        val q = PlaybackQueue.channels
        val cur = controller.state.value.channel ?: return
        val game = currentGame()
        val list = listOf(cur) + q.filter { it.id != cur.id }
        val rec = if (game != null) com.fitztv.record.Recorder.scheduleGame(this, game, list)
        else com.fitztv.record.Recorder.schedule(this, cur.label, cur.sport.name, list, System.currentTimeMillis(), System.currentTimeMillis() + 3 * 3_600_000L)
        controller.flash("Recording ${rec.title} — find it in Replays › My recordings")
    }

    private fun toggleStats() {
        if (currentGame() != null) overlay.update { it.copy(stats = !it.stats) } else controller.flash("No live stats for this channel")
        showInfo()
    }

    /** The game being watched: the one picked, or the live game this channel is showing. */
    private fun currentGame(): Game? =
        PlaybackQueue.gameId?.let { app.repo.game(it) } ?: controller.state.value.channel?.let { app.repo.gameFor(it) }

    private fun showInfo() {
        overlay.update { it.copy(info = true) }
        infoJob?.cancel()
        infoJob = lifecycleScope.launch {
            delay(5_000)
            overlay.update { it.copy(info = false) }
        }
    }

    private val controlsTick = MutableStateFlow(0)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            controlsTick.value++
            // Transport keys work everywhere (remote play/pause, rewind, fast-forward; 3 s per press, hold to repeat).
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { controller.togglePause(); showInfo(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { controller.skip(-3_000); showInfo(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { controller.skip(3_000); showInfo(); return true }
            }
        }
        if (overlay.value.panel == Panel.NONE && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(+1); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (overlay.value.stats) {
                    val d = if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                    overlay.update { it.copy(statsPage = it.statsPage + d) }; return true
                }
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE -> {
                    overlay.update { it.copy(panel = Panel.GAMES) }; return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> {
                    overlay.update { it.copy(panel = Panel.CHANNELS) }; return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    alert.value?.let { a -> alert.value = null; playGame(a.game); return true }
                    // OK opens the playback controls (pause, ±3 s, from start, live, stats).
                    overlay.update { it.copy(panel = Panel.CONTROLS) }; showInfo()
                    return true
                }
                KeyEvent.KEYCODE_INFO -> { toggleStats(); return true }
                KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (lastChannelIndex >= 0) playIndex(lastChannelIndex); return true
                }
            }
        }
        if (overlay.value.panel == Panel.NONE && event.action == KeyEvent.ACTION_UP &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
        ) return true
        return super.dispatchKeyEvent(event)
    }

    // ---- refresh-rate matching (e.g. 50 Hz for European/Australian broadcasts) -----------

    @Suppress("DEPRECATION")
    private fun matchDisplayMode(fps: Float) {
        if (!app.settings.value.matchFrameRate) return
        val display: Display = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        val cur = display.mode
        fun fit(rate: Float): Float {
            val ratio = rate / fps
            val n = ratio.roundToInt()
            return if (n < 1 || n > 2) Float.MAX_VALUE else abs(ratio - n)
        }
        val best = display.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .filter { fit(it.refreshRate) < 0.01f }
            .minByOrNull { (fit(it.refreshRate) * 1000).roundToInt() * 1000 + abs(it.refreshRate - 60f).roundToInt() }
            ?: return
        if (fit(cur.refreshRate) < 0.01f || best.modeId == cur.modeId) return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    private fun resetDisplayMode() {
        runCatching { window.attributes = window.attributes.also { it.preferredDisplayModeId = 0 } }
    }
}

@Composable
private fun PlayerScreen(
    controller: PlaybackController,
    overlayFlow: MutableStateFlow<Overlay>,
    onClosePanel: () -> Unit,
    onPickChannel: (Int) -> Unit,
    onPickGame: (Game) -> Unit,
    controlsTick: MutableStateFlow<Int>,
    onToggleStats: () -> Unit,
    onFromStart: () -> Unit,
    onMultiview: () -> Unit,
    onRecord: () -> Unit,
    alertFlow: MutableStateFlow<com.fitztv.alerts.AppAlert?>,
) {
    val st by controller.state.collectAsState()
    val ov by overlayFlow.collectAsState()
    val app = FitzApp.instance
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val zone = settings.zone()
    val game = remember(games, st.channel?.id) {
        PlaybackQueue.gameId?.let { id -> games.firstOrNull { it.id == id } } ?: st.channel?.let { app.repo.gameFor(it) }
    }

    BackHandler(enabled = ov.panel != Panel.NONE) { onClosePanel() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        st.engine?.let { engine ->
            key(engine) {
                AndroidView(
                    factory = { engine.view.also { v -> (v.parent as? ViewGroup)?.removeView(v) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (st.buffering) {
            CircularProgressIndicator(
                color = Neon.Purple, trackColor = Neon.Purple.copy(alpha = 0.15f), strokeWidth = 5.dp,
                modifier = Modifier.align(Alignment.Center).size(72.dp),
            )
        }

        // ---- info bar -----------------------------------------------------------------------
        AnimatedVisibility(ov.info || st.message != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Neon.Bg.copy(alpha = 0.92f))))
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp)
            ) {
                st.message?.let { Text(it, color = Neon.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                if (game != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (game.gameState == GameState.LIVE) { LiveDot(); Spacer(Modifier.width(8.dp)) }
                        val score = if (game.home.isNotBlank() && game.gameState != GameState.PRE)
                            "${game.away} ${game.awayScore ?: ""}  –  ${game.homeScore ?: ""} ${game.home}"
                        else game.matchup
                        Text(score, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text, style = com.fitztv.ui.components.Tabular)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (game.gameState == GameState.PRE) Fmt.whenLabel(game, zone, settings.use24h) else game.statusText,
                            color = if (game.gameState == GameState.LIVE) Neon.Live else Neon.TextDim, fontSize = 15.sp, style = com.fitztv.ui.components.Clock,
                        )
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
                        st.decoder?.let { Pill(if (it.startsWith("c2.android") || it.startsWith("OMX.google")) "SW decode" else "HW decode", Neon.TextDim); Spacer(Modifier.width(8.dp)) }
                        if (com.fitztv.data.StreamLanguage.tier(ch) == com.fitztv.data.StreamLanguage.OTHER) { Pill("Other language", Neon.TextDim); Spacer(Modifier.width(8.dp)) }
                        if (st.degraded) Pill("Safe mode", Neon.TextDim)
                    }
                    app.repo.nowPlaying(ch)?.let { p ->
                        Text("Now: ${p.title}", color = Neon.TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "${if (PlaybackQueue.gameId != null) "Stream ${PlaybackQueue.index + 1} of ${PlaybackQueue.channels.size} (best first)" else "${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}"}" +
                            "   ·   ▲▼ switch   ◀ live games   ▶ streams   OK controls",
                        color = Neon.TextFaint, fontSize = 13.sp,
                    )
                }
            }
        }

        // ---- live stats (top right, auto-updating) ---------------------------------------
        if (game != null) {
            AnimatedVisibility(
                ov.stats && (ov.panel == Panel.NONE || ov.panel == Panel.CONTROLS),
                enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) { LiveStatsPanel(game, ov.statsPage) }
        }

        // ---- alert banner (OK = watch) -------------------------------------------------------------
        val al by alertFlow.collectAsState()
        al?.let { a ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                    .background(Neon.Bg.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, Neon.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.text, color = Neon.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Text("OK to watch", color = Neon.TextDim, fontSize = 13.sp)
            }
        }

        // ---- paused badge ---------------------------------------------------------------------
        if (st.paused) {
            Row(Modifier.align(Alignment.Center).background(Neon.Bg.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(com.fitztv.ui.components.Glyph.Pause, null, tint = Neon.Text, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Paused", color = Neon.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ---- transport controls (OK) -------------------------------------------------------
        if (ov.panel == Panel.CONTROLS) {
            val tick by controlsTick.collectAsState()
            LaunchedEffect(tick) { delay(8_000); onClosePanel() }
            TransportBar(st, game != null, onFromStart, onToggleStats, controller, Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp), onMultiview, onRecord)
        }

        // ---- games panel (left) -------------------------------------------------------------
        AnimatedVisibility(
            ov.panel == Panel.GAMES, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            // Other live games only (favourites first, then the guide's leagues).
            val list = remember(games, settings) {
                FitzApp.instance.repo.liveNow(null).filter { it.id != PlaybackQueue.gameId }
            }
            SidePanel("Live now") {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (list.isEmpty()) item { Text("No other games are live right now", color = Neon.TextDim) }
                    itemsIndexed(list, key = { _, g -> g.id }) { i, g ->
                        NeonSurface(
                            onClick = { onPickGame(g) },
                            modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(fr) else Modifier),
                            radius = 10.dp, selected = g.id == PlaybackQueue.gameId,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.tv.material3.Icon(com.fitztv.ui.components.Glyph.sport(g.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(g.league, color = Neon.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (g.gameState == GameState.LIVE) com.fitztv.ui.components.LiveClock(g.statusText)
                                    else Text(Fmt.time(g.startUtcMillis, zone, settings.use24h), color = Neon.TextDim, fontSize = 13.sp, style = com.fitztv.ui.components.Tabular)
                                }
                                val line = if (g.home.isNotBlank() && g.gameState == GameState.LIVE)
                                    "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}"
                                else g.matchup
                                Text(line, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, style = com.fitztv.ui.components.Tabular)
                            }
                        }
                    }
                }
            }
        }

        // ---- channel panel (right) ----------------------------------------------------------
        AnimatedVisibility(
            ov.panel == Panel.CHANNELS, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val q: List<Channel> = remember(ov.queueVersion, st.channel) { PlaybackQueue.channels }
            SidePanel(if (game != null) "Streams for ${game.matchup} — best first" else "Channels") {
                val fr = remember { FocusRequester() }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (PlaybackQueue.index - 2).coerceAtLeast(0))
                LaunchedEffect(Unit) { delay(50); runCatching { fr.requestFocus() } }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(q, key = { _, c -> c.id }) { i, c ->
                        val current = i == PlaybackQueue.index
                        NeonSurface(
                            onClick = { onPickChannel(i) },
                            modifier = Modifier.fillMaxWidth().then(if (current) Modifier.focusRequester(fr) else Modifier),
                            radius = 10.dp, selected = current,
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", color = Neon.TextFaint, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val lang = when (com.fitztv.data.StreamLanguage.tier(c)) { com.fitztv.data.StreamLanguage.ENGLISH -> "English"; com.fitztv.data.StreamLanguage.OTHER -> "Other language"; else -> null }
                                    val sub = listOfNotNull(lang, FitzApp.instance.repo.nowPlaying(c)?.title).joinToString(" · ")
                                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(FitzApp.instance.repo.probe.label(c), color = if (FitzApp.instance.repo.probe.isMeasured(c)) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = com.fitztv.ui.components.Tabular)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidePanel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.width(440.dp).fillMaxHeight()
            .background(Neon.Bg.copy(alpha = 0.94f), RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun TransportBar(
    st: PlayerState, hasGame: Boolean, onFromStart: () -> Unit, onStats: () -> Unit,
    controller: PlaybackController, modifier: Modifier, onMultiview: () -> Unit, onRecord: () -> Unit,
) {
    val play = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { play.requestFocus() } }
    Column(
        modifier.width(900.dp).background(Neon.Bg.copy(alpha = 0.82f), RoundedCornerShape(16.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimelineRow(st)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportButton("Start", onFromStart, icon = com.fitztv.ui.components.Glyph.Start)
            TransportButton("3s", { controller.skip(-3_000) }, icon = com.fitztv.ui.components.Glyph.Back)
            TransportButton(if (st.paused) "Play" else "Pause", { controller.togglePause() }, Modifier.focusRequester(play), icon = if (st.paused) com.fitztv.ui.components.Glyph.Play else com.fitztv.ui.components.Glyph.Pause)
            TransportButton("3s", { controller.skip(3_000) }, icon = com.fitztv.ui.components.Glyph.Forward)
            TransportButton(if (st.mode == PlayMode.LIVE && st.timeline.let { !it.seekable || it.durationMs - it.positionMs < 5_000 }) "Live" else "Go live", { controller.goLive() }, icon = com.fitztv.ui.components.Glyph.GoLive)
            if (hasGame) TransportButton("Stats", onStats, icon = com.fitztv.ui.components.Glyph.Stats)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TransportButton("Multiview", onMultiview, icon = com.fitztv.ui.components.Glyph.Multiview)
            if (st.mode == PlayMode.LIVE) TransportButton("Record", onRecord, icon = com.fitztv.ui.components.Glyph.Record)
        }
    }
}

@Composable
private fun TransportButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    NeonSurface(onClick = onClick, modifier = modifier, radius = 8.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { androidx.tv.material3.Icon(icon, null, tint = Neon.Text, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Neon.Text)
        }
    }
}

/** "LIVE", "-0:45 behind live", the archive's clock time, or replay position / length, plus a progress bar. */
@Composable
private fun TimelineRow(st: PlayerState) {
    val t = st.timeline
    val settings by FitzApp.instance.settings.state.collectAsState()
    fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
    val label = when (st.mode) {
        PlayMode.VOD -> "${mmss(t.positionMs)} / ${mmss(t.durationMs)}"
        PlayMode.ARCHIVE -> "Catch-up · watching ${Fmt.time(st.archiveStartMs + t.positionMs, settings.zone(), settings.use24h)} · " +
            "${mmss(System.currentTimeMillis() - st.archiveStartMs - t.positionMs)} behind live"
        PlayMode.LIVE -> if (t.seekable && t.durationMs - t.positionMs > 5_000) "${mmss(t.durationMs - t.positionMs)} behind live" else "Live"
    }
    Text(label, color = if (label == "Live") Neon.Live else Neon.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = com.fitztv.ui.components.Clock)
    if (t.durationMs > 0 && (st.mode != PlayMode.LIVE || t.seekable)) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(Neon.SurfaceHi, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth((t.positionMs.toFloat() / t.durationMs).coerceIn(0f, 1f)).height(6.dp).background(Neon.Purple, RoundedCornerShape(3.dp)))
        }
    }
}
