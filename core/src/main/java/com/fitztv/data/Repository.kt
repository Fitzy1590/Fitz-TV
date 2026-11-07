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
    val logos = LogoStore(context)
    val teams = TeamDirectory(context, logos)

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

    private val _vod = MutableStateFlow<List<VodItem>>(emptyList())
    /** Sport replays from the providers' on-demand libraries. */
    val vod: StateFlow<List<VodItem>> = _vod

    private val _guideReport = MutableStateFlow<String?>(null)
    /** Games loaded per league on the last guide refresh — shown in Settings. */
    val guideReport: StateFlow<String?> = _guideReport

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
        read<Game>("games-v3")?.let { _games.value = it.items; gamesSavedAt = it.savedAt }
        read<VodItem>("vod")?.let { _vod.value = it.items }
        read<EpgProgram>("epg")?.let { c -> _epg.value = c.items.groupBy { it.channelEpgId } }
        rebuildMatcher()
    }

    /** Refreshes whatever is stale. Called on launch, on resume and by the daily worker. */
    suspend fun refreshIfStale(force: Boolean = false) {
        loadFromDisk()
        if (!refreshing.compareAndSet(false, true)) return
        try {
            val now = System.currentTimeMillis()
            // The guide refreshes every time the app is opened or comes back from the background
            // (at most every 10 minutes); it runs in the background and never blocks the UI.
            if (force || now - gamesSavedAt > TimeUnit.MINUTES.toMillis(10) || isNewDay(gamesSavedAt)) refreshSchedule()
            else refreshLiveScores()
            if (force || now - channelsSavedAt > TimeUnit.HOURS.toMillis(12) || isNewDay(channelsSavedAt)) refreshChannels()
            teams.load(force)
        } finally {
            refreshing.set(false)
        }
    }

    private fun isNewDay(t: Long): Boolean {
        val zone = settings.value.zone()
        return java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate() != LocalDate.now(zone)
    }

    private val scheduleLock = Mutex()
    private val mergeLock = Any()

    /**
     * Downloads the guide in the background. Each league is merged in as soon as it arrives,
     * so the guide fills in progressively and nothing (like picking a game) ever waits on it;
     * a league that fails keeps its previous fixtures.
     */
    suspend fun refreshSchedule() = scheduleLock.withLock {
        withContext(Dispatchers.IO) {
            _status.value = "Updating TV guide in the background…"
            val from = LocalDate.now(settings.value.zone()).minusDays(1)
            val gate = Semaphore(6)
            val perLeague = coroutineScope {
                Leagues.all.map { lg ->
                    // Featured leagues keep a week of results for the Replays tab.
                    val back = when {
                        lg.featured || lg.favouritesOnly -> if (lg.espnPath != null) 6L else 2L
                        else -> if (lg.espnPath != null) 3L else 0L   // other leagues: enough for favourites' recent results
                    }
                    async {
                        val got = gate.withPermit {
                            runCatching { ScheduleClient.fetch(lg, from.minusDays(back), 9 + back.toInt()) }.getOrDefault(emptyList())
                        }
                        if (got.isNotEmpty()) mergeLeague(lg.name, got)
                        lg.name to got
                    }
                }.awaitAll()
            }
            _guideReport.value = perLeague.filter { Leagues.byName(it.first)?.featured == true || (it.second.isNotEmpty() && Leagues.byName(it.first)?.favouritesOnly != true) }
                .joinToString("  •  ") { (name, g) -> "$name ${g.size}" } +
                "   (updated ${java.time.LocalTime.now(settings.value.zone()).withNano(0).withSecond(0)})"
            if (perLeague.any { it.second.isNotEmpty() }) {
                gamesSavedAt = System.currentTimeMillis()
                write("games-v3", Cache(gamesSavedAt, _games.value))
                _status.value = null
                // Look up any still-missing logos after the guide is on screen, then fill them in.
                val missing = _games.value.flatMap { g -> listOfNotNull(g.home.takeIf { g.homeLogo == null }, g.away.takeIf { g.awayLogo == null }) }
                if (missing.isNotEmpty()) {
                    logos.resolve(missing)
                    synchronized(mergeLock) { _games.value = logos.fill(_games.value) }
                    write("games-v3", Cache(gamesSavedAt, _games.value))
                }
            } else {
                _status.value = "Couldn't update the guide — showing the last saved one"
            }
        }
    }

    /** Replace one league's fixtures in the live game list (the UI updates immediately). */
    private fun mergeLeague(league: String, games: List<Game>) {
        logos.learn(games)
        val filled = logos.fill(games)
        synchronized(mergeLock) {
            val ids = filled.map { it.id }.toSet()
            _games.value = (_games.value.filter { it.league != league && it.id !in ids } + filled).sortedBy { it.startUtcMillis }
        }
    }

    /**
     * Quick refresh of live scores, and of games that should have started by now, so new
     * games appear in "Live now" within a couple of minutes of kick-off.
     */
    @Volatile private var lastLiveRefresh = 0L

    suspend fun refreshLiveScores() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Home screen, alerts and the screensaver all ask for this: at most once every 45 s.
        if (now - lastLiveRefresh < 45_000) return@withContext
        lastLiveRefresh = now
        val today = LocalDate.now(settings.value.zone())
        val active = _games.value.filter {
            it.gameState == GameState.LIVE ||
                (it.gameState == GameState.PRE && it.startUtcMillis in (now - TimeUnit.HOURS.toMillis(4))..(now + TimeUnit.MINUTES.toMillis(10)))
        }.map { it.league }.toSet()
        if (active.isEmpty()) return@withContext
        val fresh = coroutineScope {
            Leagues.all.filter { it.name in active }.map { lg ->
                async { runCatching { ScheduleClient.fetch(lg, today.minusDays(1), 2) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }.flatten().let { logos.fill(it) }.associateBy { it.id }
        if (fresh.isEmpty()) return@withContext
        val known = _games.value.map { it.id }.toSet()
        _games.value = (_games.value.map { fresh[it.id] ?: it } + fresh.values.filter { it.id !in known })
            .sortedBy { it.startUtcMillis }
    }

    /** Live games from every league, favourites and featured leagues first. */
    fun liveNow(sport: Sport?): List<Game> = _games.value
        .filter { it.gameState == GameState.LIVE && (sport == null || it.sport == sport) && (!it.favouritesOnly || settings.value.isFavourite(it)) }
        .sortedWith(
            compareByDescending<Game> { settings.value.isFavourite(it) }
                .thenByDescending { it.featured }
                .thenBy { it.startUtcMillis }
        )

    /**
     * Background art for the team theme. Rotates through favourite teams once an hour
     * ([hourIndex] = hours since epoch), cycling through each team's photos.
     */
    suspend fun themeArt(hourIndex: Long): ThemeArt? {
        val favs = settings.value.favoriteTeams.sorted()
        if (favs.isEmpty()) return null
        for (i in favs.indices) {
            val team = favs[((hourIndex + i) % favs.size).toInt()]
            val photos = logos.fanart(team)
            if (photos.isNotEmpty()) {
                val photo = photos[((hourIndex / favs.size) % photos.size).toInt()]
                return ThemeArt(team, photo, logos.get(team))
            }
        }
        return null
    }

    data class TeamSchedule(val live: Game?, val upcoming: List<Game>, val results: List<Game>)

    /** A team's live game, its next 7 days and its last few results. */
    fun teamSchedule(team: String): TeamSchedule {
        val now = System.currentTimeMillis()
        val mine = _games.value.filter { isTeam(team, it.home, it.sport) || isTeam(team, it.away, it.sport) }
        return TeamSchedule(
            live = mine.firstOrNull { it.gameState == GameState.LIVE },
            upcoming = mine.filter { it.gameState == GameState.PRE && it.startUtcMillis < now + TimeUnit.DAYS.toMillis(7) }
                .sortedBy { it.startUtcMillis },
            results = mine.filter { it.gameState == GameState.FINAL }.sortedByDescending { it.startUtcMillis }.take(5),
        )
    }

    /** Finished games available to replay, newest first (favourites first within a day). */
    fun replayGames(sport: Sport?): List<Game> {
        val now = System.currentTimeMillis()
        return _games.value.filter {
            it.gameState == GameState.FINAL && (sport == null || it.sport == sport) &&
                now - it.startUtcMillis < TimeUnit.DAYS.toMillis(8) && settings.value.showInGuide(it)
        }.sortedWith(compareByDescending<Game> { it.startUtcMillis / 86_400_000 }
            .thenByDescending { settings.value.isFavourite(it) }.thenByDescending { it.startUtcMillis })
    }

    data class ReplayOption(val label: String, val detail: String, val channel: Channel, val startOffsetMs: Long = 0)

    /**
     * Ways to watch a finished game: full game from a channel's catch-up archive (best stream
     * first), then matching replays in the VOD library.
     */
    suspend fun replayOptions(game: Game): List<ReplayOption> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val daysAgo = ((now - game.startUtcMillis) / 86_400_000L).toInt() + 1
        val start = game.startUtcMillis - TimeUnit.MINUTES.toMillis(10)
        val duration = when (game.sport) { Sport.NFL -> 240; Sport.CRICKET -> 270; Sport.MLB -> 220; else -> 180 }
        val archive = matcher.scoredFor(game)
            .filter { (c, score) -> c.hasCatchup && c.archiveDays >= daysAgo && score >= 45 }
            .sortedWith(compareByDescending<Pair<Channel, Int>> { if (it.second >= 85) 1 else 0 }
                .thenByDescending { probe.height(it.first) }.thenByDescending { it.second })
            .mapNotNull { (c, _) ->
                Catchup.url(c, start, duration)?.let { url ->
                    ReplayOption("Full game • ${c.label}", "From the channel's catch-up archive", c.copy(id = "replay:${c.id}:$start", streamUrl = url, vod = true, name = "${game.matchup} (replay)"))
                }
            }
        val keys = listOf(game.homeShort, game.awayShort, game.home, game.away)
            .filter { it.length >= 3 }.map { com.fitztv.sports.Text.norm(it) }
        val vodMatches = _vod.value.filter { v ->
            v.sport == game.sport || v.sport == Sport.GENERAL
        }.filter { v ->
            val n = com.fitztv.sports.Text.norm(v.title + " " + v.category)
            val home = keys.take(1).any { n.contains(it) } || (keys.size > 2 && n.contains(keys[2]))
            val away = keys.drop(1).take(1).any { n.contains(it) } || (keys.size > 3 && n.contains(keys[3]))
            if (game.home.isBlank()) n.contains(com.fitztv.sports.Text.norm(game.title)) else home && away
        }.take(10).map { ReplayOption(it.label, "Replay library • ${it.category}", it.asChannel()) }
        archive + vodMatches
    }

    /** Next game to start (for the "nothing live" message). */
    fun nextUp(sport: Sport?): Game? {
        val now = System.currentTimeMillis()
        return _games.value.filter { it.gameState == GameState.PRE && it.startUtcMillis > now && (sport == null || it.sport == sport) && settings.value.showInGuide(it) }
            .minByOrNull { it.startUtcMillis }
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
            val vodItems = ArrayList<VodItem>()
            for (src in s.sources) {
                runCatching {
                    when (src.type) {
                        SourceType.XTREAM -> {
                            all += XtreamClient.loadSportsChannels(src, s.userAgent, s.xtreamFormat)
                            epgUrls += XtreamClient.epgUrl(src)
                            vodItems += runCatching { XtreamClient.loadSportsVod(src, s.userAgent) }.getOrDefault(emptyList())
                        }
                        SourceType.M3U -> {
                            val r = M3uParser.load(src, s.userAgent)
                            all += r.channels
                            vodItems += r.vod
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
            if (vodItems.isNotEmpty() || errors.isEmpty()) {
                val sorted = vodItems.distinctBy { it.id }.sortedByDescending { it.addedMillis }
                _vod.value = sorted
                write("vod", Cache(System.currentTimeMillis(), sorted))
            }
            rebuildMatcher()

            _status.value = "Loading programme guide…"
            val wanted = all.mapNotNull { it.epgId }.toSet()
            epgUrls += s.extraEpgUrls
            if (wanted.isNotEmpty() && epgUrls.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val programs = epgUrls.flatMap { url ->
                    runCatching {
                        XmltvParser.load(url, s.userAgent, wanted, now - TimeUnit.DAYS.toMillis(3), now + TimeUnit.DAYS.toMillis(7))
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
        // Resolution class: 4K always first, then 1080p, then 720p, then SD.
        fun resClass(c: Channel) = probe.height(c).let { h -> when { h >= 2000 -> 3; h >= 1000 -> 2; h >= 700 -> 1; else -> 0 } }
        return scored.sortedWith(
            compareBy<Pair<Channel, Int>> { probe.recentlyFailed(it.first) }
                // Streams that are definitely this game first; possible ones (league / broadcaster channels) after.
                .thenBy { if (it.second >= 60) 0 else 1 }
                .thenByDescending { resClass(it.first) }
                .thenByDescending { probe.fps(it.first) }
                .thenByDescending { probe.isMeasured(it.first) }
                .thenByDescending { it.second }
        ).map { it.first }
    }

    sealed interface Route {
        data class Streams(val ranked: List<Channel>) : Route
        data object None : Route
    }

    /** How to watch a game: every matching IPTV stream, best resolution / frame rate first. */
    suspend fun route(context: android.content.Context, game: Game): Route {
        val ranked = rankedStreams(game)
        return if (ranked.isNotEmpty()) Route.Streams(ranked) else Route.None
    }

    data class QuickChannel(val label: String, val channels: List<Channel>)

    /**
     * Pinned channels, always shown in their sections: Fox Footy & Fox Sports News (All),
     * Fox Footy (AFL), NBA TV & the Houston Rockets' channel (NBA), NFL Network & RedZone (NFL).
     * Best copy in your sources first.
     */
    fun quickChannels(sport: Sport?): List<QuickChannel> {
        val wanted = buildList {
            if (sport == null || sport == Sport.AFL) add("Fox Footy" to listOf("fox footy"))
            if (sport == null) add("Fox Sports News" to listOf("fox sports news"))
            if (sport == Sport.NBA) {
                add("NBA TV" to listOf("nba tv", "nba network", "nbatv"))
                add("Houston Rockets" to listOf("space city home network", "schn", "rockets tv", "houston rockets", "rockets"))
            }
            if (sport == Sport.NFL) {
                add("NFL Network" to listOf("nfl network", "nfl net", "nfln"))
                add("NFL RedZone" to listOf("nfl redzone", "redzone", "red zone"))
            }
        }
        val all = _channels.value
        return wanted.map { (label, keys) ->
            // Keys are in priority order (e.g. the Rockets' regional network before event channels).
            val matches = keys.flatMap { k ->
                all.filter { c -> val n = com.fitztv.sports.Text.norm(c.label); n.contains(" $k ") }
                    .sortedWith(compareByDescending<Channel> { probe.height(it) }.thenByDescending { probe.fps(it) })
            }.distinctBy { it.id }
            QuickChannel(label, matches)
        }
    }

    fun channelsFor(sport: Sport?): List<Channel> =
        _channels.value.filter { sport == null || it.sport == sport }

    /** Lower-quality alternatives for the same feed, best first (excluding [channel]). */
    fun variantsOf(channel: Channel): List<Channel> =
        _channels.value.filter { it.variantKey == channel.variantKey && it.id != channel.id }
            .sortedByDescending { it.quality }

    /** The live (or about to start) game a channel is most likely showing, for live stats. */
    fun gameFor(channel: Channel): Game? {
        val now = System.currentTimeMillis()
        return _games.value.asSequence()
            .filter { it.gameState == GameState.LIVE || (it.gameState == GameState.PRE && it.startUtcMillis - now < 30 * 60_000) }
            .mapNotNull { g -> matcher.scoredFor(g).firstOrNull { it.first.id == channel.id }?.let { g to it.second } }
            .filter { it.second >= 60 }
            .maxByOrNull { it.second }?.first
    }

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
