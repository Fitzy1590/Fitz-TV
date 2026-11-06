package com.fitztv.sports

import com.fitztv.data.Game
import com.fitztv.data.Http
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

    fun fetch(league: League, from: LocalDate, days: Int): List<Game> {
        val games = ArrayList<Game>()
        league.espnPath?.let { games += runCatching { espn(league, it, from, days) }.getOrDefault(emptyList()) }
        if (games.isEmpty() && league.sportsDbSport != null) {
            games += runCatching { sportsDb(league, from, days) }.getOrDefault(emptyList())
        }
        return games
    }

    // ---- ESPN site API ---------------------------------------------------------------

    private fun espn(league: League, path: String, from: LocalDate, days: Int): List<Game> {
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
            homeScore = homeC?.s("score")?.takeIf { twoSided },
            awayScore = awayC?.s("score")?.takeIf { twoSided },
            startUtcMillis = start,
            state = status?.s("state") ?: "pre",
            statusText = status?.s("shortDetail") ?: "",
            venue = comp?.get("venue").obj?.s("fullName"),
            broadcasts = broadcasts,
        )
    }

    // ---- TheSportsDB (cricket, rugby, AFL fallback) -----------------------------------

    private fun sportsDb(league: League, from: LocalDate, days: Int): List<Game> {
        val sport = league.sportsDbSport!!.replace(" ", "%20")
        val out = ArrayList<Game>()
        for (i in 0 until days) {
            val d = from.plusDays(i.toLong())
            val url = "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$d&s=$sport"
            val root = runCatching { json.parseToJsonElement(Http.getString(url, UA)).obj }.getOrNull() ?: continue
            val events = root["events"] as? JsonArray ?: continue
            for (ev in events) {
                val e = ev.obj ?: continue
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
                    league = e.s("strLeague") ?: league.name,
                    title = e.s("strEvent") ?: "$home vs $away",
                    home = home, away = away, homeShort = home, awayShort = away,
                    homeLogo = e.s("strHomeTeamBadge"), awayLogo = e.s("strAwayTeamBadge"),
                    homeScore = e.s("intHomeScore"), awayScore = e.s("intAwayScore"),
                    startUtcMillis = start, state = state, statusText = st,
                    venue = e.s("strVenue"),
                )
            }
        }
        return out
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
