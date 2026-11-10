package com.fitztv.sports

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fitztv.data.Game
import com.fitztv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A highlight clip that plays inside the app (from ESPN's game summary). */
data class Clip(val title: String, val url: String, val durationSec: Int, val thumb: String?, val publishedAt: Long = 0)

enum class HighlightKind(val label: String, val words: String) {
    HIGHLIGHTS("Highlights", "highlights"),
    EXTENDED("Extended highlights", "extended highlights"),
    CONDENSED("Condensed game", "condensed game"),
}

/**
 * Game highlights: ESPN clips (in-app) and YouTube (official league / club uploads).
 * With a YouTube Data API key the exact video opens; without one, YouTube opens on a
 * search for that game.
 */
object Highlights {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun kindsFor(game: Game): List<HighlightKind> = when (game.sport) {
        Sport.NFL, Sport.NBA, Sport.NBL -> HighlightKind.entries
        else -> listOf(HighlightKind.HIGHLIGHTS, HighlightKind.EXTENDED)
    }

    /** ESPN video clips for a game (direct MP4/HLS links). */
    suspend fun espnClips(game: Game): List<Clip> = withContext(Dispatchers.IO) {
        if (!game.id.startsWith("espn:") || game.espnPath == null) return@withContext emptyList()
        runCatching {
            val url = "https://site.api.espn.com/apis/site/v2/sports/${game.espnPath}/summary?event=${game.id.removePrefix("espn:")}"
            val root = json.parseToJsonElement(Http.getString(url, "FitzTV")) as? JsonObject ?: return@runCatching emptyList()
            parseVideos(root).sortedByDescending { it.title.contains("Game Highlights", true) || it.title.contains("Highlights", true) && it.durationSec >= 45 }
        }.getOrDefault(emptyList())
    }

    /** Playable clips from an ESPN game summary, newest first. */
    fun parseVideos(root: JsonObject): List<Clip> =
        (root["videos"] as? JsonArray).orEmpty().mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val links = o["links"] as? JsonObject
            val src = links?.get("source") as? JsonObject
            val href = listOf(
                (src?.get("HD") as? JsonObject)?.str("href"),
                src?.str("href"),
                ((links?.get("mobile") as? JsonObject)?.get("source") as? JsonObject)?.str("href"),
                ((links?.get("HLS") as? JsonObject))?.str("href"),
            ).firstOrNull { it != null && it.startsWith("http") } ?: return@mapNotNull null
            val at = (o.str("lastModified") ?: o.str("originalPublishDate"))?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
            Clip(
                title = o.str("headline") ?: o.str("title") ?: "Highlights",
                url = href,
                durationSec = (o["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0,
                thumb = o.str("thumbnail"),
                publishedAt = at,
            )
        }.sortedByDescending { it.publishedAt }

    /** Search words that match how the leagues title their uploads, using the game's local date. */
    fun query(game: Game, kind: HighlightKind): String {
        val zone = when (game.sport) {
            Sport.NBA, Sport.NFL, Sport.NHL, Sport.MLB -> ZoneId.of("America/New_York")
            Sport.SOCCER -> if (game.league in setOf("A-League", "WC Qualifying", "Asian Cup")) ZoneId.of("Australia/Sydney") else ZoneId.of("Europe/London")
            else -> ZoneId.of("Australia/Sydney")
        }
        val date = Instant.ofEpochMilli(game.startUtcMillis).atZone(zone).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
        val teams = if (game.home.isBlank()) game.title else "${game.away} vs ${game.home}"
        val words = when {
            kind == HighlightKind.EXTENDED && (game.sport == Sport.NBA) -> "full game highlights"
            else -> kind.words
        }
        return "$teams $words ${game.league} $date"
    }

    /** An official upload on the league's YouTube channel. */
    data class YtVideo(val id: String, val title: String, val channel: String, val durationSec: Int, val thumb: String?)

    /** Official league channels (IDs checked against each channel's page). */
    private fun officialChannel(game: Game): Pair<String, String>? = when (game.league) {
        "NBA" -> "UCWJ2lWNubArHWmf3FIHbfcQ" to "NBA"
        "NFL" -> "UCDVYQ4Zhbm3S2dlz7P1GBDg" to "NFL"
        "NBL" -> "UCQOt5-Mc5m03JCO_W9SWySA" to "NBL"
        "AFL", "AFLW" -> "UCxAeHdLDAatFV_vt95MSO-g" to "AFL"
        "A-League" -> "UCzRogd_oK3bzKvAW-4aLuPQ" to "A-Leagues"
        "Premier League" -> "UCG5qGWdu8nIRZqJ_GgDwQ-w" to "Premier League"
        "BBL", "WBBL" -> "UCIZTs-TArrzk13rk0kSV-Cw" to "BBL"
        "International Cricket" -> "UCkBY0aHJP9BwjZLDYxAQrKg" to "cricket.com.au"
        "NRL" -> "UC33-OkQ6VCYk8xtml8Pk4-g" to "NRL"
        else -> null
    }

    private val ytCache = java.util.concurrent.ConcurrentHashMap<String, List<YtVideo>>()

    /**
     * The league's own uploads for this game (needs your YouTube Data API key): searched only in
     * the official channel, from kick-off to 3 days after, both teams in the title, longest first
     * (extended / full-game highlights before short clips). ~101 quota units per game, cached.
     */
    suspend fun officialVideos(game: Game, apiKey: String): List<YtVideo> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || game.home.isBlank()) return@withContext emptyList()
        val (channelId, channelName) = officialChannel(game) ?: return@withContext emptyList()
        ytCache[game.id]?.let { return@withContext it }
        val found = runCatching {
            val after = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(game.startUtcMillis - 3_600_000))
            val before = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(game.startUtcMillis + 3 * 86_400_000L))
            val q = "${game.awayShort.ifBlank { game.away }} ${game.homeShort.ifBlank { game.home }}"
            val key = URLEncoder.encode(apiKey, "UTF-8")
            val search = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=10&order=relevance" +
                "&channelId=$channelId&publishedAfter=$after&publishedBefore=$before&q=${URLEncoder.encode(q, "UTF-8")}&key=$key"
            val root = json.parseToJsonElement(Http.getString(search, "FitzTV")) as? JsonObject ?: return@runCatching emptyList()
            val hk = com.fitztv.data.TeamKeys.of(game.home, game.homeShort, game.sport) + com.fitztv.data.TeamKeys.weak(game.home, game.homeShort, game.homeAbbr, game.sport)
            val ak = com.fitztv.data.TeamKeys.of(game.away, game.awayShort, game.sport) + com.fitztv.data.TeamKeys.weak(game.away, game.awayShort, game.awayAbbr, game.sport)
            val hits = (root["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { item ->
                val id = (item["id"] as? JsonObject)?.str("videoId") ?: return@mapNotNull null
                val sn = item["snippet"] as? JsonObject ?: return@mapNotNull null
                val title = sn.str("title")?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() } ?: return@mapNotNull null
                val n = Text.norm(title)
                if (!(com.fitztv.data.TeamKeys.mentioned(n, hk) && com.fitztv.data.TeamKeys.mentioned(n, ak))) return@mapNotNull null
                val thumb = ((sn["thumbnails"] as? JsonObject)?.get("medium") as? JsonObject)?.str("url")
                YtVideo(id, title, channelName, 0, thumb)
            }
            if (hits.isEmpty()) return@runCatching emptyList()
            // Lengths (1 quota unit) so extended highlights can go first.
            val vids = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=${hits.joinToString(",") { it.id }}&key=$key"
            val dur = (json.parseToJsonElement(Http.getString(vids, "FitzTV")) as? JsonObject)?.let { r ->
                (r["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.associate { v ->
                    v.str("id").orEmpty() to isoSeconds((v["contentDetails"] as? JsonObject)?.str("duration"))
                }
            }.orEmpty()
            hits.map { it.copy(durationSec = dur[it.id] ?: 0) }.sortedByDescending { it.durationSec }
        }.getOrDefault(emptyList())
        if (found.isNotEmpty()) ytCache[game.id] = found
        found
    }

    /** "PT12M34S" → 754. */
    private fun isoSeconds(d: String?): Int {
        if (d == null) return 0
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").find(d) ?: return 0
        val (h, mi, se) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
    }

    /** Opens an official video in the YouTube app (or SmartTube / browser). */
    suspend fun openVideo(context: Context, id: String) = withContext(Dispatchers.Main) { watch(context, id) }

    /** Opens YouTube on the best match: the video itself with an API key, otherwise search results. */
    suspend fun open(context: Context, game: Game, kind: HighlightKind, apiKey: String) {
        val q = query(game, kind)
        val id = if (apiKey.isNotBlank()) findVideo(q, apiKey, game) else null
        withContext(Dispatchers.Main) { if (id != null) watch(context, id) else search(context, q) }
    }

    private fun findVideo(q: String, key: String, game: Game): String? = runCatching {
        val after = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(game.startUtcMillis - 3_600_000))
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=8&order=relevance" +
            "&publishedAfter=$after&q=${URLEncoder.encode(q, "UTF-8")}&key=${URLEncoder.encode(key, "UTF-8")}"
        val root = json.parseToJsonElement(Http.getString(url, "FitzTV")) as? JsonObject ?: return null
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val official = listOf(game.league, "NBA", "NFL", "AFL", "Premier League", "NBL", "cricket.com.au", "Socceroos", game.home, game.away)
            .filter { it.length >= 3 }
        val best = items.firstOrNull { item ->
            val ch = (item["snippet"] as? JsonObject)?.str("channelTitle").orEmpty()
            official.any { ch.contains(it, ignoreCase = true) }
        } ?: items.firstOrNull()
        (best?.get("id") as? JsonObject)?.str("videoId")
    }.getOrNull()

    private fun watch(context: Context, id: String) {
        val tries = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")),
        )
        tries.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
    }

    private fun search(context: Context, q: String) {
        val enc = Uri.encode(q)
        val tries = listOf(
            // Android TV YouTube app
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra(SearchManager.QUERY, q),
            // Phone YouTube app
            Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, q),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$enc")),
        )
        tries.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
    }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
