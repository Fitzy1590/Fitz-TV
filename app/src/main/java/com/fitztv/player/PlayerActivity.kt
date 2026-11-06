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

enum class Panel { NONE, GAMES, CHANNELS }

data class Overlay(
    val panel: Panel = Panel.NONE,
    val info: Boolean = true,
    val queueVersion: Int = 0,
    /** Live stats panel (non-focusable, so ▲▼ still switch channels while it's open). */
    val stats: Boolean = false,
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
            val matches = app.repo.rankedStreams(game)
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

    private fun showInfo() {
        overlay.update { it.copy(info = true) }
        infoJob?.cancel()
        infoJob = lifecycleScope.launch {
            delay(5_000)
            overlay.update { it.copy(info = false) }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (overlay.value.panel == Panel.NONE && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(+1); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE -> {
                    overlay.update { it.copy(panel = Panel.GAMES) }; return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> {
                    overlay.update { it.copy(panel = Panel.CHANNELS) }; return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // OK toggles live stats when watching a game, otherwise the info bar.
                    if (PlaybackQueue.gameId != null) { overlay.update { it.copy(stats = !it.stats) }; showInfo() }
                    else if (overlay.value.info) overlay.update { it.copy(info = false) } else showInfo()
                    return true
                }
                KeyEvent.KEYCODE_INFO -> {
                    if (overlay.value.info) overlay.update { it.copy(info = false) } else showInfo(); return true
                }
                KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (lastChannelIndex >= 0) playIndex(lastChannelIndex); return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> { controller.retry(); return true }
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
) {
    val st by controller.state.collectAsState()
    val ov by overlayFlow.collectAsState()
    val app = FitzApp.instance
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val zone = settings.zone()
    val game = PlaybackQueue.gameId?.let { id -> games.firstOrNull { it.id == id } }

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
                st.message?.let { Text(it, color = Neon.Magenta, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (game != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (game.gameState == GameState.LIVE) { LiveDot(); Spacer(Modifier.width(8.dp)) }
                        val score = if (game.home.isNotBlank() && game.gameState != GameState.PRE)
                            "${game.away} ${game.awayScore ?: ""}  –  ${game.homeScore ?: ""} ${game.home}"
                        else game.matchup
                        Text(score, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (game.gameState == GameState.PRE) Fmt.whenLabel(game, zone, settings.use24h) else game.statusText,
                            color = Neon.Cyan, fontSize = 15.sp,
                        )
                    }
                }
                st.channel?.let { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ch.label, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(14.dp))
                        Pill(if (st.height > 0) Fmt.resolution(st.height) else Fmt.quality(ch.quality), Neon.Cyan)
                        Spacer(Modifier.width(8.dp))
                        if (st.fps > 0) { Pill("${(st.fps * 100).roundToInt() / 100f} fps", Neon.TextDim); Spacer(Modifier.width(8.dp)) }
                        Pill(st.engine?.name ?: "", Neon.Purple)
                        Spacer(Modifier.width(8.dp))
                        st.decoder?.let { Pill(if (it.startsWith("c2.android") || it.startsWith("OMX.google")) "SW decode" else "HW decode", Neon.TextDim); Spacer(Modifier.width(8.dp)) }
                        if (st.degraded) Pill("SAFE MODE", Neon.Magenta)
                    }
                    app.repo.nowPlaying(ch)?.let { p ->
                        Text("Now: ${p.title}", color = Neon.TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "${if (PlaybackQueue.gameId != null) "Stream ${PlaybackQueue.index + 1} of ${PlaybackQueue.channels.size} (best first)" else "${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}"}" +
                            "    ▲▼ switch   ◀ games & my teams   ▶ streams   OK ${if (game != null) "live stats" else "info"}",
                        color = Neon.TextFaint, fontSize = 13.sp,
                    )
                }
            }
        }

        // ---- live stats (top right, auto-updating) ---------------------------------------
        if (game != null) {
            AnimatedVisibility(
                ov.stats && ov.panel == Panel.NONE, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp),
            ) { LiveStatsPanel(game) }
        }

        // ---- games panel (left) -------------------------------------------------------------
        AnimatedVisibility(
            ov.panel == Panel.GAMES, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            val now = System.currentTimeMillis()
            val list = remember(games) {
                games.filter {
                    it.gameState == GameState.LIVE ||
                        (it.gameState == GameState.PRE && it.startUtcMillis - now < 12 * 3_600_000L)
                }.sortedWith(compareBy<Game> { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
            }
            val favs = remember(games, settings.favoriteTeams) { com.fitztv.ui.screens.favouriteEntries(games, settings, now) }
            SidePanel("My teams & games") {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (favs.isNotEmpty()) {
                        item { Text("★  My teams", color = Neon.Magenta, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        itemsIndexed(favs, key = { _, e -> "fav:" + e.team }) { i, e ->
                            Box(if (i == 0) Modifier.focusRequester(fr) else Modifier) {
                                com.fitztv.ui.screens.FavouriteCard(e, settings, onClick = { e.game?.let(onPickGame) })
                            }
                        }
                        item { Spacer(Modifier.height(6.dp)); Text("Live & next 12 hours", color = Neon.Purple, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                    if (list.isEmpty()) item { Text("No games right now", color = Neon.TextDim) }
                    itemsIndexed(list, key = { _, g -> g.id }) { i, g ->
                        NeonSurface(
                            onClick = { onPickGame(g) },
                            modifier = Modifier.fillMaxWidth().then(if (i == 0 && favs.isEmpty()) Modifier.focusRequester(fr) else Modifier),
                            radius = 10.dp, selected = g.id == PlaybackQueue.gameId,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Pill("${g.sport.emoji} ${g.league}", Neon.Purple)
                                    Spacer(Modifier.weight(1f))
                                    if (g.gameState == GameState.LIVE) {
                                        LiveDot(); Spacer(Modifier.width(4.dp))
                                        Text(g.statusText, color = Neon.Live, fontSize = 12.sp, maxLines = 1)
                                    } else Text(Fmt.time(g.startUtcMillis, zone, settings.use24h), color = Neon.Cyan, fontSize = 12.sp)
                                }
                                val line = if (g.home.isNotBlank() && g.gameState == GameState.LIVE)
                                    "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}"
                                else g.matchup
                                Text(line, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                                    Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    FitzApp.instance.repo.nowPlaying(c)?.let { Text(it.title, fontSize = 12.sp, color = Neon.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                                Pill(Fmt.quality(c.quality), Neon.Cyan)
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
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Purple)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Neon.glowBar))
        Spacer(Modifier.height(14.dp))
        content()
    }
}
