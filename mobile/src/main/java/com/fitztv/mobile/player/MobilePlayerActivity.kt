package com.fitztv.mobile.player

import com.fitztv.mobile.ui.Glyph
import com.fitztv.mobile.ui.Tabular
import com.fitztv.mobile.ui.LiveClock

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.data.favouriteEntries
import com.fitztv.mobile.ui.FavouriteCard
import com.fitztv.mobile.ui.FitzMobileTheme
import com.fitztv.mobile.ui.LiveDot
import com.fitztv.mobile.ui.LiveStatsCard
import com.fitztv.mobile.ui.Neon
import com.fitztv.mobile.ui.NeonCard
import com.fitztv.mobile.ui.Pill
import com.fitztv.player.PlaybackController
import com.fitztv.player.PlaybackQueue
import com.fitztv.player.PlayerState
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Fold posture for the Galaxy Z Fold: half-open with a horizontal hinge = tabletop (Flex mode). */
data class Posture(val tabletop: Boolean = false, val hingeTopPx: Int = 0)

class MobilePlayerActivity : ComponentActivity() {
    private val app get() = FitzApp.instance
    private lateinit var controller: PlaybackController
    private val posture = MutableStateFlow(Posture())
    private val inPip = MutableStateFlow(false)
    private val queueVersion = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        controller = PlaybackController(this, app.repo, app.settings, lifecycleScope)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MobilePlayerActivity)
                    .windowLayoutInfo(this@MobilePlayerActivity)
                    .collect { info ->
                        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                        posture.value = if (fold != null && fold.state == FoldingFeature.State.HALF_OPENED &&
                            fold.orientation == FoldingFeature.Orientation.HORIZONTAL
                        ) Posture(true, fold.bounds.top) else Posture()
                    }
            }
        }
        lifecycleScope.launch {
            app.alerts.events.collect { a ->
                if (a.game.id == PlaybackQueue.gameId) return@collect
                if (a.kind == com.fitztv.alerts.AppAlert.Kind.TEAM_STARTING && app.settings.value.autoSwitchToTeam) { playGame(a.game); return@collect }
                alert.value = a
                kotlinx.coroutines.delay(12_000)
                if (alert.value == a) alert.value = null
            }
        }
        lifecycleScope.launch { controller.state.collect { app.alerts.watchingGameId = PlaybackQueue.gameId } }
        com.fitztv.mobile.ui.ClipBus.clip.value = null
        // Highlight over the game: mute the game while it plays, sound back afterwards.
        lifecycleScope.launch { com.fitztv.mobile.ui.ClipBus.clip.collect { controller.setVolume(if (it != null) 0f else 1f) } }
        lifecycleScope.launch {
            controller.state.distinctUntilChangedBy { it.width to it.height }.collect { st -> if (st.height > 0) updatePipParams(st) }
        }

        setContent {
            FitzMobileTheme {
                PlayerUi(
                    controller = controller,
                    posture = posture,
                    inPip = inPip,
                    queueVersion = queueVersion,
                    onBack = { finish() },
                    onPlayIndex = ::playIndex,
                    onPlayGame = ::playGame,
                    onPip = ::enterPip,
                    onMultiview = ::openMultiview,
                    onRecord = ::recordNow,
                    alertFlow = alert,
                    setImmersive = ::applyImmersive,
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
        queueVersion.value++
        playIndex(PlaybackQueue.index)
    }

    override fun onStop() {
        super.onStop()
        controller.release() // nothing streams once the player is gone (incl. closing PiP)
    }

    private fun playIndex(i: Int) {
        val q = PlaybackQueue.channels
        if (q.isEmpty()) { finish(); return }
        val idx = i.mod(q.size)
        PlaybackQueue.index = idx
        controller.play(q[idx], candidates = if (PlaybackQueue.gameId != null) q else emptyList())
    }

    private val alert = MutableStateFlow<com.fitztv.alerts.AppAlert?>(null)

    private fun openMultiview() {
        val q = PlaybackQueue.channels
        if (q.isEmpty()) return
        val game = PlaybackQueue.gameId?.let { app.repo.game(it) }
        com.fitztv.player.Multiview.slotFor(game, if (game != null) q else listOf(q[PlaybackQueue.index]) + q.filterIndexed { i, _ -> i != PlaybackQueue.index })
            ?.let { com.fitztv.player.Multiview.add(it) }
        controller.release()   // free this connection before the multiview windows start
        com.fitztv.player.Multiview.open(this)
        finish()
    }

    private fun recordNow() {
        val cur = controller.state.value.channel ?: return
        val game = PlaybackQueue.gameId?.let { app.repo.game(it) } ?: app.repo.gameFor(cur)
        val list = listOf(cur) + PlaybackQueue.channels.filter { it.id != cur.id }
        val rec = if (game != null) com.fitztv.record.Recorder.scheduleGame(this, game, list)
        else com.fitztv.record.Recorder.schedule(this, cur.label, cur.sport.name, list, System.currentTimeMillis(), System.currentTimeMillis() + 3 * 3_600_000L)
        controller.flash("Recording ${rec.title} — see Replays › My recordings")
    }

    private fun playGame(game: Game) {
        lifecycleScope.launch {
            controller.flash("Finding the best stream…")
            val route = app.repo.route(this@MobilePlayerActivity, game)
            val ranked = (route as? com.fitztv.data.Repository.Route.Streams)?.ranked.orEmpty()
            if (ranked.isEmpty()) { controller.flash("No channel found for ${game.matchup}"); return@launch }
            PlaybackQueue.replace(ranked, 0, game.id)
            queueVersion.value++
            playIndex(0)
        }
    }

    // ---- Picture-in-picture --------------------------------------------------------------

    private fun pipParams(st: PlayerState?): PictureInPictureParams {
        val w = st?.width?.takeIf { it > 0 } ?: 16
        val h = st?.height?.takeIf { it > 0 } ?: 9
        val ratio = (w.toFloat() / h).coerceIn(0.42f, 2.38f) // platform limits
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(true).setSeamlessResizeEnabled(true)
        return b.build()
    }

    private fun updatePipParams(st: PlayerState) = runCatching { setPictureInPictureParams(pipParams(st)) }

    private fun enterPip() { runCatching { enterPictureInPictureMode(pipParams(controller.state.value)) } }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && controller.state.value.engine != null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    private fun applyImmersive(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (on) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private enum class PanelTab(val label: String) { STATS("Live stats"), MOMENTUM("Momentum"), STREAMS("Streams"), GAMES("Live now") }

@Composable
private fun PlayerUi(
    controller: PlaybackController,
    posture: StateFlow<Posture>,
    inPip: StateFlow<Boolean>,
    queueVersion: StateFlow<Int>,
    onBack: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onPlayGame: (Game) -> Unit,
    onPip: () -> Unit,
    onMultiview: () -> Unit,
    onRecord: () -> Unit,
    alertFlow: MutableStateFlow<com.fitztv.alerts.AppAlert?>,
    setImmersive: (Boolean) -> Unit,
) {
    val st by controller.state.collectAsState()
    val fold by posture.collectAsState()
    val pip by inPip.collectAsState()
    val qv by queueVersion.collectAsState()
    val app = FitzApp.instance
    val games by app.repo.games.collectAsState()
    val game = remember(games, qv, st.channel?.id) {
        PlaybackQueue.gameId?.let { id -> games.firstOrNull { it.id == id } } ?: st.channel?.let { app.repo.gameFor(it) }
    }

    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    val wide = cfg.screenWidthDp >= 600
    val fullscreen = landscape && !fold.tabletop
    LaunchedEffect(fullscreen, pip) { setImmersive(fullscreen || pip) }

    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, tick) { if (controls) { delay(4_000); controls = false } }
    var sidePanel by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(if (PlaybackQueue.gameId != null) PanelTab.STATS else PanelTab.STREAMS) }
    // Landscape: open straight onto live stats when there's a game.
    LaunchedEffect(sidePanel) { if (sidePanel && game != null) tab = PanelTab.STATS }
    BackHandler(enabled = sidePanel && fullscreen) { sidePanel = false }

    // The video surface is moved, never recreated, when the layout changes (rotate / fold / unfold).
    val video = remember {
        movableContentOf { s: PlayerState, m: Modifier ->
            VideoSurface(
                s, m, onTap = { controls = !controls; tick++ }, onSwipe = { d -> onPlayIndex(PlaybackQueue.index + d); tick++ },
                onDoubleTap = { fwd -> controller.skip(if (fwd) 3_000 else -3_000); controls = true; tick++ },
            )
        }
    }
    val overlay: @Composable BoxScope.() -> Unit = {
        if (!pip) Controls(
            controller = controller, onActivity = { tick++ },
            visible = controls, st = st, game = game, compact = !fullscreen,
            onBack = onBack, onPrev = { onPlayIndex(PlaybackQueue.index - 1) }, onNext = { onPlayIndex(PlaybackQueue.index + 1) },
            onPanel = { if (fullscreen) sidePanel = !sidePanel }, onPip = onPip, showPanelButton = fullscreen,
            onMultiview = onMultiview, onRecord = onRecord,
        )
        // Who just scored, with their stats.
        if (!pip) com.fitztv.mobile.ui.ScoreAnimationHost(game, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = if (fullscreen) 72.dp else 12.dp), compact = !fullscreen)
    }
    val panel: @Composable (Modifier) -> Unit = { m ->
        SidePanel(tab, { tab = it }, game, onPlayIndex, onPlayGame, qv, m, twoColumn = wide && !fullscreen && !fold.tabletop)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            pip -> Box(Modifier.fillMaxSize()) { video(st, Modifier.fillMaxSize()) }

            fold.tabletop -> {
                // Flex mode: picture on the top half, stats and streams on the bottom half.
                val density = LocalDensity.current
                val topDp = with(density) { fold.hingeTopPx.toDp() }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(topDp).statusBarsPadding()) { video(st, Modifier.fillMaxSize()); overlay() }
                    panel(Modifier.fillMaxWidth().weight(1f).background(Neon.Bg).navigationBarsPadding())
                }
            }

            fullscreen -> {
                Box(Modifier.fillMaxSize()) {
                    video(st, Modifier.fillMaxSize())
                    overlay()
                    AnimatedVisibility(
                        sidePanel, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        panel(
                            // See-through so the game stays visible behind the box score.
                            Modifier.width(if (wide) 400.dp else 340.dp).fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(Neon.Bg.copy(alpha = 0.35f), Neon.Bg.copy(alpha = 0.7f))))
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                        )
                    }
                }
            }

            else -> {
                // Portrait (cover screen or unfolded): video on top, info + panel below.
                Column(Modifier.fillMaxSize().background(Neon.Bg)) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().aspectRatio(16f / 9f).background(Color.Black)) {
                        video(st, Modifier.fillMaxSize()); overlay()
                    }
                    NowPlayingBar(st, game, onPrev = { onPlayIndex(PlaybackQueue.index - 1) }, onNext = { onPlayIndex(PlaybackQueue.index + 1) })
                    panel(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding())
                }
            }
        }
        // Reminder / score alert: tap to switch to that game.
        val al by alertFlow.collectAsState()
        al?.let { a ->
            Box(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)
                    .background(Neon.Bg.copy(alpha = 0.92f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .border(2.dp, Neon.Magenta, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { alertFlow.value = null; onPlayGame(a.game) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) { Text(a.text + "  • Tap to watch", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun VideoSurface(st: PlayerState, modifier: Modifier, onTap: () -> Unit, onSwipe: (Int) -> Unit, onDoubleTap: (Boolean) -> Unit) {
    Box(
        modifier
            .background(Color.Black)
            // Tap = controls; double-tap left/right half = back/forward 3 s.
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { o -> onDoubleTap(o.x > size.width / 2) }) }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (kotlin.math.abs(total) > 160f) onSwipe(if (total < 0) 1 else -1) },
                    onHorizontalDrag = { _, d -> total += d },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        st.engine?.let { engine ->
            key(engine) {
                AndroidView(factory = { engine.view.also { v -> (v.parent as? ViewGroup)?.removeView(v) } }, modifier = Modifier.fillMaxSize())
                // Highlight over the game: game muted meanwhile, sound back when it ends or is closed.
                val clip by com.fitztv.mobile.ui.ClipBus.clip.collectAsState()
                clip?.let { c -> com.fitztv.mobile.ui.MobileClipOverlay(c, onDone = { com.fitztv.mobile.ui.ClipBus.clip.value = null }, modifier = Modifier.fillMaxSize()) }
            }
        }
        if (st.buffering) CircularProgressIndicator(color = Neon.Purple, trackColor = Neon.Purple.copy(alpha = 0.15f), strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun BoxScope.Controls(
    controller: PlaybackController, onActivity: () -> Unit,
    visible: Boolean, st: PlayerState, game: Game?, compact: Boolean, showPanelButton: Boolean,
    onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onPanel: () -> Unit, onPip: () -> Unit,
    onMultiview: () -> Unit, onRecord: () -> Unit,
) {
    if (st.paused && !visible) {
        Box(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Paused", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
    AnimatedVisibility(visible || st.message != null || st.paused, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.6f))))) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart).then(if (compact) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        game?.awayLogo?.let { coil.compose.AsyncImage(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
                        Text(
                            if (game != null && game.home.isNotBlank() && game.gameState != com.fitztv.data.GameState.PRE)
                                "${game.awayShort.ifBlank { game.away }} ${game.awayScore ?: 0} – ${game.homeScore ?: 0} ${game.homeShort.ifBlank { game.home }}"
                            else game?.matchup ?: st.channel?.label.orEmpty(),
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Tabular,
                        )
                        game?.homeLogo?.let { Spacer(Modifier.width(6.dp)); coil.compose.AsyncImage(it, null, Modifier.size(20.dp)) }
                    }
                    if (game != null) Text(st.channel?.label.orEmpty(), color = Neon.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                var syncOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                IconButton(onClick = { syncOpen = true; onActivity() }) { Icon(Glyph.Sound, "Audio sync", tint = Color.White) }
                if (syncOpen) AudioSyncDialog(controller) { syncOpen = false }
                IconButton(onClick = onMultiview) { Icon(Icons.Outlined.GridView, "Multiview", tint = Color.White) }
                if (st.mode == com.fitztv.player.PlayMode.LIVE) IconButton(onClick = onRecord) { Icon(Icons.Outlined.FiberManualRecord, "Record", tint = Neon.Live) }
                var preferred by androidx.compose.runtime.remember(st.channel?.id) { androidx.compose.runtime.mutableStateOf(controller.isUpvoted()) }
                IconButton(onClick = { val msg = controller.toggleUpvote(); preferred = controller.isUpvoted(); controller.flash(msg); onActivity() }) {
                    Icon(if (preferred) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, if (preferred) "Preferred stream" else "Prefer this stream", tint = if (preferred) Neon.Purple else Color.White)
                }
                IconButton(onClick = onPip) { Icon(Icons.Outlined.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                if (showPanelButton) IconButton(onClick = onPanel) { Icon(if (game != null) Icons.Outlined.BarChart else Icons.Outlined.ViewList, "Panel", tint = Color.White) }
            }
            // Transport: from start, back 3 s, play/pause, forward 3 s, live.
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val big = if (compact) 44.dp else 60.dp
                TextButton(onClick = { controller.playFromStart(game); onActivity() }) { Icon(Icons.Outlined.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Start", color = Color.White, fontSize = if (compact) 12.sp else 14.sp) }
                IconButton(onClick = { controller.skip(-3_000); onActivity() }, modifier = Modifier.size(big)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.FastRewind, "Back 3 seconds", tint = Color.White); Text("3s", color = Color.White, fontSize = 9.sp) }
                }
                IconButton(onClick = { controller.togglePause(); onActivity() }, modifier = Modifier.size(big + 8.dp).background(Neon.Purple.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)) {
                    Icon(if (st.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (st.paused) "Play" else "Pause", tint = Color.White, modifier = Modifier.size(if (compact) 28.dp else 36.dp))
                }
                IconButton(onClick = { controller.skip(3_000); onActivity() }, modifier = Modifier.size(big)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.FastForward, "Forward 3 seconds", tint = Color.White); Text("3s", color = Color.White, fontSize = 9.sp) }
                }
                val atLive = st.mode == com.fitztv.player.PlayMode.LIVE && st.timeline.let { !it.seekable || it.durationMs - it.positionMs < 5_000 }
                TextButton(onClick = { controller.goLive(); onActivity() }) {
                    Text(if (atLive) "Live" else "Go live", color = if (atLive) Neon.Live else Color.White, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (!compact) {
                IconButton(onClick = onPrev, modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) { Icon(Icons.Outlined.SkipPrevious, "Previous stream", tint = Color.White) }
                IconButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)) { Icon(Icons.Outlined.SkipNext, "Next stream", tint = Color.White) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().then(if (compact) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)).padding(12.dp)) {
                st.message?.let { Text(it, color = Neon.Magenta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                SeekBar(st, controller, onActivity)
                if (!compact) QualityPills(st)
            }
        }
    }
}

/** Position / length for replays and catch-up, "behind live" for live streams with a rewind window. */
@Composable
private fun SeekBar(st: PlayerState, controller: PlaybackController, onActivity: () -> Unit) {
    val t = st.timeline
    val settings by FitzApp.instance.settings.state.collectAsState()
    fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
    val label = when (st.mode) {
        com.fitztv.player.PlayMode.VOD -> "${mmss(t.positionMs)} / ${mmss(t.durationMs)}"
        com.fitztv.player.PlayMode.ARCHIVE -> "Catch-up • ${Fmt.time(st.archiveStartMs + t.positionMs, settings.zone(), settings.use24h)} • " +
            "${mmss(System.currentTimeMillis() - st.archiveStartMs - t.positionMs)} behind live"
        com.fitztv.player.PlayMode.LIVE -> if (t.seekable && t.durationMs - t.positionMs > 5_000) "${mmss(t.durationMs - t.positionMs)} behind live" else null
    }
    label?.let { Text(it, color = Neon.Cyan, fontSize = 12.sp) }
    if (t.seekable && t.durationMs > 0) {
        var dragging by remember { mutableStateOf<Float?>(null) }
        androidx.compose.material3.Slider(
            value = dragging ?: (t.positionMs.toFloat() / t.durationMs).coerceIn(0f, 1f),
            onValueChange = { dragging = it; onActivity() },
            onValueChangeFinished = { dragging?.let { controller.seekTo((it * t.durationMs).toLong()) }; dragging = null },
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Neon.Magenta, activeTrackColor = Neon.Purple, inactiveTrackColor = Neon.SurfaceHi),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QualityPills(st: PlayerState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        st.channel?.let { Pill(if (st.height > 0) Fmt.resolution(st.height) else Fmt.quality(it.quality), Neon.Text) }
        st.channel?.takeIf { com.fitztv.data.StreamLanguage.tier(it) == com.fitztv.data.StreamLanguage.OTHER }?.let { Pill("Other language", Neon.TextDim) }
        if (st.fps > 0) Pill("${(st.fps * 100).toInt() / 100f} fps", Neon.TextDim)
        st.engine?.let { Pill(it.name, Neon.TextDim) }
        if (st.degraded) Pill("Safe mode", Neon.TextDim)
        Text(
            if (PlaybackQueue.gameId != null) "Stream ${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}" else "${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}",
            color = Neon.TextDim, fontSize = 11.sp,
        )
    }
}

@Composable
private fun NowPlayingBar(st: PlayerState, game: Game?, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Neon.Bg2).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Outlined.SkipPrevious, "Previous stream", tint = Neon.Text) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game?.gameState == GameState.LIVE) { LiveDot(); Spacer(Modifier.width(8.dp)) }
                Text(
                    game?.let { g ->
                        if (g.home.isNotBlank() && g.gameState != GameState.PRE) "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}" else g.matchup
                    } ?: st.channel?.label.orEmpty(),
                    color = Neon.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            QualityPills(st)
            st.message?.let { Text(it, color = Neon.Magenta, fontSize = 12.sp, maxLines = 1) }
        }
        IconButton(onClick = onNext) { Icon(Icons.Outlined.SkipNext, "Next stream", tint = Neon.Text) }
    }
}

@Composable
private fun SidePanel(
    tab: PanelTab, onTab: (PanelTab) -> Unit, game: Game?,
    onPlayIndex: (Int) -> Unit, onPlayGame: (Game) -> Unit, qv: Int, modifier: Modifier, twoColumn: Boolean,
) {
    if (twoColumn && game != null) {
        // Unfolded portrait: stats and streams side by side.
        Row(modifier) {
            LiveStatsCard(game, Modifier.weight(1f).fillMaxHeight(), onClip = { com.fitztv.mobile.ui.ClipBus.clip.value = it })
            Box(Modifier.width(1.dp).fillMaxHeight().background(Neon.Purple.copy(alpha = 0.3f)))
            Column(Modifier.weight(1f)) {
                Text("Streams — best first", color = Neon.Purple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp))
                StreamList(onPlayIndex, qv)
            }
        }
        return
    }
    val hasFeed = game != null && game.espnPath != null && game.id.startsWith("espn:")
    val tabs = PanelTab.entries.filter { (game != null || it == PanelTab.STREAMS || it == PanelTab.GAMES) && (it != PanelTab.MOMENTUM || hasFeed) }
    val current = if (tab in tabs) tab else tabs.first()
    Column(modifier) {
        TabRow(
            selectedTabIndex = tabs.indexOf(current), containerColor = Neon.Bg, contentColor = Neon.Text,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tabs.indexOf(current)]), color = Neon.Purple) },
        ) {
            tabs.forEach { t -> Tab(selected = t == current, onClick = { onTab(t) }, text = { Text(t.label, fontSize = 13.sp) }) }
        }
        when (current) {
            PanelTab.STATS -> if (game != null) LiveStatsCard(game, Modifier.fillMaxSize(), onClip = { com.fitztv.mobile.ui.ClipBus.clip.value = it })
            PanelTab.MOMENTUM -> if (game != null) com.fitztv.mobile.ui.MomentumPanel(game, Modifier.fillMaxSize())
            PanelTab.STREAMS -> StreamList(onPlayIndex, qv)
            PanelTab.GAMES -> GamesList(onPlayGame)
        }
    }
}

@Composable
private fun StreamList(onPlayIndex: (Int) -> Unit, qv: Int) {
    val probe = FitzApp.instance.repo.probe
    val q = remember(qv, PlaybackQueue.index) { PlaybackQueue.channels }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(q, key = { _, c -> c.id }) { i, c ->
            val current = i == PlaybackQueue.index
            NeonCard(onClick = { onPlayIndex(i) }, modifier = Modifier.fillMaxWidth(), highlighted = current) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = if (current) Neon.Purple else Neon.TextFaint, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val game = remember(PlaybackQueue.gameId) { PlaybackQueue.gameId?.let { FitzApp.instance.repo.game(it) } }
                        val sub = remember(c.id, game?.id) { com.fitztv.data.StreamNotes.line(FitzApp.instance.repo, c, game) }
                        if (sub.isNotEmpty()) Text(sub, color = Neon.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(probe.label(c), color = if (probe.isMeasured(c)) Neon.Text else Neon.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = Tabular)
                }
            }
        }
    }
}

@Composable
private fun GamesList(onPlayGame: (Game) -> Unit) {
    val app = FitzApp.instance
    val settings by app.settings.state.collectAsState()
    val games by app.repo.games.collectAsState()
    val now = System.currentTimeMillis()
    // Other live games only (favourites first).
    val list = remember(games, settings) { app.repo.liveNow(null).filter { it.id != PlaybackQueue.gameId } }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (list.isEmpty()) item { Text("No other games are live right now", color = Neon.TextDim) }
        items(list, key = { it.id }) { g ->
            NeonCard(onClick = { onPlayGame(g) }, modifier = Modifier.fillMaxWidth(), highlighted = g.id == PlaybackQueue.gameId) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyph.sport(g.sport), null, tint = Neon.TextFaint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (g.gameState == GameState.LIVE && g.home.isNotBlank()) "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}" else g.matchup,
                        color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = Tabular,
                    )
                    if (g.gameState == GameState.LIVE) LiveClock(g.statusText)
                    else Text(Fmt.time(g.startUtcMillis, settings.zone(), settings.use24h), color = Neon.TextDim, fontSize = 12.sp, style = Tabular)
                }
            }
        }
    }
}

/** Audio sync (phone): nudge the sound against the picture; remembered per channel. */
@Composable
private fun AudioSyncDialog(controller: com.fitztv.player.PlaybackController, onDismiss: () -> Unit) {
    var offset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(controller.audioOffsetMs()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = Neon.Bg2,
        title = { Text("Audio sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("If the sound is ahead of the picture, move it later; if it's behind, move it earlier. Saved for this channel.", color = Neon.TextDim, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { controller.adjustAudioOffset(-20); offset = controller.audioOffsetMs() }) { Text("Earlier", color = Neon.Text) }
                    Text(if (offset == 0) "0 ms" else "${if (offset > 0) "+" else ""}$offset ms", color = Neon.Text, fontSize = 16.sp, style = Tabular, modifier = Modifier.width(80.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { controller.adjustAudioOffset(20); offset = controller.audioOffsetMs() }) { Text("Later", color = Neon.Text) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done", color = Neon.Purple) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { controller.adjustAudioOffset(0); offset = 0 }) { Text("Reset") } },
    )
}
