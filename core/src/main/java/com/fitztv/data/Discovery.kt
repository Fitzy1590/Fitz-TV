package com.fitztv.data

import com.fitztv.sports.Sport
import com.fitztv.sports.Text
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Rows for the grid TV guide: one row per channel (best copy of each), with its programmes. */
data class GridRow(val channel: Channel, val variants: List<Channel>, val programs: List<EpgProgram>)

fun Repository.gridRows(sport: Sport?, settings: AppSettings, limit: Int = 400): List<GridRow> {
    val epg = epg.value
    return channels.value
        .filter { it.epgId != null && (sport == null || it.sport == sport) && settings.sportVisible(it.sport) }
        .groupBy { it.variantKey }
        .values
        .mapNotNull { group ->
            val best = group.sortedWith(compareByDescending<Channel> { probe.height(it) }.thenByDescending { it.quality })
            val progs = best.firstNotNullOfOrNull { c -> epg[c.epgId]?.takeIf { it.isNotEmpty() } } ?: return@mapNotNull null
            GridRow(best.first(), best, progs)
        }
        .sortedWith(compareBy<GridRow> { it.channel.sport.ordinal }.thenBy { it.channel.label })
        .take(limit)
}

/** Search across teams, games, channels and replays. */
data class SearchResults(
    val teams: List<Team> = emptyList(),
    val games: List<Game> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val replays: List<VodItem> = emptyList(),
) {
    val isEmpty get() = teams.isEmpty() && games.isEmpty() && channels.isEmpty() && replays.isEmpty()
}

fun Repository.search(query: String, settings: AppSettings): SearchResults {
    val q = Text.norm(query).trim()
    if (q.length < 2) return SearchResults()
    fun hit(s: String) = Text.norm(s).contains(q)
    val now = System.currentTimeMillis()
    return SearchResults(
        teams = teams.teams.value.filter { hit(it.name) }.distinctBy { it.name }.take(20),
        games = games.value.filter { hit(it.home) || hit(it.away) || hit(it.title) || hit(it.league) || hit(it.homeShort) || hit(it.awayShort) }
            .sortedWith(compareBy<Game> { it.gameState != GameState.LIVE }.thenBy { kotlin.math.abs(it.startUtcMillis - now) })
            .take(40),
        channels = channels.value.filter { hit(it.label) || hit(it.group) }.sortedByDescending { it.quality }.distinctBy { it.variantKey }.take(40),
        replays = vod.value.filter { hit(it.title) || hit(it.category) }.take(40),
    )
}

/**
 * Shield ↔ Fold sync: all settings (sources, favourites, tokens, preferences) as one code
 * that goes phone → TV through the TV's QR page, or TV → phone via its "copy" page.
 * Travels only over your home network / clipboard.
 */
object SyncCode {
    private const val PREFIX = "FITZTV1:"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun export(s: AppSettings): String =
        PREFIX + android.util.Base64.encodeToString(json.encodeToString(s).toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)

    /** Returns the imported settings, or null if the code isn't valid. */
    fun import(code: String, store: SettingsStore): AppSettings? = runCatching {
        val body = code.trim().substringAfter(PREFIX)
        val incoming = json.decodeFromString<AppSettings>(String(android.util.Base64.decode(body, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)))
        store.update { cur ->
            incoming.copy(
                // Merge sources and favourites rather than wiping what's already on this device.
                sources = (cur.sources + incoming.sources).distinctBy { it.type.name + it.url + it.username },
                favoriteTeams = cur.favoriteTeams + incoming.favoriteTeams,
                githubToken = incoming.githubToken.ifBlank { cur.githubToken },
                youtubeApiKey = incoming.youtubeApiKey.ifBlank { cur.youtubeApiKey },
                migrations = maxOf(cur.migrations, incoming.migrations),
            )
        }
        store.value
    }.getOrNull()
}
