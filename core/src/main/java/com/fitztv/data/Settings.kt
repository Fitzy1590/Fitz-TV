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
    /**
     * Experimental: tunnelled playback — the video and audio hardware sync each other directly
     * (Android TV / Shield). Can be smoother with lower CPU use; some streams or audio setups
     * misbehave. ExoPlayer only, never in multiview; falls back silently where unsupported.
     */
    val tunneling: Boolean = false,
    /** Audio/video sync per channel (channel key → ms; + = audio later) and the default for others. */
    val audioOffsets: Map<String, Int> = emptyMap(),
    val audioOffsetDefault: Int = 0,
    val hiddenSports: Set<String> = Sport.hiddenByDefault,
    val favoriteTeams: Set<String> = DEFAULT_FAVOURITES,
    /** Extra XMLTV guides (e.g. added from a phone via the QR code). */
    val extraEpgUrls: List<String> = emptyList(),
    /** Extra loudness for quiet IPTV audio, in dB (0 = off). */
    val audioBoostDb: Int = 6,
    /** Background photo of a favourite team, rotating hourly. */
    val teamTheme: Boolean = true,
    /** Optional YouTube Data API key: opens the exact highlights video instead of a search. */
    val youtubeApiKey: String = "",
    /** Provider categories whose streams rank first (after "is it this game" and the SD rule). */
    val priorityGroups: List<String> = DEFAULT_PRIORITY_GROUPS,
    /** GitHub token (read-only) for in-app updates while the repository is private. */
    val githubToken: String = "",
    /** Check GitHub for a newer build each time the app starts. */
    val autoUpdateCheck: Boolean = true,
    /** When a newer build is found at startup, download it and open the installer without asking first. */
    val autoUpdateInstall: Boolean = true,
    /** Game ids with a kick-off reminder. */
    val reminders: Set<String> = emptySet(),
    /** Pop-up when a favourite team's game starts. */
    val teamStartAlerts: Boolean = true,
    /** Banners for favourite-team scores and close finishes. */
    val scoreAlerts: Boolean = true,
    /** Open straight into the player when your team's game starts (if watching something else). */
    val autoSwitchToTeam: Boolean = false,
    /** Colour theme id (see Palettes). */
    val theme: String = "esports",
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
        val DEFAULT_PRIORITY_GROUPS = listOf(
            "UK| FORMULA 1 PPV", "UK| EPL PREMIER LEAGUE PPV VIP", "UK| EPL PREMIER LEAGUE PPV", "UK| PREMIER LEAGUE+ PPV",
            "US| NFL PPV", "US| NFL REPLAY", "US| NBA PASS PPV 8K", "US| NBA PPV", "US| NBA TEAM PPV",
        )
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
    if (com.fitztv.sports.Text.norm(a) == com.fitztv.sports.Text.norm(b)) return true
    val ca = teamCore(a)
    return ca.isNotEmpty() && ca == teamCore(b)
}

/** Club suffixes / prefixes feeds add or drop: "Adelaide United FC" = "Adelaide United". */
private val clubWords = setOf("fc", "afc", "sc", "cf", "football", "club", "the")

private fun teamCore(s: String): String =
    com.fitztv.sports.Text.norm(s).trim().split(' ').filter { it.isNotEmpty() && it !in clubWords }.joinToString(" ")

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
            ?: return AppSettings(migrations = 4)
        // 1: add Adelaide United, Socceroos, Boomers and Port Adelaide (AFL + AFLW) to My teams.
        if (s.migrations < 1) {
            val up = s.copy(favoriteTeams = s.favoriteTeams + setOf("Adelaide United", "Socceroos", "Boomers", "Port Adelaide"), migrations = 1)
            prefs.edit().putString("settings", json.encodeToString(up)).apply()
            return migrate2(up)
        }
        return migrate2(s)
    }

    /** 2: the redesign — move the old default (Midnight) and the neon look to Scoreboard. */
    private fun migrate2(s: AppSettings): AppSettings {
        if (s.migrations >= 2) return migrate3(s)
        val up = s.copy(theme = if (s.theme == "midnight" || s.theme == "neon") "scoreboard" else s.theme, migrations = 2)
        prefs.edit().putString("settings", json.encodeToString(up)).apply()
        return migrate3(up)
    }

    /** 3: IPTV audio is often quiet — turn the audio boost on (+6 dB) once if it was off. */
    private fun migrate3(s: AppSettings): AppSettings {
        if (s.migrations >= 3) return migrate4(s)
        val up = s.copy(audioBoostDb = if (s.audioBoostDb == 0) 6 else s.audioBoostDb, migrations = 3)
        prefs.edit().putString("settings", json.encodeToString(up)).apply()
        return migrate4(up)
    }

    /** 4: the Esports teal look is the new default — move people still on the old amber default. */
    private fun migrate4(s: AppSettings): AppSettings {
        if (s.migrations >= 4) return s
        val up = s.copy(theme = if (s.theme == "scoreboard") "esports" else s.theme, migrations = 4)
        prefs.edit().putString("settings", json.encodeToString(up)).apply()
        return up
    }

    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit().putString("settings", json.encodeToString(next)).apply()
    }
}
