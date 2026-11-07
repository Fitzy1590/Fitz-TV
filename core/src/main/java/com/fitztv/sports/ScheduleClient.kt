package com.fitztv.sports

import com.fitztv.data.Game
import com.fitztv.data.Http
import com.fitztv.data.TeamKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Downloads fixtures from public, key-less schedule feeds. Only the schedule itself is
 * requested — no identifiers, accounts or device information are sent anywhere.
 */
object ScheduleClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "FitzTV"
    private val day = DateTimeFormatter.BASIC_ISO_DATE

    /**
     * @param gapFill also ask TheSportsDB to fill gaps in ESPN-led leagues (slow: rate limited)
     * @param live a live-score refresh: TheSportsDB answers may only be a minute old
     */
    fun fetch(league: League, from: LocalDate, days: Int, gapFill: Boolean = true, live: Boolean = false): List<Game> {
        val games = ArrayList<Game>()
        league.espnPath?.let { games += runCatching { espn(league, it, from, days) }.getOrDefault(emptyList()) }
        if (league.espnCricketSeries) games += runCatching { espnCricketSeries(league, from, days) }.getOrDefault(emptyList())
        if (Thread.currentThread().isInterrupted) return games
        val useTsdb = league.sportsDbSport != null &&
            if (league.espnCricketSeries) gapFill else games.isEmpty()
        if (useTsdb) {
            val extra = runCatching { sportsDb(league, from, days, if (live) 60_000L else 10 * 60_000L) }.getOrDefault(emptyList())
            // TheSportsDB fills gaps only: skip fixtures ESPN already has (same teams within a day).
            games += extra.filter { t -> games.none { e -> sameFixture(e, t) } }
        }
        return games.distinctBy { it.id }
    }

    private fun sameFixture(a: Game, b: Game): Boolean {
        if (Math.abs(a.startUtcMillis - b.startUtcMillis) > 20 * 3_600_000L || a.home.isBlank() || b.home.isBlank()) return false
        val n = Text.norm(a.home + " " + a.away)
        return TeamKeys.mentioned(n, TeamKeys.of(b.home, b.homeShort, b.sport)) && TeamKeys.mentioned(n, TeamKeys.of(b.away, b.awayShort, b.sport))
    }

    // ---- ESPN cricket: international series ---------------------------------------------

    private val notSenior = Regex("""\bA tour\b|\bA$|Lions|Under-?1\d|U1\d|Women|\bXI\b|Emerging|Academy|Development|Youth""", RegexOption.IGNORE_CASE)
    private val intlSeries = Regex("""\btour of\b|^ICC|World Cup|Champions Trophy|Tri-?Series|Tri-?Nation|Ashes|Trophy \d{4}""", RegexOption.IGNORE_CASE)

    /** ESPN gives each tour a new id; its cricket score panel lists the current ones. */
    private fun espnCricketSeries(league: League, from: LocalDate, days: Int): List<Game> {
        val root = json.parseToJsonElement(Http.getString("https://site.api.espn.com/apis/site/v2/sports/cricket/scorepanel", UA)).obj ?: return emptyList()
        val ids = (root["scores"] as? JsonArray).orEmpty().mapNotNull { sc ->
            val lg = (sc.obj?.get("leagues") as? JsonArray)?.firstOrNull()?.obj ?: return@mapNotNull null
            val name = lg.s("name") ?: return@mapNotNull null
            val id = lg.s("id") ?: return@mapNotNull null
            id.takeIf { intlSeries.containsMatchIn(name) && !notSenior.containsMatchIn(name) }
        }.distinct().take(8)
        return ids.flatMap { id -> runCatching { espn(league.copy(espnPath = "cricket/$id"), "cricket/$id", from, days) }.getOrDefault(emptyList()) }
    }

    // ---- ESPN site API ---------------------------------------------------------------

    /**
     * One request per day: ESPN's scoreboard only reliably returns a single day for NBA/NHL
     * style leagues (a date *range* silently drops later days), so ranges are not used.
     * Days are fetched in parallel and de-duplicated.
     */
    private fun espn(league: League, path: String, from: LocalDate, days: Int): List<Game> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until days).map { i ->
                pool.submit<List<Game>> {
                    val d = from.plusDays(i.toLong()).format(day)
                    val url = "https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?dates=$d&limit=200"
                    runCatching {
                        val root = json.parseToJsonElement(Http.getString(url, UA)).obj ?: return@runCatching emptyList()
                        val events = root["events"] as? JsonArray ?: return@runCatching emptyList()
                        events.mapNotNull { runCatching { espnEvent(league, it.obj!!) }.getOrNull() }
                    }.getOrDefault(emptyList())
                }
            }
            val all = futures.flatMap { f ->
                try {
                    f.get(20, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    // The refresh timed out: stop every day's request and give up on this league.
                    futures.forEach { it.cancel(true) }
                    Thread.currentThread().interrupt()
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (all.isNotEmpty()) return all.distinctBy { it.id }
        } finally {
            pool.shutdownNow()
        }
        if (Thread.currentThread().isInterrupted) return emptyList()
        // Fallback: some sports (soccer, NFL) do answer a range in one call.
        val range = "${from.format(day)}-${from.plusDays(days.toLong() - 1).format(day)}"
        val url = "https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?dates=$range&limit=500"
        val root = json.parseToJsonElement(Http.getString(url, UA)).obj ?: return emptyList()
        val events = root["events"] as? JsonArray ?: return emptyList()
        return events.mapNotNull { runCatching { espnEvent(league, it.obj!!) }.getOrNull() }
    }

    private fun espnEvent(league: League, e: JsonObject): Game? {
        val start = parseInstant(e.s("date") ?: return null)
        val comp = (e["competitions"] as? JsonArray)?.firstOrNull()?.obj
        val status = e["status"].obj?.get("type").obj
        val competitors = (comp?.get("competitors") as? JsonArray).orEmpty().mapNotNull { it.obj }
        fun side(which: String): JsonObject? =
            competitors.firstOrNull { it.s("homeAway") == which }
        val homeC = side("home") ?: competitors.getOrNull(0)
        val awayC = side("away") ?: competitors.getOrNull(1)
        fun name(c: JsonObject?) = c?.get("team").obj?.s("displayName") ?: c?.get("athlete").obj?.s("displayName") ?: ""
        fun short(c: JsonObject?) = c?.get("team").obj?.let { it.s("shortDisplayName") ?: it.s("name") }
            ?: c?.get("athlete").obj?.s("shortName") ?: ""
        fun logo(c: JsonObject?) = c?.get("team").obj?.s("logo")
        val twoSided = competitors.size == 2
        // Cricket: "140/7 (18.5/20 ov, target 189)" → score "140/7", the rest goes to the status line.
        val cricket = league.sport == com.fitztv.sports.Sport.CRICKET
        fun score(c: JsonObject?): String? = c?.s("score")?.let { if (cricket) it.substringBefore(" (").trim() else it }?.takeIf { it.isNotBlank() }
        val cricketDetail = if (cricket) competitors.mapNotNull { c -> c.s("score")?.takeIf { "(" in it }?.substringAfter("(")?.substringBeforeLast(")") }.lastOrNull() else null
        val broadcasts = (comp?.get("broadcasts") as? JsonArray).orEmpty()
            .flatMap { (it.obj?.get("names") as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .distinct()
        return Game(
            id = "espn:${e.s("id")}",
            sport = league.sport,
            league = league.name,
            title = e.s("name") ?: "",
            home = if (twoSided) name(homeC) else "",
            away = if (twoSided) name(awayC) else "",
            homeShort = if (twoSided) short(homeC) else "",
            awayShort = if (twoSided) short(awayC) else "",
            homeLogo = if (twoSided) logo(homeC) else null,
            awayLogo = if (twoSided) logo(awayC) else null,
            homeScore = score(homeC)?.takeIf { twoSided },
            awayScore = score(awayC)?.takeIf { twoSided },
            startUtcMillis = start,
            state = status?.s("state") ?: "pre",
            statusText = listOfNotNull(status?.s("shortDetail"), cricketDetail?.takeIf { status?.s("state") == "in" }).joinToString(" · "),
            venue = comp?.get("venue").obj?.s("fullName"),
            broadcasts = broadcasts,
            espnPath = league.espnPath,
            featured = league.featured,
            favouritesOnly = league.favouritesOnly,
        )
    }

    // ---- TheSportsDB (cricket, rugby, AFL fallback) -----------------------------------

    private fun sportsDb(league: League, from: LocalDate, days: Int, maxAgeMs: Long): List<Game> {
        val sport = league.sportsDbSport!!.replace(" ", "%20")
        val out = ArrayList<Game>()
        val byId = league.sportsDbIds.isNotEmpty()
        // Per league id when known (complete); per sport otherwise (the free key caps that at 3 events).
        val queries = if (byId) league.sportsDbIds.map { "l=$it" } else listOf("s=$sport")
        for (i in 0 until days) for (q in queries) {
            val d = from.plusDays(i.toLong())
            val url = "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$d&$q"
            val root = tsdbDay(url, maxAgeMs) ?: continue
            val events = root["events"] as? JsonArray ?: continue
            for (ev in events) {
                val e = ev.obj ?: continue
                val lg = e.s("strLeague").orEmpty()
                if (!byId && league.sportsDbLeague.isNotEmpty() && league.sportsDbLeague.none { lg.contains(it, true) }) continue
                if (!byId && league.sportsDbExclude.any { lg.contains(it, true) }) continue
                val start = e.s("strTimestamp")?.let { parseInstant(it) }
                    ?: runCatching {
                        LocalDateTime.parse("${e.s("dateEvent")}T${e.s("strTime") ?: "00:00:00"}")
                            .toInstant(ZoneOffset.UTC).toEpochMilli()
                    }.getOrNull() ?: continue
                val st = e.s("strStatus").orEmpty()
                val state = when {
                    st.equals("Match Finished", true) || st.equals("FT", true) || st.equals("AET", true) -> "post"
                    st.isNotBlank() && !st.equals("Not Started", true) && !st.equals("NS", true) &&
                        !st.equals("Time to be defined", true) && !st.equals("Postponed", true) -> "in"
                    else -> "pre"
                }
                val home = e.s("strHomeTeam").orEmpty()
                val away = e.s("strAwayTeam").orEmpty()
                out += Game(
                    id = "tsdb:${e.s("idEvent")}",
                    sport = league.sport,
                    league = league.name,
                    title = e.s("strEvent") ?: "$home vs $away",
                    home = home, away = away, homeShort = home, awayShort = away,
                    homeLogo = e.s("strHomeTeamBadge"), awayLogo = e.s("strAwayTeamBadge"),
                    homeScore = e.s("intHomeScore"), awayScore = e.s("intAwayScore"),
                    startUtcMillis = start, state = state, statusText = st,
                    venue = e.s("strVenue"),
                    featured = league.featured,
                    favouritesOnly = league.favouritesOnly,
                )
            }
        }
        return out
    }

    private val tsdbCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, JsonObject>>()

    /** One TheSportsDB day request shared by every league of that sport (free API is rate limited). */
    private fun tsdbDay(url: String, maxAgeMs: Long): JsonObject? {
        val now = System.currentTimeMillis()
        tsdbCache[url]?.takeIf { now - it.first < maxAgeMs }?.let { return it.second }
        Http.throttleSportsDb()
        val o = runCatching { json.parseToJsonElement(Http.getString(url, UA)).obj }.getOrNull() ?: return null
        tsdbCache[url] = now to o
        return o
    }

    // ---- helpers ------------------------------------------------------------------------

    private fun parseInstant(s: String): Long {
        val t = s.trim()
        return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t.replace("Z", ":00Z")).toInstant().toEpochMilli() } // ESPN: 2026-11-06T00:30Z
            .recoverCatching { LocalDateTime.parse(t.removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrThrow()
    }

    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private fun JsonObject.s(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it.isNotBlank() }
}
