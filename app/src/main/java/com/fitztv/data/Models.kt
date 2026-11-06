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
)

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
) {
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
