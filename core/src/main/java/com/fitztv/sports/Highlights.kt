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
data class Clip(val title: String, val url: String, val durationSec: Int, val thumb: String?)

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
                Clip(
                    title = o.str("headline") ?: o.str("title") ?: "Highlights",
                    url = href,
                    durationSec = (o["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0,
                    thumb = o.str("thumbnail"),
                )
            }
        }.getOrDefault(emptyList())
    }

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
