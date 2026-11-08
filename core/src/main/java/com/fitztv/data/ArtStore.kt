package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Leagues
import com.fitztv.sports.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Fixture artwork: TheSportsDB matchup thumbnails (`strThumb`) for games in leagues it covers.
 * One request per league per day (rate-limited, background), matched to our games by both teams
 * and date, cached on disk. Games without one use the generated matchup art in the UI.
 */
class ArtStore(context: Context) {
    private val file = File(context.filesDir, "cache/art.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    /** game id -> thumbnail url ("" = looked, none). */
    private val thumbs = ConcurrentHashMap<String, String>()
    /** "leagueId|date" -> fetched at. */
    private val fetched = ConcurrentHashMap<String, Long>()
    private val _version = MutableStateFlow(0)
    /** Bumps when new artwork arrives, so screens can refresh. */
    val version: StateFlow<Int> = _version

    init {
        runCatching { thumbs.putAll(json.decodeFromString<Map<String, String>>(file.readText())) }
    }

    fun thumb(g: Game): String? = thumbs[g.id]?.takeIf { it.isNotEmpty() }

    private fun save() = runCatching {
        file.parentFile?.mkdirs()
        // Keep the cache small: drop entries for games long gone is not possible by id alone, so cap size.
        if (thumbs.size > 3000) thumbs.keys.take(thumbs.size - 3000).forEach { thumbs.remove(it) }
        file.writeText(json.encodeToString(thumbs.toMap()))
    }

    private fun core(name: String) = Text.norm(name).trim().split(' ')
        .filter { it.isNotEmpty() && it !in setOf("fc", "afc", "sc", "cf", "the", "club") }.joinToString(" ")

    /** Looks up thumbnails for [games] starting within the next ~2 days (and live ones). */
    suspend fun refresh(games: List<Game>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val want = games.filter {
            it.home.isNotBlank() && !thumbs.containsKey(it.id) && it.startUtcMillis in (now - 6 * 3_600_000L)..(now + 48 * 3_600_000L)
        }
        if (want.isEmpty()) return@withContext
        val byLeague = want.groupBy { it.league }
        var changed = false
        for ((leagueName, gs) in byLeague) {
            val id = Leagues.byName(leagueName)?.artLeagueId ?: continue
            val dates = gs.map { Instant.ofEpochMilli(it.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate() }.toSet()
            val events = dates.flatMap { d -> day(id, d) }
            for (g in gs) {
                val hk = TeamKeys.of(g.home, g.homeShort, g.sport)
                val ak = TeamKeys.of(g.away, g.awayShort, g.sport)
                val match = events.firstOrNull { (teams, ts, _) ->
                    TeamKeys.mentioned(teams, hk) && TeamKeys.mentioned(teams, ak) &&
                        (ts == null || Math.abs(ts - g.startUtcMillis) < 20 * 3_600_000L)
                }
                thumbs[g.id] = match?.third.orEmpty()
                changed = true
            }
        }
        if (changed) { save(); _version.value++ }
    }

    /** (normalised "home away", start millis, thumb url) for one league-day. */
    private fun day(id: String, d: LocalDate): List<Triple<String, Long?, String>> {
        val key = "$id|$d"
        if (fetched[key]?.let { System.currentTimeMillis() - it < 6 * 3_600_000L } == true) return emptyList()
        fetched[key] = System.currentTimeMillis()
        Http.throttleSportsDb()
        val root = runCatching {
            json.parseToJsonElement(Http.getString("https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$d&l=$id", "FitzTV")) as? JsonObject
        }.getOrNull() ?: return emptyList()
        return (root["events"] as? JsonArray).orEmpty().mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (e[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            val thumb = s("strThumb") ?: return@mapNotNull null
            val teams = Text.norm("${s("strHomeTeam").orEmpty()} ${s("strAwayTeam").orEmpty()}")
            val ts = s("strTimestamp")?.let { runCatching { java.time.LocalDateTime.parse(it.removeSuffix("Z").substringBefore('+')).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
            Triple(teams, ts, thumb)
        }
    }
}
