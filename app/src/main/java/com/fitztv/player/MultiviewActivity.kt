package com.fitztv.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.theme.FitzTheme
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the multiview overlay is doing. */
data class MvUi(
    val focus: Int = 0,
    val audio: Int = 0,
    /** Shown briefly after input; hidden = completely clean full screen. */
    val chrome: Boolean = true,
    val menu: Boolean = false,
    val picker: Boolean = false,
    /** "Move" mode: arrows swap the focused window with its neighbour. */
    val moving: Boolean = false,
    val tick: Int = 0,
    /** e.g. the provider only allows 1 stream at once. */
    val warning: String? = null,
)

/**
 * Full-screen multiview: 1–4 games at once. Arrows move between windows, OK puts the sound on
 * that window, MENU / long-press OK opens the window menu (move, make big, change game,
 * remove, full screen). Each window is its own player with the same anti-buffering logic.
 */
class MultiviewActivity : ComponentActivity() {
    private val app get() = FitzApp.instance
    private val controllers = HashMap<String, PlaybackController>()
    private val ui = MutableStateFlow(MvUi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            FitzTheme {
                MultiviewScreen(controllers = { id -> controllerFor(id) }, uiFlow = ui, onPick = ::addGame, onClose = ::finish)
            }
        }
        lifecycleScope.launch { Multiview.slots.collect { syncPlayers() } }
    }

    private fun controllerFor(id: String): PlaybackController? = controllers[id]

    private fun syncPlayers() {
        val slots = Multiview.slots.value
        if (slots.isEmpty()) { finish(); return }
        // Stop windows that were removed, start new ones.
        (controllers.keys - slots.map { it.id }.toSet()).forEach { controllers.remove(it)?.release() }
        slots.forEach { s ->
            if (s.id !in controllers) {
                val c = PlaybackController(this, app.repo, app.settings, lifecycleScope, multiview = true)
                controllers[s.id] = c
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) c.play(s.channels.first(), s.channels)
            }
        }
        ui.update { it.copy(focus = it.focus.coerceIn(0, slots.lastIndex), audio = it.audio.coerceIn(0, slots.lastIndex), tick = it.tick + 1) }
        applyAudio()
    }

    private fun applyAudio() {
        val slots = Multiview.slots.value
        slots.forEachIndexed { i, s -> controllers[s.id]?.setVolume(if (i == ui.value.audio) 1f else 0f) }
    }

    override fun onStart() {
        super.onStart()
        Multiview.slots.value.forEach { s -> controllers[s.id]?.play(s.channels.first(), s.channels) }
        applyAudio()
    }

    override fun onStop() {
        super.onStop()
        controllers.values.forEach { it.release() }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllers.values.forEach { it.release() }
        controllers.clear()
    }

    private fun addGame(game: Game) {
        val replacing = ui.value.menu          // "Change game" opens the picker with the menu flag set
        val focus = ui.value.focus
        ui.update { it.copy(picker = false, menu = false) }
        lifecycleScope.launch {
            val ranked = app.repo.rankedStreams(game)
            val slot = Multiview.slotFor(game, ranked) ?: return@launch
            if (replacing || Multiview.slots.value.size >= Multiview.MAX) Multiview.replace(focus, slot) else Multiview.add(slot)
        }
    }

    private fun neighbour(i: Int, keyCode: Int, n: Int): Int = when (n) {
        4 -> when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (i % 2 == 1) i - 1 else i
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (i % 2 == 0) i + 1 else i
            KeyEvent.KEYCODE_DPAD_UP -> if (i >= 2) i - 2 else i
            else -> if (i < 2) i + 2 else i
        }
        3 -> when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (i == 0) 1 else i
            KeyEvent.KEYCODE_DPAD_UP -> if (i == 2) 1 else i
            else -> if (i == 1) 2 else i
        }
        else -> when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> (i - 1).coerceAtLeast(0)
            else -> (i + 1).coerceAtMost(n - 1)
        }
    }

    private var okArmed = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val u = ui.value
        if (u.menu || u.picker) { okArmed = false; return super.dispatchKeyEvent(event) }
        val n = Multiview.slots.value.size
        if (event.action == KeyEvent.ACTION_DOWN) {
            ui.update { it.copy(chrome = true, tick = it.tick + 1) }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val target = neighbour(u.focus, event.keyCode, n)
                    if (u.moving && target != u.focus) {
                        Multiview.swap(u.focus, target)
                        val audio = when (u.audio) { u.focus -> target; target -> u.focus; else -> u.audio }
                        ui.update { it.copy(focus = target, audio = audio) }
                        applyAudio()
                    } else {
                        // Selecting a window moves the sound to it.
                        ui.update { it.copy(focus = target, audio = target) }
                        applyAudio()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (event.repeatCount == 0) okArmed = true; return true }   // acted on at release

                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { ui.update { it.copy(menu = true) }; return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    Multiview.slots.value.getOrNull(u.audio)?.let { controllers[it.id]?.togglePause() }; return true
                }
            }
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
            // Only a press that started here (not the release of a menu selection) opens the menu.
            if (!okArmed) return true
            okArmed = false
            // A normal press of OK opens the window menu (on release, so the press doesn't also pick an item).
            if (u.moving) ui.update { it.copy(moving = false) } else ui.update { it.copy(menu = true, chrome = true) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Window menu actions. */
    internal fun menuAction(action: String) {
        val u = ui.value
        val slots = Multiview.slots.value
        val slot = slots.getOrNull(u.focus)
        ui.update { it.copy(menu = false) }
        when (action) {
            "move" -> ui.update { it.copy(moving = true) }
            "big" -> { Multiview.swap(u.focus, 0); ui.update { it.copy(focus = 0, audio = if (it.audio == u.focus) 0 else if (it.audio == 0) u.focus else it.audio) }; applyAudio() }
            "audio" -> { ui.update { it.copy(audio = it.focus) }; applyAudio() }
            "change" -> ui.update { it.copy(picker = true, menu = true) }
            "add" -> ui.update { it.copy(picker = true) }
            "next" -> slot?.let { s -> controllers[s.id]?.let { c -> val list = s.channels; val cur = c.state.value.channel; val i = list.indexOfFirst { it.id == cur?.id }; c.play(list[(i + 1).mod(list.size)], list) } }
            "remove" -> Multiview.remove(u.focus)
            "full" -> slot?.let { s ->
                PlaybackQueue.launch(this, s.channels, 0, s.game?.id)
                Multiview.clear(); finish()
            }
        }
    }

    internal val uiState get() = ui
}

@Composable
private fun MultiviewScreen(
    controllers: (String) -> PlaybackController?,
    uiFlow: MutableStateFlow<MvUi>,
    onPick: (Game) -> Unit,
    onClose: () -> Unit,
) {
    val slots by Multiview.slots.collectAsState()
    val u by uiFlow.collectAsState()
    val activity = androidx.compose.ui.platform.LocalContext.current as MultiviewActivity
    LaunchedEffect(u.tick, u.chrome) { if (u.chrome) { delay(3_500); uiFlow.update { it.copy(chrome = false) } } }
    BackHandler(enabled = u.menu || u.picker || u.moving) { uiFlow.update { it.copy(menu = false, picker = false, moving = false) } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val tile: @Composable (Int, Modifier) -> Unit = { i, m ->
            val s = slots[i]
            val c = controllers(s.id)
            val st = c?.state?.collectAsState()?.value
            Box(m.background(Color.Black)) {
                st?.engine?.let { e ->
                    key(e) { AndroidView(factory = { e.view.also { v -> (v.parent as? ViewGroup)?.removeView(v) } }, modifier = Modifier.fillMaxSize()) }
                }
                val focused = u.focus == i
                if (u.chrome || u.moving) {
                    Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 0.dp, if (u.moving && focused) Neon.Magenta else if (focused) Neon.Purple else Color.Transparent))
                    Row(Modifier.align(Alignment.TopStart).padding(8.dp).background(Neon.Bg.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (u.audio == i) { Text("🔊", fontSize = 13.sp); Spacer(Modifier.width(6.dp)) }
                        val g = s.game
                        Text(
                            if (g != null && g.gameState != GameState.PRE && g.home.isNotBlank()) "${g.awayShort} ${g.awayScore ?: ""}–${g.homeScore ?: ""} ${g.homeShort}" else s.title,
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                    }
                } else if (u.audio == i && slots.size > 1) {
                    Text("🔊", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                if (st?.buffering == true) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(color = Neon.Purple, modifier = Modifier.width(36.dp).height(36.dp))
                        Text(st.message ?: "Connecting…", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                } else st?.message?.let { m ->
                    Text(m, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        // Layouts: 1 = full, 2 = side by side, 3 = one big + two stacked, 4 = 2×2. No gaps, truly full screen.
        when (slots.size) {
            0 -> Unit
            1 -> tile(0, Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                tile(0, Modifier.weight(1f).fillMaxHeight()); tile(1, Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize()) {
                tile(0, Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) { tile(1, Modifier.weight(1f).fillMaxWidth()); tile(2, Modifier.weight(1f).fillMaxWidth()) }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) { tile(0, Modifier.weight(1f).fillMaxHeight()); tile(1, Modifier.weight(1f).fillMaxHeight()) }
                Row(Modifier.weight(1f)) { tile(2, Modifier.weight(1f).fillMaxHeight()); tile(3, Modifier.weight(1f).fillMaxHeight()) }
            }
        }

        if (u.chrome && !u.menu && !u.picker) {
            Text(
                if (u.moving) "Move mode: arrows swap this window • OK when done"
                else "◀ ▶ ▲ ▼ select a window (sound follows) • OK: window menu — move, make big, change or add a game",
                color = Neon.TextDim, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Neon.Bg.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        u.warning?.let { w ->
            Text(w, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Neon.Live.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
        }

        if (u.menu && !u.picker) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            Column(
                Modifier.align(Alignment.Center).width(380.dp).background(Neon.Bg2.copy(alpha = 0.96f), RoundedCornerShape(18.dp)).border(2.dp, Neon.Purple, RoundedCornerShape(18.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(slots.getOrNull(u.focus)?.title ?: "", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val items = buildList {
                    add("audio" to "🔊  Sound on this window")
                    add("move" to "✥  Move this window")
                    if (slots.size >= 3 && u.focus != 0) add("big" to "⬛  Make this the big window")
                    add("change" to "⇄  Change game in this window")
                    add("next" to "⟳  Try next stream for this game")
                    if (slots.size < Multiview.MAX) add("add" to "+  Add another game")
                    add("full" to "⛶  Watch this one full screen")
                    add("remove" to "✕  Remove this window")
                }
                items.forEachIndexed { i, (k, label) ->
                    NeonSurface(onClick = { activity.menuAction(k) }, modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier), radius = 10.dp) {
                        Text(label, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }

        if (u.picker) GamePicker(onPick = onPick, modifier = Modifier.align(Alignment.Center))
    }
}

/** Live and upcoming games (favourites first) plus quick channels, for filling a window. */
@Composable
private fun GamePicker(onPick: (Game) -> Unit, modifier: Modifier) {
    val app = FitzApp.instance
    val games by app.repo.games.collectAsState()
    val settings by app.settings.state.collectAsState()
    val now = System.currentTimeMillis()
    val list = remember(games) {
        games.filter { it.gameState == GameState.LIVE || (it.gameState == GameState.PRE && it.startUtcMillis - now < 6 * 3_600_000L) }
            .sortedWith(compareByDescending<Game> { settings.isFavourite(it) }.thenBy { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { first.requestFocus() } }
    Column(modifier.width(560.dp).background(Neon.Bg2.copy(alpha = 0.97f), RoundedCornerShape(18.dp)).border(2.dp, Neon.Purple, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text("Choose a game", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) Text("No live or upcoming games", color = Neon.TextDim)
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list.withIndex().toList(), key = { it.value.id }) { (i, g) ->
                NeonSurface(onClick = { onPick(g) }, modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier), radius = 10.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (settings.isFavourite(g)) { Text("★", color = Neon.Magenta); Spacer(Modifier.width(6.dp)) }
                        Pill(g.league, Neon.Purple)
                        Spacer(Modifier.width(8.dp))
                        Text(g.matchup, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (g.gameState == GameState.LIVE) "LIVE" else com.fitztv.ui.components.Fmt.time(g.startUtcMillis, settings.zone(), settings.use24h),
                            color = if (g.gameState == GameState.LIVE) Neon.Live else Neon.Cyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
