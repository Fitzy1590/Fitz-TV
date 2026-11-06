package com.fitztv.data

import android.content.Context
import com.fitztv.sources.M3uParser
import com.fitztv.sources.XmltvParser
import com.fitztv.sources.XtreamClient
import com.fitztv.sports.Leagues
import com.fitztv.sports.ScheduleClient
import com.fitztv.sports.Sport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
private data class Cache<T>(val savedAt: Long, val items: List<T>)

class Repository(context: Context, private val settings: SettingsStore) {
    val probe = StreamProbe(context)

    private val dir = File(context.filesDir, "cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels
    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games
    private val _epg = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epg: StateFlow<Map<String, List<EpgProgram>>> = _epg
    private val _status = MutableStateFlow<String?>(null)
    /** Human readable status / error for the UI, null when idle. */
    val status: StateFlow<String?> = _status

    private var channelsSavedAt = 0L
    private var gamesSavedAt = 0L
    private val lock = Mutex()

    @Volatile var matcher: GameMatcher = GameMatcher(emptyList(), emptyMap())
        private set

    private val loaded = CompletableDeferred<Unit>()
    private val loadLock = Mutex()
    private val refreshing = AtomicBoolean(false)

    suspend fun loadFromDisk() = loadLock.withLock {
        if (loaded.isCompleted) return@withLock
        withContext(Dispatchers.IO) { readAll() }
        loaded.complete(Unit)
        Unit
    }

    private fun readAll() {
        read<Channel>("channels")?.let { _channels.value = it.items; channelsSavedAt = it.savedAt }
        read<Game>("games")?.let { _games.value = it.items; gamesSavedAt = it.savedAt }
        read<EpgProgram>("epg")?.let { c -> _epg.value = c.items.groupBy { it.channelEpgId } }
        rebuildMatcher()
    }

    /** Refreshes whatever is stale. Called on launch, on resume and by the daily worker. */
    suspend fun refreshIfStale(force: Boolean = false) {
        loadFromDisk()
        if (!refreshing.compareAndSet(false, true)) return
        try {
            val now = System.currentTimeMillis()
            if (force || now - gamesSavedAt > TimeUnit.HOURS.toMillis(3) || isNewDay(gamesSavedAt)) refreshSchedule()
            if (force || now - channelsSavedAt > TimeUnit.HOURS.toMillis(12) || isNewDay(channelsSavedAt)) refreshChannels()
        } finally {
            refreshing.set(false)
        }
    }

    private fun isNewDay(t: Long): Boolean {
        val zone = settings.value.zone()
        return java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate() != LocalDate.now(zone)
    }

    suspend fun refreshSchedule() = lock.withLock {
        withContext(Dispatchers.IO) {
            _status.value = "Updating TV guide…"
            val from = LocalDate.now(settings.value.zone()).minusDays(1)
            val gate = Semaphore(6)
            val all = coroutineScope {
                Leagues.all.filter { settings.value.sportVisible(it.sport) }.map { lg -> async { gate.withPermit { ScheduleClient.fetch(lg, from, 9) } } }.awaitAll()
            }.flatten().distinctBy { it.id }.sortedBy { it.startUtcMillis }
            if (all.isNotEmpty()) {
                _games.value = all
                gamesSavedAt = System.currentTimeMillis()
                write("games", Cache(gamesSavedAt, all))
                rebuildMatcher()
                _status.value = null
            } else {
                _status.value = "Couldn't download the schedule — check your connection"
            }
        }
    }

    /** Quick refresh of today's live scores only (used while the home screen is open). */
    suspend fun refreshLiveScores() = withContext(Dispatchers.IO) {
        val today = LocalDate.now(settings.value.zone())
        val liveLeagues = _games.value.filter { it.gameState == GameState.LIVE }.map { it.league }.toSet()
        if (liveLeagues.isEmpty()) return@withContext
        val fresh = Leagues.all.filter { it.name in liveLeagues }
            .flatMap { runCatching { ScheduleClient.fetch(it, today.minusDays(1), 2) }.getOrDefault(emptyList()) }
            .associateBy { it.id }
        if (fresh.isNotEmpty()) _games.value = _games.value.map { fresh[it.id] ?: it }
    }

    suspend fun refreshChannels() = lock.withLock {
        withContext(Dispatchers.IO) {
            val s = settings.value
            if (s.sources.isEmpty()) {
                _channels.value = emptyList(); rebuildMatcher(); return@withContext
            }
            _status.value = "Loading channels…"
            val errors = ArrayList<String>()
            val epgUrls = LinkedHashSet<String>()
            val all = ArrayList<Channel>()
            for (src in s.sources) {
                runCatching {
                    when (src.type) {
                        SourceType.XTREAM -> {
                            all += XtreamClient.loadSportsChannels(src, s.userAgent, s.xtreamFormat)
                            epgUrls += XtreamClient.epgUrl(src)
                        }
                        SourceType.M3U -> {
                            val r = M3uParser.load(src, s.userAgent)
                            all += r.channels
                            (src.epgUrl.ifBlank { r.epgUrl.orEmpty() }).takeIf { it.isNotBlank() }?.let { epgUrls += it }
                        }
                    }
                }.onFailure { errors += "${src.name}: ${it.message}" }
            }
            if (all.isNotEmpty() || errors.isEmpty()) {
                _channels.value = all
                channelsSavedAt = System.currentTimeMillis()
                write("channels", Cache(channelsSavedAt, all))
            }
            rebuildMatcher()

            _status.value = "Loading programme guide…"
            val wanted = all.mapNotNull { it.epgId }.toSet()
            epgUrls += s.extraEpgUrls
            if (wanted.isNotEmpty() && epgUrls.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val programs = epgUrls.flatMap { url ->
                    runCatching {
                        XmltvParser.load(url, s.userAgent, wanted, now - TimeUnit.HOURS.toMillis(4), now + TimeUnit.DAYS.toMillis(7))
                    }.getOrElse { errors += "EPG: ${it.message}"; emptyList() }
                }
                if (programs.isNotEmpty()) {
                    _epg.value = programs.groupBy { it.channelEpgId }.mapValues { (_, v) -> v.sortedBy { it.startMillis } }
                    write("epg", Cache(now, programs))
                    rebuildMatcher()
                }
            }
            _status.value = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
    }

    private fun rebuildMatcher() {
        matcher = GameMatcher(_channels.value, _epg.value)
    }

    /**
     * Every stream that looks like it's showing [game], best first. Streams are grouped by how
     * sure we are they carry this game (so a 4K channel showing something else never beats the
     * right game), then sorted by resolution, then frame rate. Recently failed streams go last.
     */
    suspend fun rankedStreams(game: Game): List<Channel> {
        val scored = withContext(Dispatchers.Default) { matcher.scoredFor(game) }
        if (scored.isEmpty()) return emptyList()
        probe.probe(scored.map { it.first }, settings.value.userAgent)
        fun tier(score: Int) = when { score >= 85 -> 0; score >= 60 -> 1; score >= 40 -> 2; else -> 3 }
        return scored.sortedWith(
            compareBy<Pair<Channel, Int>> { probe.recentlyFailed(it.first) }
                .thenBy { tier(it.second) }
                .thenByDescending { probe.height(it.first) }
                .thenByDescending { probe.fps(it.first) }
                .thenByDescending { it.second }
        ).map { it.first }
    }

    fun channelsFor(sport: Sport?): List<Channel> =
        _channels.value.filter { sport == null || it.sport == sport }

    /** Lower-quality alternatives for the same feed, best first (excluding [channel]). */
    fun variantsOf(channel: Channel): List<Channel> =
        _channels.value.filter { it.variantKey == channel.variantKey && it.id != channel.id }
            .sortedByDescending { it.quality }

    fun channel(id: String): Channel? = _channels.value.firstOrNull { it.id == id }
    fun game(id: String): Game? = _games.value.firstOrNull { it.id == id }

    fun nowPlaying(channel: Channel, at: Long = System.currentTimeMillis()): EpgProgram? =
        channel.epgId?.let { id -> _epg.value[id]?.firstOrNull { at in it.startMillis until it.stopMillis } }

    private inline fun <reified T> read(name: String): Cache<T>? = runCatching {
        json.decodeFromString<Cache<T>>(File(dir, "$name.json").readText())
    }.getOrNull()

    private inline fun <reified T> write(name: String, cache: Cache<T>) {
        runCatching {
            val tmp = File(dir, "$name.json.tmp")
            tmp.writeText(json.encodeToString(cache))
            tmp.renameTo(File(dir, "$name.json"))
        }
    }
}
