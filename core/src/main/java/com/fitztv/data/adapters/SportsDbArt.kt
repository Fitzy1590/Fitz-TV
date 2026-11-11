package com.fitztv.data.adapters

import com.fitztv.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

/** Transparent crests and league art from TheSportsDB's free key (cached in memory, throttled). */
object SportsDbArt {
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/123"
    private const val UA = "FitzTV"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class LeagueArt(val badge: String?, val logo: String?, val banner: String?)
    data class Broadcaster(val channel: String, val logo: String?, val country: String?)

    private val teams = ConcurrentHashMap<String, String>()
    private val leagues = ConcurrentHashMap<String, LeagueArt>()
    private val tv = ConcurrentHashMap<String, List<Broadcaster>>()

    /** Transparent team crest (PNG) by team name; blocking, call off the main thread. */
    fun teamCrest(team: String): String? {
        if (team.isBlank()) return null
        teams[team]?.let { return it.ifEmpty { null } }
        val first = rows("searchteams.php?t=${enc(team)}", "teams").firstOrNull()
        val url = first?.s("strBadge") ?: first?.s("strLogo")
        teams[team] = url.orEmpty()
        return url
    }

    /** League badge / logo / banner by TheSportsDB league id (e.g. 4387 = NBA). */
    fun league(id: String): LeagueArt? {
        leagues[id]?.let { return it }
        val l = rows("lookupleague.php?id=$id", "leagues").firstOrNull() ?: return null
        return LeagueArt(l.s("strBadge"), l.s("strLogo"), l.s("strBanner")).also { leagues[id] = it }
    }

    /** Channels that showed / will show an event (id = TheSportsDB idEvent), with their logos. */
    fun broadcasters(eventId: String): List<Broadcaster> = tv.getOrPut(eventId) {
        rows("lookuptv.php?id=$eventId", "tvevent").mapNotNull { r ->
            Broadcaster(r.s("strChannel") ?: return@mapNotNull null, r.s("strLogo"), r.s("strCountry"))
        }.distinctBy { it.channel }
    }

    private fun rows(path: String, key: String): List<JsonObject> = runCatching {
        Http.throttleSportsDb()
        val root = json.parseToJsonElement(Http.getString("$BASE/$path", UA)) as? JsonObject
        (root?.get(key) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }.getOrDefault(emptyList())

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun JsonObject.s(key: String) = ((this[key] as? JsonElement) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
