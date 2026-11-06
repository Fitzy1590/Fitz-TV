package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Sport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

enum class Engine { AUTO, EXOPLAYER, VLC }


/** AUTO = HLS when the provider allows it (most resilient), otherwise MPEG-TS. */
enum class XtreamFormat { AUTO, HLS, TS }

@Serializable
data class AppSettings(
    val sources: List<Source> = emptyList(),
    val engine: Engine = Engine.AUTO,
    val xtreamFormat: XtreamFormat = XtreamFormat.AUTO,
    /** Blank = follow the Shield's system time zone. */
    val timeZone: String = "Australia/Adelaide",
    val use24h: Boolean = false,
    val userAgent: String = DEFAULT_UA,
    /** Seconds of video to keep buffered ahead (bigger = fewer stalls, slower zaps). */
    val bufferSeconds: Int = 30,
    val matchFrameRate: Boolean = true,
    val hiddenSports: Set<String> = Sport.hiddenByDefault,
    val favoriteTeams: Set<String> = DEFAULT_FAVOURITES,
    /** Extra XMLTV guides (e.g. added from a phone via the QR code). */
    val extraEpgUrls: List<String> = emptyList(),
    /** Extra loudness for quiet IPTV audio, in dB (0 = off). */
    val audioBoostDb: Int = 0,
    /** Background photo of a favourite team, rotating hourly. */
    val teamTheme: Boolean = true,
    /** Optional YouTube Data API key: opens the exact highlights video instead of a search. */
    val youtubeApiKey: String = "",
    /** GitHub token (read-only) for in-app updates while the repository is private. */
    val githubToken: String = "",
    /** Check GitHub for a newer build each time the app starts. */
    val autoUpdateCheck: Boolean = true,
    /** Game ids with a kick-off reminder. */
    val reminders: Set<String> = emptySet(),
    /** Pop-up when a favourite team's game starts. */
    val teamStartAlerts: Boolean = true,
    /** Banners for favourite-team scores and close finishes. */
    val scoreAlerts: Boolean = true,
    /** Open straight into the player when your team's game starts (if watching something else). */
    val autoSwitchToTeam: Boolean = false,
    /** One-off settings upgrades already applied. */
    val migrations: Int = 0,
) {
    /** Per-settings cache: a new settings object (e.g. favourites edited) starts a fresh one. */
    @kotlinx.serialization.Transient
    private val favCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isFavourite(g: Game): Boolean = favCache.getOrPut(g.id + "|" + g.home + "|" + g.away) {
        favoriteTeams.any { isTeam(it, g.home, g.sport) || isTeam(it, g.away, g.sport) }
    }

    /** Games for the guide / upcoming rows: featured leagues, live games and favourite teams. */
    fun showInGuide(g: Game): Boolean =
        (isFavourite(g) || (!g.favouritesOnly && (g.featured || g.gameState == GameState.LIVE))) && sportVisible(g.sport)

    fun sportVisible(sport: Sport) = sport == Sport.GENERAL || sport.name !in hiddenSports

    /** Always Adelaide time (ACST/ACDT, daylight saving handled automatically). */
    fun zone(): ZoneId = ZONE

    companion object {
        const val DEFAULT_UA = "VLC/3.0.21 LibVLC/3.0.21"
        /** The app's fixed time zone for the guide, game times, clock and daily refresh. */
        val ZONE: ZoneId = ZoneId.of("Australia/Adelaide")
        val DEFAULT_FAVOURITES = setOf(
            "Houston Rockets", "Manchester United", "Green Bay Packers",
            "Adelaide United", "Socceroos", "Boomers", "Port Adelaide",
        )
    }
}

/** Team names match ignoring case, accents and punctuation ("Man. United" style variants aside). */
fun sameTeam(a: String, b: String): Boolean {
    if (a.isBlank() || b.isBlank()) return false
    return com.fitztv.sports.Text.norm(a) == com.fitztv.sports.Text.norm(b)
}

/** Tiny SharedPreferences-backed store. Nothing ever leaves the device. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fitz", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    private fun load(): AppSettings {
        val s = prefs.getString("settings", null)
            ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?: return AppSettings(migrations = 1)
        // 1: add Adelaide United, Socceroos, Boomers and Port Adelaide (AFL + AFLW) to My teams.
        if (s.migrations < 1) {
            val up = s.copy(favoriteTeams = s.favoriteTeams + setOf("Adelaide United", "Socceroos", "Boomers", "Port Adelaide"), migrations = 1)
            prefs.edit().putString("settings", json.encodeToString(up)).apply()
            return up
        }
        return s
    }

    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit().putString("settings", json.encodeToString(next)).apply()
    }
}
