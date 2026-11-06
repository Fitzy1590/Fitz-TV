package com.fitztv.mobile.player

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ViewList
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
            controller.state.collect { st -> if (st.height > 0) updatePipParams(st) }
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

    private fun playGame(game: Game) {
        lifecycleScope.launch {
            controller.flash("Finding the best stream…")
            val ranked = app.repo.rankedStreams(game)
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

private enum class PanelTab(val label: String) { STATS("Live stats"), STREAMS("Streams"), GAMES("Games") }

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
            VideoSurface(s, m, onTap = { controls = !controls; tick++ }, onSwipe = { d -> onPlayIndex(PlaybackQueue.index + d); tick++ })
        }
    }
    val overlay: @Composable BoxScope.() -> Unit = {
        if (!pip) Controls(
            visible = controls, st = st, game = game, compact = !fullscreen,
            onBack = onBack, onPrev = { onPlayIndex(PlaybackQueue.index - 1) }, onNext = { onPlayIndex(PlaybackQueue.index + 1) },
            onPanel = { if (fullscreen) sidePanel = !sidePanel }, onPip = onPip, showPanelButton = fullscreen,
        )
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
    }
}

@Composable
private fun VideoSurface(st: PlayerState, modifier: Modifier, onTap: () -> Unit, onSwipe: (Int) -> Unit) {
    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
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
            }
        }
        if (st.buffering) CircularProgressIndicator(color = Neon.Purple, trackColor = Neon.Purple.copy(alpha = 0.15f), strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun BoxScope.Controls(
    visible: Boolean, st: PlayerState, game: Game?, compact: Boolean, showPanelButton: Boolean,
    onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onPanel: () -> Unit, onPip: () -> Unit,
) {
    AnimatedVisibility(visible || st.message != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.6f))))) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart).then(if (compact) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(game?.matchup ?: st.channel?.label.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (game != null) Text(st.channel?.label.orEmpty(), color = Neon.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPip) { Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                if (showPanelButton) IconButton(onClick = onPanel) { Icon(if (game != null) Icons.Filled.BarChart else Icons.Filled.ViewList, "Panel", tint = Color.White) }
            }
            if (!compact) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous stream", tint = Color.White, modifier = Modifier.size(40.dp)) }
                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next stream", tint = Color.White, modifier = Modifier.size(40.dp)) }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).then(if (compact) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)).padding(12.dp)) {
                st.message?.let { Text(it, color = Neon.Magenta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                if (!compact) QualityPills(st)
            }
        }
    }
}

@Composable
private fun QualityPills(st: PlayerState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        st.channel?.let { Pill(if (st.height > 0) Fmt.resolution(st.height) else Fmt.quality(it.quality), Neon.Cyan) }
        if (st.fps > 0) Pill("${(st.fps * 100).toInt() / 100f} fps", Neon.TextDim)
        st.engine?.let { Pill(it.name, Neon.Purple) }
        if (st.degraded) Pill("SAFE MODE", Neon.Magenta)
        Text(
            if (PlaybackQueue.gameId != null) "Stream ${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}" else "${PlaybackQueue.index + 1}/${PlaybackQueue.channels.size}",
            color = Neon.TextDim, fontSize = 11.sp,
        )
    }
}

@Composable
private fun NowPlayingBar(st: PlayerState, game: Game?, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Neon.Bg2).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "Previous stream", tint = Neon.Text) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game?.gameState == GameState.LIVE) { LiveDot(); Spacer(Modifier.width(6.dp)) }
                Text(
                    game?.let { g ->
                        if (g.home.isNotBlank() && g.gameState != GameState.PRE) "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}" else g.matchup
                    } ?: st.channel?.label.orEmpty(),
                    color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            QualityPills(st)
            st.message?.let { Text(it, color = Neon.Magenta, fontSize = 12.sp, maxLines = 1) }
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next stream", tint = Neon.Text) }
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
            LiveStatsCard(game, Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(Neon.Purple.copy(alpha = 0.3f)))
            Column(Modifier.weight(1f)) {
                Text("Streams — best first", color = Neon.Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp))
                StreamList(onPlayIndex, qv)
            }
        }
        return
    }
    val tabs = if (game != null) PanelTab.entries else listOf(PanelTab.STREAMS, PanelTab.GAMES)
    val current = if (tab in tabs) tab else tabs.first()
    Column(modifier) {
        TabRow(
            selectedTabIndex = tabs.indexOf(current), containerColor = Neon.Bg, contentColor = Neon.Text,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tabs.indexOf(current)]), color = Neon.Purple) },
        ) {
            tabs.forEach { t -> Tab(selected = t == current, onClick = { onTab(t) }, text = { Text(t.label, fontSize = 13.sp) }) }
        }
        when (current) {
            PanelTab.STATS -> if (game != null) LiveStatsCard(game, Modifier.fillMaxSize())
            PanelTab.STREAMS -> StreamList(onPlayIndex, qv)
            PanelTab.GAMES -> GamesList(onPlayGame)
        }
    }
}

@Composable
private fun StreamList(onPlayIndex: (Int) -> Unit, qv: Int) {
    val probe = FitzApp.instance.repo.probe
    val q = remember(qv, PlaybackQueue.index) { PlaybackQueue.channels }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(q, key = { _, c -> c.id }) { i, c ->
            val current = i == PlaybackQueue.index
            NeonCard(onClick = { onPlayIndex(i) }, modifier = Modifier.fillMaxWidth(), highlighted = current) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = if (current) Neon.Magenta else Neon.TextFaint, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        FitzApp.instance.repo.nowPlaying(c)?.let { Text(it.title, color = Neon.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    val f = probe.fps(c)
                    Pill(Fmt.resolution(probe.height(c)) + if (f >= 49f) " • ${f.toInt()}" else "", Neon.Cyan)
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
    val favs = remember(games, settings.favoriteTeams) { favouriteEntries(games, settings, now).filter { it.game != null } }
    val list = remember(games) {
        games.filter { it.gameState == GameState.LIVE || (it.gameState == GameState.PRE && it.startUtcMillis - now < 12 * 3_600_000L) }
            .sortedWith(compareBy<Game> { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (favs.isNotEmpty()) {
            item { Text("★  My teams", color = Neon.Magenta, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(favs, key = { "f:" + it.team }) { e -> FavouriteCard(e, settings, onClick = { e.game?.let(onPlayGame) }, modifier = Modifier.fillMaxWidth()) }
            item { Text("Live & next 12 hours", color = Neon.Purple, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (list.isEmpty()) item { Text("No games right now", color = Neon.TextDim) }
        items(list, key = { it.id }) { g ->
            NeonCard(onClick = { onPlayGame(g) }, modifier = Modifier.fillMaxWidth(), highlighted = g.id == PlaybackQueue.gameId) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("${g.sport.emoji} ${g.league}", Neon.Purple)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (g.gameState == GameState.LIVE && g.home.isNotBlank()) "${g.awayShort} ${g.awayScore ?: ""} – ${g.homeScore ?: ""} ${g.homeShort}" else g.matchup,
                        color = Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (g.gameState == GameState.LIVE) LiveDot()
                    else Text(Fmt.time(g.startUtcMillis, settings.zone(), settings.use24h), color = Neon.Cyan, fontSize = 12.sp)
                }
            }
        }
    }
}
