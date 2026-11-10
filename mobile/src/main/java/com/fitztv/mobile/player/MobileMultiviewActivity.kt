package com.fitztv.mobile.player

import androidx.compose.material3.Icon
import com.fitztv.mobile.ui.Glyph

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.GameState
import com.fitztv.mobile.ui.FitzMobileTheme
import com.fitztv.mobile.ui.Neon
import com.fitztv.mobile.ui.NeonCard
import com.fitztv.mobile.ui.Pill
import com.fitztv.player.Multiview
import com.fitztv.player.PlaybackController
import com.fitztv.player.PlaybackQueue
import com.fitztv.ui.components.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Full-screen multiview on the phone: tap a window for its sound, long-press for the window
 * menu (move to another position, make big, change game, remove, full screen).
 */
class MobileMultiviewActivity : ComponentActivity() {
    private val app get() = FitzApp.instance
    private val controllers = HashMap<String, PlaybackController>()
    private val version = MutableStateFlow(0)
    private val audio = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent { FitzMobileTheme { Screen() } }
        lifecycleScope.launch { Multiview.slots.collect { sync() } }
    }

    private fun sync() {
        val slots = Multiview.slots.value
        if (slots.isEmpty()) { finish(); return }
        (controllers.keys - slots.map { it.id }.toSet()).forEach { controllers.remove(it)?.release() }
        slots.forEach { s ->
            if (s.id !in controllers) {
                val c = PlaybackController(this, app.repo, app.settings, lifecycleScope, multiview = true)
                controllers[s.id] = c
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) c.play(s.channels.first(), s.channels)
            }
        }
        audio.value = audio.value.coerceIn(0, slots.lastIndex)
        applyAudio()
        version.value++
    }

    private fun applyAudio() = Multiview.slots.value.forEachIndexed { i, s -> controllers[s.id]?.setVolume(if (i == audio.value) 1f else 0f) }

    override fun onStart() {
        super.onStart()
        Multiview.slots.value.forEach { s -> controllers[s.id]?.play(s.channels.first(), s.channels) }
        applyAudio()
    }

    override fun onStop() { super.onStop(); controllers.values.forEach { it.release() } }

    override fun onDestroy() { super.onDestroy(); controllers.values.forEach { it.release() }; controllers.clear() }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Screen() {
        val slots by Multiview.slots.collectAsState()
        val v by version.collectAsState()
        val au by audio.collectAsState()
        var menuFor by remember { mutableStateOf<Int?>(null) }
        var pickFor by remember { mutableStateOf<Int?>(null) }   // index to replace, or -1 to add
        var hint by remember { mutableIntStateOf(1) }
        LaunchedEffect(hint) { kotlinx.coroutines.delay(4_000); hint = 0 }
        val cfg = LocalConfiguration.current
        val landscape = cfg.screenWidthDp > cfg.screenHeightDp

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val tile: @Composable (Int, Modifier) -> Unit = { i, m ->
                val s = slots[i]
                val c = remember(s.id, v) { controllers[s.id] }
                val st = c?.state?.collectAsState()?.value
                Box(m.background(Color.Black).combinedClickable(onClick = { audio.value = i; applyAudio(); hint++ }, onLongClick = { menuFor = i })) {
                    st?.engine?.let { e -> key(e) { AndroidView(factory = { e.view.also { x -> (x.parent as? ViewGroup)?.removeView(x) } }, modifier = Modifier.fillMaxSize()) } }
                    if (au == i && slots.size > 1) Box(Modifier.fillMaxSize().border(2.dp, Neon.Purple))
                    Row(Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        if (au == i) { Icon(Glyph.Sound, "Sound on", tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                        val g = s.game
                        Text(if (g != null && g.gameState != GameState.PRE && g.home.isNotBlank()) "${g.awayShort} ${g.awayScore ?: ""}–${g.homeScore ?: ""} ${g.homeShort}" else s.title,
                            color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                    if (st?.buffering == true) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Neon.Purple, modifier = Modifier.size(28.dp))
                        Text(st.message ?: "Connecting…", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
            when (slots.size) {
                0 -> Unit
                1 -> tile(0, Modifier.fillMaxSize())
                2 -> if (landscape) Row(Modifier.fillMaxSize()) { tile(0, Modifier.weight(1f).fillMaxHeight()); tile(1, Modifier.weight(1f).fillMaxHeight()) }
                     else Column(Modifier.fillMaxSize()) { tile(0, Modifier.weight(1f).fillMaxWidth()); tile(1, Modifier.weight(1f).fillMaxWidth()) }
                3 -> if (landscape) Row(Modifier.fillMaxSize()) {
                    tile(0, Modifier.weight(2f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight()) { tile(1, Modifier.weight(1f).fillMaxWidth()); tile(2, Modifier.weight(1f).fillMaxWidth()) }
                } else Column(Modifier.fillMaxSize()) {
                    tile(0, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.weight(1f)) { tile(1, Modifier.weight(1f).fillMaxHeight()); tile(2, Modifier.weight(1f).fillMaxHeight()) }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) { tile(0, Modifier.weight(1f).fillMaxHeight()); tile(1, Modifier.weight(1f).fillMaxHeight()) }
                    Row(Modifier.weight(1f)) { tile(2, Modifier.weight(1f).fillMaxHeight()); tile(3, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
            if (hint > 0) Text(
                "Tap a window for its sound · long-press for more",
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Neon.Bg.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        menuFor?.let { i ->
            val s = slots.getOrNull(i)
            AlertDialog(
                onDismissRequest = { menuFor = null }, containerColor = Neon.Bg2,
                title = { Text(s?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    val actions = buildList<Pair<String, () -> Unit>> {
                        add("Sound on this window" to { audio.value = i; applyAudio() })
                        slots.indices.filter { it != i }.forEach { j ->
                            add("Move to position ${j + 1}" to {
                                Multiview.swap(i, j); audio.value = when (audio.value) { i -> j; j -> i; else -> audio.value }; applyAudio()
                            })
                        }
                        add("Change game" to { pickFor = i })
                        if (slots.size < Multiview.MAX) add("Add another game" to { pickFor = -1 })
                        add("Watch this one full screen" to {
                            s?.let { PlaybackQueue.launch(this@MobileMultiviewActivity, it.channels, 0, it.game?.id); Multiview.clear(); finish() }
                        })
                        add("Remove this window" to { Multiview.remove(i) })
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        actions.forEach { (label, action) ->
                            TextButton(onClick = { menuFor = null; action() }, modifier = Modifier.fillMaxWidth()) { Text(label, color = Neon.Text, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Close") } },
            )
        }
        pickFor?.let { target -> GamePicker(onPick = { g ->
            pickFor = null
            lifecycleScope.launch {
                val slot = Multiview.slotFor(g, app.repo.rankedStreams(g)) ?: return@launch
                if (target >= 0) Multiview.replace(target, slot) else Multiview.add(slot)
            }
        }, onDismiss = { pickFor = null }) }
    }

    @Composable
    private fun GamePicker(onPick: (Game) -> Unit, onDismiss: () -> Unit) {
        val games by app.repo.games.collectAsState()
        val settings by app.settings.state.collectAsState()
        val now = System.currentTimeMillis()
        val list = remember(games) {
            games.filter { it.gameState == GameState.LIVE || (it.gameState == GameState.PRE && it.startUtcMillis - now < 6 * 3_600_000L) }
                .sortedWith(compareByDescending<Game> { settings.isFavourite(it) }.thenBy { it.gameState != GameState.LIVE }.thenBy { it.startUtcMillis })
        }
        AlertDialog(
            onDismissRequest = onDismiss, containerColor = Neon.Bg2, title = { Text("Choose a game") },
            text = {
                if (list.isEmpty()) Text("No live or upcoming games", color = Neon.TextDim)
                else LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { g ->
                        NeonCard(onClick = { onPick(g) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (settings.isFavourite(g)) { Icon(Glyph.Star, "My team", tint = Neon.Purple, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                                Pill(g.league, Neon.Purple); Spacer(Modifier.width(8.dp))
                                Text(g.matchup, color = Neon.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(if (g.gameState == GameState.LIVE) "Live" else Fmt.time(g.startUtcMillis, settings.zone(), settings.use24h),
                                    color = if (g.gameState == GameState.LIVE) Neon.Live else Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    }
}
