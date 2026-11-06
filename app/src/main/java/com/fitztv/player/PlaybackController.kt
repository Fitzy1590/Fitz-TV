package com.fitztv.player

import android.content.Context
import com.fitztv.data.Channel
import com.fitztv.data.Engine
import com.fitztv.data.Repository
import com.fitztv.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val channel: Channel? = null,
    val engine: PlaybackEngine? = null,
    val buffering: Boolean = true,
    val message: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = -1f,
    val decoder: String? = null,
    /** True after we stepped down to protect playback. */
    val degraded: Boolean = false,
)

/**
 * Owns the active engine and keeps the stream alive:
 *  - reconnects instantly on network drops and "behind live window" errors,
 *  - detects frozen video (position not advancing) and long stalls,
 *  - steps quality down (HLS rung, then lower-quality variant channel) when stalls repeat,
 *  - climbs back to full quality once playback has been stable for a while,
 *  - falls back from ExoPlayer to VLC on unsupported streams.
 */
class PlaybackController(
    private val context: Context,
    private val repo: Repository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : EngineCallback {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private var engine: PlaybackEngine? = null
    private var requested: Channel? = null      // what the user picked (best quality)
    private var playing: Channel? = null        // what we're actually playing (may be a lower variant)
    private var watchdog: Job? = null
    private var retryJob: Job? = null

    private var bufferingSince = 0L
    private var playingSince = 0L
    private var lastPos = -1L
    private var lastPosChange = 0L
    private val stalls = ArrayDeque<Long>()
    private var retries = 0
    private var vlcFallbackUsed = false
    /** Stable time needed before climbing back to full quality; doubles each time we have to step down. */
    private var upgradeAfterMs = 90_000L

    fun play(channel: Channel) {
        requested = channel
        vlcFallbackUsed = false
        upgradeAfterMs = 90_000L
        start(channel, preferVlc = settings.value.engine == Engine.VLC)
    }

    private fun start(channel: Channel, preferVlc: Boolean) {
        retryJob?.cancel()
        playing = channel
        retries = 0
        stalls.clear()
        val wantVlc = preferVlc
        val current = engine
        val e = if (current != null && (current is VlcEngine) == wantVlc) current else {
            current?.release()
            if (wantVlc) VlcEngine(context, settings.value, this) else ExoEngine(context, settings.value, this)
        }
        engine = e
        _state.value = PlayerState(channel = channel, engine = e, buffering = true,
            degraded = requested?.let { it.id != channel.id } ?: false)
        markBuffering(true)
        e.play(channel.streamUrl)
        startWatchdog()
    }

    /** Show a transient message on the player. */
    fun flash(msg: String) {
        _state.update { it.copy(message = msg) }
        scope.launch { delay(4_000); _state.update { if (it.message == msg) it.copy(message = null) else it } }
    }

    fun retry() { playing?.let { start(it, engine is VlcEngine) } }

    fun release() {
        watchdog?.cancel(); retryJob?.cancel()
        engine?.release(); engine = null
        _state.update { it.copy(engine = null) }
    }

    // ---- EngineCallback ------------------------------------------------------------------

    override fun onBuffering(buffering: Boolean) {
        if (buffering && _state.value.buffering.not() && playingSince > 0) {
            val now = System.currentTimeMillis()
            stalls.addLast(now)
            while (stalls.isNotEmpty() && now - stalls.first() > 90_000) stalls.removeFirst()
            if (stalls.size >= 3) { stalls.clear(); stepDown("Repeated buffering") }
        }
        markBuffering(buffering)
    }

    override fun onPlaying() {
        retries = 0
        markBuffering(false)
        _state.update { it.copy(message = null) }
    }

    override fun onError(kind: ErrorKind, message: String) {
        when (kind) {
            ErrorKind.BEHIND_LIVE, ErrorKind.ENDED -> engine?.reconnect()
            ErrorKind.UNSUPPORTED -> {
                if (engine is ExoEngine && settings.value.engine == Engine.AUTO && !vlcFallbackUsed) {
                    vlcFallbackUsed = true
                    _state.update { it.copy(message = "Switching to VLC engine…") }
                    playing?.let { start(it, preferVlc = true) }
                } else scheduleRetry(message)
            }
            ErrorKind.NETWORK -> scheduleRetry(message)
        }
    }

    override fun onVideoInfo(width: Int, height: Int, fps: Float, decoder: String?) {
        _state.update { it.copy(width = width, height = height, fps = fps, decoder = decoder ?: it.decoder) }
    }

    // ---- recovery --------------------------------------------------------------------------

    private fun scheduleRetry(reason: String) {
        retryJob?.cancel()
        retries++
        // After a few failed reconnects, try a different variant of the same channel.
        if (retries == 4 && stepDown("Stream unavailable")) return
        val wait = minOf(500L * (1L shl minOf(retries - 1, 4)), 8_000L)
        _state.update { it.copy(message = "Reconnecting… ($reason)") }
        retryJob = scope.launch {
            delay(wait)
            if (retries > 6 && engine is ExoEngine && settings.value.engine == Engine.AUTO && !vlcFallbackUsed) {
                vlcFallbackUsed = true
                playing?.let { start(it, preferVlc = true) }
            } else {
                engine?.reconnect()
            }
        }
    }

    /** Lower quality one step. Returns true if something changed. */
    private fun stepDown(reason: String): Boolean {
        val e = engine ?: return false
        upgradeAfterMs = minOf(upgradeAfterMs * 2, 30 * 60_000L)
        if (e.lowerQuality()) {
            _state.update { it.copy(degraded = true, message = "$reason – lowering quality") }
            return true
        }
        val cur = playing ?: return false
        val lower = repo.variantsOf(cur).firstOrNull { it.quality < cur.quality }
            ?: repo.variantsOf(cur).firstOrNull { it.quality == cur.quality && it.sourceId != cur.sourceId }
        if (lower != null) {
            start(lower, e is VlcEngine)
            _state.update { it.copy(degraded = true, message = "$reason – switched to ${lower.name}") }
            return true
        }
        e.reconnect()
        return false
    }

    private fun markBuffering(b: Boolean) {
        val now = System.currentTimeMillis()
        if (b) { if (bufferingSince == 0L) bufferingSince = now; playingSince = 0 }
        else { bufferingSince = 0; if (playingSince == 0L) playingSince = now; lastPosChange = now }
        _state.update { it.copy(buffering = b) }
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        lastPos = -1; lastPosChange = System.currentTimeMillis()
        watchdog = scope.launch {
            while (isActive) {
                delay(1_000)
                val e = engine ?: continue
                val now = System.currentTimeMillis()
                if (bufferingSince > 0 && now - bufferingSince > 10_000 && retryJob?.isActive != true) {
                    // Stuck buffering: reconnect at the live edge, stepping down if it keeps happening.
                    bufferingSince = now
                    stalls.addLast(now)
                    if (stalls.size >= 2) { stalls.clear(); stepDown("Slow stream") } else e.reconnect()
                    continue
                }
                if (playingSince > 0) {
                    val pos = e.positionMs()
                    if (pos != lastPos) { lastPos = pos; lastPosChange = now }
                    else if (now - lastPosChange > 6_000) {
                        // Frozen picture without a buffering event: kick the stream.
                        lastPosChange = now
                        _state.update { it.copy(message = "Picture froze – reconnecting") }
                        e.reconnect()
                    }
                    // Stable for a while: go back up to the best quality.
                    if (_state.value.degraded && now - playingSince > upgradeAfterMs) {
                        playingSince = now
                        val best = requested
                        if (best != null && playing?.id != best.id) {
                            start(best, e is VlcEngine)
                        } else {
                            e.restoreQuality()
                            _state.update { it.copy(degraded = false) }
                        }
                    }
                }
            }
        }
    }
}
