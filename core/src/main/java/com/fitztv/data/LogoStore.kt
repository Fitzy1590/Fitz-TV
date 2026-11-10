package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
private data class LogoFile(
    val logos: Map<String, String> = emptyMap(),
    val misses: Map<String, Long> = emptyMap(),
    /** Team -> background photos (TheSportsDB fanart, usually players in action). */
    val art: Map<String, List<String>> = emptyMap(),
    val artChecked: Map<String, Long> = emptyMap(),
)

data class ThemeArt(val team: String, val url: String, val logo: String?)

/**
 * Team name -> badge URL. Learns from every schedule download and team list, and looks up
 * anything still missing on TheSportsDB (by team name only). Kept on the device.
 */
class LogoStore(context: Context) {
    private val file = File(context.filesDir, "cache/logos.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val logos = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val art = ConcurrentHashMap<String, List<String>>()
    private val artChecked = ConcurrentHashMap<String, Long>()

    init {
        runCatching {
            val f = json.decodeFromString<LogoFile>(file.readText())
            logos.putAll(f.logos); misses.putAll(f.misses); art.putAll(f.art); artChecked.putAll(f.artChecked)
        }
    }

    /** Background photos for a team (fetched once, re-checked every 2 weeks). */
    suspend fun fanart(team: String): List<String> = withContext(Dispatchers.IO) {
        val k = key(team)
        val checked = artChecked[k] ?: 0
        if (checked > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)) return@withContext art[k].orEmpty()
        lookup(team)
        save()
        art[k].orEmpty()
    }

    private fun key(name: String) = Text.norm(name).trim()

    fun get(name: String): String? = logos[key(name)]

    fun learn(name: String, url: String?) {
        if (name.isBlank() || url.isNullOrBlank() || !url.startsWith("http")) return
        logos.putIfAbsent(key(name), url)
    }

    fun learn(games: List<Game>) = games.forEach { learn(it.home, it.homeLogo); learn(it.away, it.awayLogo) }

    /** Fill in any missing logos on these games from the store. */
    fun fill(games: List<Game>): List<Game> = games.map { g ->
        if ((g.homeLogo != null || g.home.isBlank()) && (g.awayLogo != null || g.away.isBlank())) g
        else g.copy(homeLogo = g.homeLogo ?: get(g.home), awayLogo = g.awayLogo ?: get(g.away))
    }

    /** Look up teams we have no logo for (skipping recent misses), a few at a time. */
    suspend fun resolve(names: Collection<String>) = withContext(Dispatchers.IO) {
        val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val todo = names.filter { it.isNotBlank() && get(it) == null && (misses[key(it)] ?: 0) < weekAgo }
            .distinctBy { key(it) }.take(40)
        if (todo.isEmpty()) return@withContext
        val gate = Semaphore(3)
        coroutineScope { todo.map { n -> async { gate.withPermit { lookup(n) } } }.awaitAll() }
        save()
    }

    private fun lookup(name: String) {
        val alias = TeamAliases.of(name)
        val query = alias?.team ?: name
        val url = "https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=" + URLEncoder.encode(query, "UTF-8")
        val team = runCatching {
            Http.throttleSportsDb()
            val root = json.parseToJsonElement(Http.getString(url, "FitzTV")) as? JsonObject
            val teams = (root?.get("teams") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            if (alias == null) teams.firstOrNull()
            else teams.firstOrNull { (it["strSport"] as? JsonPrimitive)?.content.equals(alias.tsdbSport, true) }
        }.getOrNull()
        fun str(k: String) = (team?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.startsWith("http") }
        val k = key(name)
        val badge = listOf("strBadge", "strTeamBadge", "strLogo").firstNotNullOfOrNull { str(it) }
        if (badge != null) logos.putIfAbsent(k, badge) else if (logos[k] == null) misses[k] = System.currentTimeMillis()
        val photos = (1..4).mapNotNull { str("strFanart$it") ?: str("strTeamFanart$it") }
        if (team != null) {
            art[k] = photos
            artChecked[k] = System.currentTimeMillis()
        }
    }

    fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(LogoFile(logos.toMap(), misses.toMap(), art.toMap(), artChecked.toMap())))
        }
    }
}
