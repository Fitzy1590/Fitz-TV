package com.fitztv.data

import com.fitztv.sports.Sport
import kotlinx.serialization.Serializable

@Serializable
enum class SourceType { XTREAM, M3U }

@Serializable
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    /** Xtream: server base URL (http://host:port). M3U: playlist URL. */
    val url: String,
    val username: String = "",
    val password: String = "",
    /** Optional XMLTV URL. Xtream sources derive it automatically when blank. */
    val epgUrl: String = "",
)

@Serializable
data class Channel(
    val id: String,
    val sourceId: String,
    val name: String,
    val group: String,
    val logo: String?,
    val streamUrl: String,
    val epgId: String?,
    val sport: Sport,
    /** 4 = UHD, 3 = FHD, 2 = HD, 1 = SD. */
    val quality: Int,
    /** Channels sharing a key are quality variants of the same feed. */
    val variantKey: String,
    /** Days of provider catch-up (TV archive) available; 0 = none. */
    val archiveDays: Int = 0,
    /** M3U catch-up type (default / append / shift / flussonic / xc) and template. */
    val catchup: String? = null,
    val catchupSource: String? = null,
    /** Xtream server time zone (timeshift URLs use server-local time). */
    val serverTz: String? = null,
    /** On-demand video (a replay), not a live channel. */
    val vod: Boolean = false,
) {
    val hasCatchup: Boolean get() = archiveDays > 0

    /** Cleaned-up name for display ("Lakers vs Celtics", "Fox Footy"). */
    val label: String get() = com.fitztv.sports.Text.cleanName(name)
}

enum class GameState { PRE, LIVE, FINAL }

@Serializable
data class Game(
    val id: String,
    val sport: Sport,
    val league: String,
    val title: String,
    val home: String,
    val away: String,
    val homeShort: String,
    val awayShort: String,
    val homeLogo: String? = null,
    val awayLogo: String? = null,
    val homeScore: String? = null,
    val awayScore: String? = null,
    val startUtcMillis: Long,
    /** "pre", "in" or "post". */
    val state: String,
    val statusText: String,
    val venue: String? = null,
    val broadcasts: List<String> = emptyList(),
    /** ESPN API path for live stats (null for non-ESPN fixtures). */
    val espnPath: String? = null,
    /** False for "other top leagues" that are only surfaced while live or for favourites. */
    val featured: Boolean = true,
    /** Only surfaced for favourite teams (internationals, AFLW). */
    val favouritesOnly: Boolean = false,
    /** Team colours (hex, no '#') and the league's logo, for matchup art. */
    val homeColor: String? = null,
    val awayColor: String? = null,
    val leagueLogo: String? = null,
    /** Team abbreviations from the feed ("BKN", "DAL"), for matching event channel names. */
    val homeAbbr: String? = null,
    val awayAbbr: String? = null,
) {
    /** "Rockets vs Lakers" – short team names only. */
    val matchup: String
        get() = if (home.isBlank()) title
        else "${awayShort.ifBlank { away }} vs ${homeShort.ifBlank { home }}"

    val gameState: GameState
        get() = when (state) {
            "in" -> GameState.LIVE
            "post" -> GameState.FINAL
            else -> GameState.PRE
        }
}

@Serializable
data class EpgProgram(
    val channelEpgId: String,
    val title: String,
    val desc: String,
    val startMillis: Long,
    val stopMillis: Long,
)

/** A replay in the provider's on-demand library. */
@Serializable
data class VodItem(
    val id: String,
    val title: String,
    val category: String,
    val sport: Sport,
    val url: String,
    val poster: String? = null,
    val addedMillis: Long = 0,
) {
    val label: String get() = com.fitztv.sports.Text.cleanName(title)

    /** When the game was played: the date written in the title, else when it was added. */
    val eventMillis: Long get() = ReplayTitles.titleDate(title) ?: addedMillis

    /** "Wisconsin Badgers at Notre Dame": no provider tag, quality words, date or season suffix. */
    val shortTitle: String get() = com.fitztv.sports.Text.vodTitle(title)

    /** Wrap as a playable "channel" for the player. */
    fun asChannel(sourceId: String = "vod") = Channel(
        id = id, sourceId = sourceId, name = title, group = category, logo = poster, streamUrl = url,
        epgId = null, sport = sport, quality = com.fitztv.sports.Text.quality(title), variantKey = id, vod = true,
    )
}
