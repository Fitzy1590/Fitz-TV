package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Leagues
import com.fitztv.sports.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class Team(val name: String, val league: String, val sport: Sport, val logo: String? = null)

@Serializable
private data class TeamFile(val savedAt: Long, val teams: List<Team>)

/** Every team in every supported league, for picking favourites. Refreshed weekly. */
class TeamDirectory(context: Context, private val logos: LogoStore) {
    private val file = File(context.filesDir, "cache/teams.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams: StateFlow<List<Team>> = _teams
    private var savedAt = 0L

    init {
        runCatching { json.decodeFromString<TeamFile>(file.readText()) }.getOrNull()?.let { _teams.value = it.teams; savedAt = it.savedAt }
    }

    suspend fun load(force: Boolean = false) = lock.withLock {
        if (!force && _teams.value.isNotEmpty() && System.currentTimeMillis() - savedAt < TimeUnit.DAYS.toMillis(7)) return@withLock
        val fetched = withContext(Dispatchers.IO) {
            coroutineScope {
                Leagues.all.map { lg ->
                    async {
                        // Internationals: offer the nicknames (Socceroos, Boomers) rather than 200 national teams.
                        if (lg.favouritesOnly) return@async Builtin.teams[lg.name].orEmpty().map { Team(it, "Australia", lg.sport) }
                        val espn = lg.espnPath?.let { runCatching { espnTeams(lg.name, lg.sport, it) }.getOrDefault(emptyList()) }.orEmpty()
                        espn.ifEmpty { Builtin.teams[lg.name].orEmpty().map { Team(it, lg.name, lg.sport) } }
                    }
                }.awaitAll().flatten()
            }
        }
        if (fetched.isEmpty()) return@withLock
        fetched.forEach { logos.learn(it.name, it.logo) }
        logos.resolve(fetched.filter { it.logo == null }.map { it.name })
        val withLogos = fetched.map { if (it.logo == null) it.copy(logo = logos.get(it.name)) else it }
            .distinctBy { it.name + "|" + it.league }
        _teams.value = withLogos
        savedAt = System.currentTimeMillis()
        runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(TeamFile(savedAt, withLogos))) }
        logos.save()
    }

    private fun espnTeams(league: String, sport: Sport, path: String): List<Team> {
        val url = "https://site.api.espn.com/apis/site/v2/sports/$path/teams?limit=1000"
        val root = json.parseToJsonElement(Http.getString(url, "FitzTV")) as? JsonObject ?: return emptyList()
        val teams = ((((root["sports"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("leagues") as? JsonArray)
            ?.firstOrNull() as? JsonObject)?.get("teams") as? JsonArray ?: return emptyList()
        return teams.mapNotNull { el ->
            val t = (el as? JsonObject)?.get("team") as? JsonObject ?: return@mapNotNull null
            val name = (t["displayName"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val logo = ((t["logos"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("href")?.let { (it as? JsonPrimitive)?.content }
            Team(name, league, sport, logo)
        }
    }
}

/** Leagues whose team lists come from TheSportsDB fixtures rather than ESPN. */
private object Builtin {
    val teams = mapOf(
        "Friendly" to listOf("Socceroos", "Matildas"),
        "FIBA" to listOf("Boomers"),
        "NBL" to listOf(
            "Adelaide 36ers", "Brisbane Bullets", "Cairns Taipans", "Illawarra Hawks", "Melbourne United",
            "New Zealand Breakers", "Perth Wildcats", "South East Melbourne Phoenix", "Sydney Kings", "Tasmania JackJumpers",
        ),
        "BBL" to listOf(
            "Adelaide Strikers", "Brisbane Heat", "Hobart Hurricanes", "Melbourne Renegades",
            "Melbourne Stars", "Perth Scorchers", "Sydney Sixers", "Sydney Thunder",
        ),
        "AFL" to listOf(
            "Adelaide Crows", "Brisbane Lions", "Carlton", "Collingwood", "Essendon", "Fremantle", "Geelong Cats",
            "Gold Coast Suns", "GWS Giants", "Hawthorn", "Melbourne", "North Melbourne", "Port Adelaide", "Richmond",
            "St Kilda", "Sydney Swans", "West Coast Eagles", "Western Bulldogs",
        ),
        "NRL" to listOf(
            "Brisbane Broncos", "Canberra Raiders", "Canterbury Bulldogs", "Cronulla Sharks", "Dolphins",
            "Gold Coast Titans", "Manly Sea Eagles", "Melbourne Storm", "Newcastle Knights", "New Zealand Warriors",
            "North Queensland Cowboys", "Parramatta Eels", "Penrith Panthers", "South Sydney Rabbitohs",
            "St George Illawarra Dragons", "Sydney Roosters", "Wests Tigers",
        ),
    )
}
