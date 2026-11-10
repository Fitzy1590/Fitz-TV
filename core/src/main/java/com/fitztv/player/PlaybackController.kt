package com.fitztv.player

import android.content.Context
import com.fitztv.data.Catchup
import com.fitztv.data.Channel
import com.fitztv.data.Game
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
    val paused: Boolean = false,
    val mode: PlayMode = PlayMode.LIVE,
    /** Wall-clock time the archive (catch-up) stream starts at, when [mode] is ARCHIVE. */
    val archiveStartMs: Long = 0,
    val timeline: Timeline = Timeline(),
)

/** LIVE = live stream; ARCHIVE = provider catch-up of a live channel (timeshift); VOD = replay video. */
enum class PlayMode { LIVE, ARCHIVE, VOD }

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
    /** One tile of multiview: independent of the main player queue. */
    private val multiview: Boolean = false,
) : EngineCallback {

    private var volume = 1f

    fun setVolume(v: Float) { volume = v; engine?.setVolume(v) }

    /** Is the channel playing now one you up-voted? */
    fun isUpvoted(): Boolean = playing?.let { repo.votes.has(it) } == true

    /**
     * Up-vote (or take back the vote for) the channel playing now. Only a picture in HD or better
     * can be preferred. An up-vote also teaches the league's category preference. Returns a message.
     */
    fun toggleUpvote(): String {
        val ch = playing ?: return "Nothing playing"
        val st = _state.value
        if (repo.votes.has(ch)) { repo.votes.toggle(ch, st.height, st.fps); return "Removed from preferred channels" }
        if (st.height in 1 until 700 || st.height == 0) return "Only a working HD stream can be preferred"
        repo.votes.toggle(ch, st.height, st.fps)
        PlaybackQueue.gameId?.let { repo.game(it) }?.let { g -> runCatching { repo.hints.addWatch(g, ch, 30 * 60) } }
        return "Preferred: ${ch.label} will rank first from now on"
    }

    private var handleAudioFocus = true
    /** Off while in picture-in-picture so another app's audio doesn't pause this stream. */
    fun setHandleAudioFocus(h: Boolean) { handleAudioFocus = h; engine?.setHandleAudioFocus(h) }

    /** Channel identity for per-channel settings (stable across playlist refreshes). */
    private fun syncKey(c: Channel) = c.sourceId + "|" + com.fitztv.sports.Text.norm(c.name).trim()

    /** Current audio offset for the playing channel (ms; + = audio later). */
    fun audioOffsetMs(): Int = playing?.let { settings.value.audioOffsets[syncKey(it)] } ?: settings.value.audioOffsetDefault

    /** Nudge audio sync for this channel by [deltaMs] (0 resets); remembered per channel. */
    fun adjustAudioOffset(deltaMs: Int) {
        val ch = playing ?: return
        val next = if (deltaMs == 0) 0 else (audioOffsetMs() + deltaMs).coerceIn(-1000, 1000)
        settings.update { s -> s.copy(audioOffsets = if (next == 0) s.audioOffsets - syncKey(ch) else s.audioOffsets + (syncKey(ch) to next)) }
        engine?.setAudioOffsetMs(next)
        flash(if (next == 0) "Audio sync reset" else "Audio ${if (next > 0) "+" else ""}$next ms (${if (next > 0) "later" else "earlier"})")
    }

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
    private var currentUrl: String? = null
    private var altFormatTried = false
    /** Stable time needed before climbing back to full quality; doubles each time we have to step down. */
    private var upgradeAfterMs = 90_000L

    /** Ranked alternatives (best first) to fall back through if this stream fails. */
    private var candidates: List<Channel> = emptyList()

    fun play(channel: Channel, candidates: List<Channel> = emptyList()) {
        requested = channel
        upgradedFrom = null
        this.candidates = candidates
        vlcFallbackUsed = false
        autoUpgrades = 0
        upgradeCheckedFor = null
        upgradeAfterMs = 90_000L
        start(channel, preferVlc = settings.value.engine == Engine.VLC)
    }

    private var hintRecorded = false

    /** A frame has been drawn for the current stream. */
    private var frameSeen = false
    /** Smooth live viewing not yet handed to the category learner. */
    private var watchedMs = 0L
    private var watchedChannel: Channel? = null

    private fun flushWatch() {
        val ch = watchedChannel; val ms = watchedMs
        watchedMs = 0
        if (ch == null || ms < 10_000 || multiview) return
        val g = PlaybackQueue.gameId?.let { repo.game(it) } ?: return
        runCatching { repo.hints.addWatch(g, ch, (ms / 1000).toInt()) }
    }
    /** The stream we left for an automatic upgrade: go back to it if the new one shows nothing. */
    private var upgradedFrom: Channel? = null

    /** Current escalation (User-Agent / buffers) and the one the running engine was built with. */
    private var tuning = StreamTuning()
    private var engineTuning = StreamTuning()
    private val troubles = ArrayDeque<Long>()
    private var lastEscalationAt = 0L
    private var fixRecorded = false

    /**
     * Count a reconnect / stall / freeze. Three within 2.5 minutes escalate: another User-Agent,
     * a bigger buffer and a longer wait after stalls, then rebuild the player. Returns true when
     * it restarted the stream, so the caller should stop what it was doing.
     */
    private fun noteTrouble(): Boolean {
        if (!settings.value.selfHeal || mode != PlayMode.LIVE) return false
        val now = System.currentTimeMillis()
        troubles.addLast(now)
        while (troubles.isNotEmpty() && now - troubles.first() > 150_000) troubles.removeFirst()
        if (troubles.size < 3 || now - lastEscalationAt < 20_000 || tuning.level >= StreamTuning.MAX_LEVEL) return false
        val cur = playing ?: return false
        lastEscalationAt = now
        troubles.clear()
        tuning = tuning.next()
        start(cur, engine is VlcEngine, keepTuning = true)
        flash("Unstable stream – steadier connection (buffer ×${"%.1f".format(tuning.bufferFactor)}, step ${tuning.level})")
        return true
    }

    private fun start(channel: Channel, preferVlc: Boolean, keepTuning: Boolean = false) {
        if (!keepTuning) { tuning = repo.fixes.get(channel) ?: StreamTuning(); troubles.clear() }
        fixRecorded = false
        flushWatch()
        watchedChannel = channel
        hintRecorded = false
        frameSeen = false
        retryJob?.cancel()
        upgradeJob?.cancel(); upgradeJob = null
        playing = channel
        retries = 0
        stalls.clear()
        val wantVlc = preferVlc
        currentUrl = channel.streamUrl
        altFormatTried = false
        val current = engine
        if (current == null) {
            com.fitztv.data.QualityScout.activePlayers.incrementAndGet()
            repo.probe.cancelInFlight()   // never compete with a quality check for the provider's connection
        }
        val e = if (current != null && (current is VlcEngine) == wantVlc && engineTuning == tuning) current else {
            current?.release()
            engineTuning = tuning
            if (wantVlc) VlcEngine(context, settings.value.copy(
                userAgent = tuning.userAgent(settings.value.userAgent),
                bufferSeconds = (settings.value.bufferSeconds * tuning.bufferFactor).toInt().coerceAtMost(60),
            ), this) else ExoEngine(context, settings.value, this, multiview, tuning)
        }
        engine = e
        if (!multiview) PlaybackQueue.channels.indexOfFirst { it.id == channel.id }.takeIf { it >= 0 }?.let { PlaybackQueue.index = it }
        e.setVolume(volume)
        if (!handleAudioFocus) e.setHandleAudioFocus(false)
        e.setAudioOffsetMs(settings.value.audioOffsets[syncKey(channel)] ?: settings.value.audioOffsetDefault)
        mode = if (channel.vod) PlayMode.VOD else PlayMode.LIVE
        pendingSeekMs = 0
        _state.value = PlayerState(channel = channel, engine = e, buffering = true,
            degraded = requested?.let { it.id != channel.id } ?: false, mode = mode)
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

    // ---- transport: pause / skip / from start / live -------------------------------------

    private var mode = PlayMode.LIVE
    private var archiveStartMs = 0L
    private var pendingSeekMs = 0L

    fun togglePause() {
        val e = engine ?: return
        if (_state.value.paused) { e.resume(); _state.update { it.copy(paused = false) }; lastPosChange = System.currentTimeMillis() }
        else { e.pause(); _state.update { it.copy(paused = true) } }
    }

    /** Skip back (negative) or forward. Uses the stream's own window, else the provider's catch-up. */
    fun skip(deltaMs: Long) {
        val e = engine ?: return
        val t = e.timeline()
        val now = System.currentTimeMillis()
        when {
            mode == PlayMode.ARCHIVE -> {
                val target = archiveStartMs + t.positionMs + deltaMs
                if (target >= now - 5_000) { goLive(); return }
                if (t.seekable && (t.durationMs == 0L || t.positionMs + deltaMs in 0..t.durationMs)) e.seekTo(t.positionMs + deltaMs)
                else openArchive(target)
            }
            t.seekable -> {
                val target = t.positionMs + deltaMs
                if (t.live && t.durationMs > 0 && target >= t.durationMs - 1_000) { flash("You're watching live"); e.seekToLive(); return }
                if (target < 0 && t.live && playing?.hasCatchup == true) { openArchive(now - (t.durationMs - t.positionMs) + deltaMs); return }
                e.seekTo(target)
            }
            mode == PlayMode.LIVE && playing?.hasCatchup == true -> {
                if (deltaMs > 0) { flash("You're watching live"); return }
                openArchive(now + deltaMs)
            }
            deltaMs > 0 && mode == PlayMode.LIVE -> flash("You're watching live")
            else -> flash("This stream can't rewind (no catch-up). Pause still works.")
        }
    }

    /** Restart the game (or current programme) from the beginning. */
    fun playFromStart(game: Game?) {
        val e = engine ?: return
        val ch = playing ?: return
        val now = System.currentTimeMillis()
        if (mode == PlayMode.VOD) { e.seekTo(0); return }
        val start = game?.startUtcMillis?.takeIf { it < now }
            ?: repo.nowPlaying(ch)?.startMillis
        when {
            ch.hasCatchup && start != null -> { openArchive(start); flash("Playing from the start") }
            e.timeline().seekable && e.seekTo(0) -> flash("Playing from the start of the stream's window")
            else -> flash("Play from start needs a channel with catch-up (TV archive)")
        }
    }

    /** Back to the live edge. */
    fun goLive() {
        val e = engine ?: return
        if (_state.value.paused) { e.resume(); _state.update { it.copy(paused = false) } }
        when (mode) {
            PlayMode.ARCHIVE -> {
                mode = PlayMode.LIVE
                playing?.let { ch -> currentUrl = ch.streamUrl; markBuffering(true); e.play(ch.streamUrl) }
                _state.update { it.copy(mode = PlayMode.LIVE, archiveStartMs = 0, message = "Back to live") }
            }
            PlayMode.LIVE -> { e.seekToLive(); flash("Live") }
            PlayMode.VOD -> Unit
        }
    }

    fun seekTo(positionMs: Long) { engine?.seekTo(positionMs) }

    /** Switch the current live channel to its catch-up archive at wall-clock [targetMs]. */
    private fun openArchive(targetMs: Long) {
        val e = engine ?: return
        val ch = playing ?: return
        val now = System.currentTimeMillis()
        val oldest = now - ch.archiveDays * 86_400_000L
        val target = targetMs.coerceIn(oldest, now - 10_000)
        val startMin = target - target % 60_000   // archives are addressed by the minute
        val duration = ((now - startMin) / 60_000 + 240).toInt()
        val url = Catchup.url(ch, startMin, duration) ?: run { flash("Catch-up isn't available on this channel"); return }
        mode = PlayMode.ARCHIVE
        archiveStartMs = startMin
        pendingSeekMs = target - startMin
        currentUrl = url
        retries = 0
        markBuffering(true)
        e.play(url)
        val zone = settings.value.zone()
        _state.update {
            it.copy(mode = PlayMode.ARCHIVE, archiveStartMs = startMin, paused = false,
                message = "Watching from ${com.fitztv.ui.components.Fmt.time(target, zone, settings.value.use24h)}")
        }
    }

    fun release() {
        flushWatch()
        watchdog?.cancel(); retryJob?.cancel(); upgradeJob?.cancel()
        if (engine != null) com.fitztv.data.QualityScout.activePlayers.decrementAndGet()
        engine?.release(); engine = null
        _state.update { it.copy(engine = null) }
        repo.probe.save()
    }

    // ---- EngineCallback ------------------------------------------------------------------

    override fun onBuffering(buffering: Boolean) {
        if (buffering && _state.value.buffering.not() && playingSince > 0) {
            val now = System.currentTimeMillis()
            stalls.addLast(now)
            while (stalls.isNotEmpty() && now - stalls.first() > 90_000) stalls.removeFirst()
            playing?.let { repo.probe.recordStall(it) }
            if (noteTrouble()) return
            if (stalls.size >= 3) { stalls.clear(); stepDown("Repeated buffering") }
        }
        markBuffering(buffering)
    }

    private var countedPlayFor: String? = null

    override fun onPlaying() {
        retries = 0
        playing?.let { ch -> if (countedPlayFor != ch.id) { countedPlayFor = ch.id; repo.probe.recordPlay(ch) } }
        if (pendingSeekMs > 0) { val p = pendingSeekMs; pendingSeekMs = 0; engine?.seekTo(p) }
        markBuffering(false)
        _state.update { it.copy(message = null) }
    }

    override fun onError(kind: ErrorKind, message: String) {
        when (kind) {
            ErrorKind.ENDED -> when (mode) {
                PlayMode.VOD -> _state.update { it.copy(message = "Replay finished", buffering = false) }
                PlayMode.ARCHIVE -> goLive()        // caught up with live
                PlayMode.LIVE -> engine?.reconnect()
            }
            ErrorKind.BEHIND_LIVE -> engine?.reconnect()
            ErrorKind.UNSUPPORTED -> {
                if (engine is ExoEngine && settings.value.engine == Engine.AUTO && !vlcFallbackUsed) {
                    vlcFallbackUsed = true
                    _state.update { it.copy(message = "Switching to VLC engine…") }
                    playing?.let { start(it, preferVlc = true, keepTuning = true) }
                } else if (!failover("Stream won't play")) scheduleRetry(message)
            }
            ErrorKind.NETWORK -> scheduleRetry(friendly(message))
        }
    }

    override fun onFirstFrame() { frameSeen = true; upgradedFrom = null }

    override fun onVideoInfo(width: Int, height: Int, fps: Float, decoder: String?) {
        playing?.let { repo.probe.record(it, height, fps); engine?.bitrateKbps()?.let { b -> repo.probe.recordBitrate(it, b) } }
        if (height > 0 && !multiview && PlaybackQueue.fromStartPending) {
            PlaybackQueue.fromStartPending = false
            scope.launch { delay(500); playFromStart(PlaybackQueue.gameId?.let { repo.game(it) }) }
        }
        if (height > 0) playing?.let { cur ->
            if (upgradeCheckedFor != cur.id && upgradeJob == null) upgradeJob = scope.launch {
                delay(8_000)   // let adaptive HLS ramp up before judging the resolution
                upgradeJob = null
                if (playing?.id == cur.id) maybeUpgrade(cur)
            }
        }
        _state.update { it.copy(width = width, height = height, fps = fps, decoder = decoder ?: it.decoder) }
    }

    // ---- recovery --------------------------------------------------------------------------

    private fun friendly(code: String): String = when {
        code.contains("BAD_HTTP_STATUS") -> "provider busy"
        code.contains("TIMEOUT") -> "network slow"
        code.contains("NETWORK_CONNECTION_FAILED") -> "connection dropped"
        code.contains("FILE_NOT_FOUND") -> "stream offline"
        else -> "connection hiccup"
    }

    private fun scheduleRetry(reason: String) {
        retryJob?.cancel()
        if (noteTrouble()) return
        retries++
        if (mode == PlayMode.ARCHIVE && retries >= 3) {
            flash("Catch-up unavailable – back to live")
            goLive()
            return
        }
        // Xtream serves every channel as both HLS and MPEG-TS: if one keeps failing, try the other.
        if (retries == 3 && !altFormatTried) {
            altUrl(currentUrl)?.let { alt ->
                altFormatTried = true
                currentUrl = alt
                _state.update { it.copy(message = "Trying ${if (alt.endsWith(".ts")) "MPEG-TS" else "HLS"} stream…") }
                engine?.play(alt)
                return
            }
        }
        // After a few failed reconnects, try a different variant of the same channel.
        if (retries == 5 && failover("Stream unavailable")) return
        // The first couple of retries are quick and silent: providers often take a moment to free a slot.
        val wait = if (retries <= 2) 400L else minOf(500L * (1L shl minOf(retries - 1, 4)), 8_000L)
        if (retries > 2) _state.update { it.copy(message = "Reconnecting… ($reason)") }
        retryJob = scope.launch {
            delay(wait)
            if (retries > 6 && engine is ExoEngine && settings.value.engine == Engine.AUTO && !vlcFallbackUsed) {
                vlcFallbackUsed = true
                playing?.let { start(it, preferVlc = true, keepTuning = true) }
            } else {
                engine?.reconnect()
            }
        }
    }

    private fun altUrl(url: String?): String? {
        if (url == null || !url.contains("/live/")) return null
        return when {
            url.endsWith(".m3u8") -> url.removeSuffix(".m3u8") + ".ts"
            url.endsWith(".ts") -> url.removeSuffix(".ts") + ".m3u8"
            else -> null
        }
    }

    /** Lower quality one step. Returns true if something changed. */
    private fun stepDown(reason: String): Boolean {
        val e = engine ?: return false
        lastStepDownAt = System.currentTimeMillis()
        upgradeAfterMs = minOf(upgradeAfterMs * 2, 30 * 60_000L)
        if (e.lowerQuality()) {
            _state.update { it.copy(degraded = true, message = "$reason – lowering quality") }
            return true
        }
        val cur = playing ?: return false
        val lower = nextCandidate(cur)
        if (lower != null) {
            start(lower, e is VlcEngine)
            _state.update { it.copy(degraded = true, message = "$reason – switched to ${lower.label}") }
            return true
        }
        e.reconnect()
        return false
    }

    private var upgradeCheckedFor: String? = null
    private var autoUpgrades = 0
    private var upgradeJob: kotlinx.coroutines.Job? = null
    private var lastStepDownAt = 0L

    /**
     * Seamless upgrade: once a stream shows its real resolution, if it is lower than another
     * candidate for this game (same or better language, measured — or this one is SD), switch
     * once to the sharper one. At most twice per game so it can't ping-pong.
     */
    private fun maybeUpgrade(cur: Channel) {
        if (upgradeCheckedFor == cur.id || mode != PlayMode.LIVE || candidates.size < 2 || autoUpgrades >= 2) return
        // Never undo a deliberate step down for buffering.
        if (System.currentTimeMillis() - lastStepDownAt < 5 * 60_000L) return
        upgradeCheckedFor = cur.id
        val p = repo.probe
        val mine = p.resClass(cur)
        val lang = com.fitztv.data.StreamLanguage.tier(cur)
        val game = PlaybackQueue.gameId?.let { repo.game(it) }
        val curConfirmed = game != null && repo.verdict(cur, game) == com.fitztv.data.GameMatcher.Verdict.CONFIRMED
        val better = candidates.asSequence()
            .filter { it.id != cur.id && !p.recentlyFailed(it) }
            // Only to a stream we're as sure carries this game: never from a confirmed stream to a guess.
            .filter { c ->
                val v = game?.let { repo.verdict(c, it) } ?: com.fitztv.data.GameMatcher.Verdict.UNKNOWN
                v != com.fitztv.data.GameMatcher.Verdict.CONTRADICTED && (!curConfirmed || v == com.fitztv.data.GameMatcher.Verdict.CONFIRMED)
            }
            .filter { com.fitztv.data.StreamLanguage.tier(it) <= lang }
            .filter { !repo.isPriority(cur) || repo.isPriority(it) }   // never leave a priority category
            .filter { p.resClass(it) > mine && (p.isMeasured(it) || com.fitztv.sports.Text.qualityTag(it.name) != null || mine == 0) }
            .maxWithOrNull(compareBy<Channel> { p.resClass(it) }.thenBy { p.isMeasured(it) }.thenBy { p.effectiveKbps(it) })
            ?: return
        autoUpgrades++
        start(better, engine is VlcEngine)
        upgradedFrom = cur
        _state.update { it.copy(message = "Switched to a sharper stream – ${p.label(better)}") }
    }

    /** The current stream is dead: remember that and move to the next-ranked stream. */
    private fun failover(reason: String): Boolean {
        val cur = playing ?: return false
        repo.probe.markFailed(cur)
        val next = nextCandidate(cur) ?: return false
        vlcFallbackUsed = false
        start(next, preferVlc = settings.value.engine == Engine.VLC)
        _state.update { it.copy(degraded = true, message = "$reason – trying ${next.label}") }
        return true
    }

    /**
     * Next stream down the ranking: the ranked game list when we have one, otherwise other
     * quality variants of this channel. Skips streams that failed recently.
     */
    private fun nextCandidate(cur: Channel): Channel? {
        val pool = if (candidates.size > 1) candidates
        else listOf(cur) + repo.variantsOf(cur).sortedWith(
            compareBy<Channel> { if (repo.probe.resClass(it) == 0) 1 else 0 }
                .thenBy { com.fitztv.data.StreamLanguage.tier(it) }
                .thenByDescending { repo.probe.rank(it) }.thenByDescending { repo.probe.fps(it) }
        )
        val i = pool.indexOfFirst { it.id == cur.id }
        val after = if (i >= 0) pool.drop(i + 1) else pool
        return after.firstOrNull { !repo.probe.recentlyFailed(it) }
            ?: pool.firstOrNull { it.id != cur.id && !repo.probe.recentlyFailed(it) }
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
                    if (noteTrouble()) continue
                    if (stalls.size >= 2) { stalls.clear(); stepDown("Slow stream") } else e.reconnect()
                    continue
                }
                if (_state.value.paused) {
                    lastPosChange = now
                    _state.update { it.copy(timeline = e.timeline()) }
                    continue
                }
                _state.update { it.copy(timeline = e.timeline()) }
                // Time spent watching (picture on screen, not buffering) teaches which category works.
                if (playingSince > 0 && frameSeen && mode == PlayMode.LIVE && !multiview) {
                    watchedMs += 1_000
                    if (watchedMs >= 60_000) flushWatch()
                }
                // Playing but nothing on screen (an empty event slot, audio-only feed): move on.
                if (playingSince > 0 && !frameSeen && mode == PlayMode.LIVE && now - playingSince > 20_000) {
                    frameSeen = true   // one decision per stream
                    val back = upgradedFrom
                    val cur = playing
                    upgradedFrom = null
                    if (back != null && cur != null && back.id != cur.id && !repo.probe.recentlyFailed(back)) {
                        repo.probe.markFailed(cur)
                        start(back, e is VlcEngine)
                        _state.update { it.copy(message = "Sharper stream showed no picture – back to ${back.label}") }
                    } else if (!failover("No picture")) e.reconnect()
                    continue
                }
                if (playingSince > 0) {
                    val pos = e.positionMs()
                    if (pos != lastPos) { lastPos = pos; lastPosChange = now }
                    else if (now - lastPosChange > 6_000) {
                        // Frozen picture without a buffering event: kick the stream.
                        lastPosChange = now
                        playing?.let { repo.probe.recordStall(it) }
                        if (noteTrouble()) continue
                        _state.update { it.copy(message = "Picture froze – reconnecting") }
                        e.reconnect()
                    }
                    // An escalated tuning that then played smoothly for 2 minutes is remembered for this channel.
                    if (!fixRecorded && tuning.level > 0 && now - playingSince > 120_000) {
                        fixRecorded = true
                        playing?.let { runCatching { repo.fixes.remember(it, tuning) } }
                    }
                    // Played smoothly for 3 minutes: remember this channel for the league / teams.
                    if (!hintRecorded && !multiview && mode == PlayMode.LIVE && !_state.value.degraded && now - playingSince > 90_000) {
                        hintRecorded = true
                        val ch = playing
                        val g = PlaybackQueue.gameId?.let { repo.game(it) }
                        if (ch != null && g != null) runCatching {
                            repo.hints.record(g, ch, _state.value.height, _state.value.fps, repo.matcher.looksLikeEvent(ch.name))
                        }
                    }
                    // Stable for a while: go back up to the best quality.
                    if (mode == PlayMode.LIVE && _state.value.degraded && now - playingSince > upgradeAfterMs) {
                        playingSince = now
                        val best = requested
                        if (best != null && playing?.id != best.id && !repo.probe.recentlyFailed(best)) {
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
