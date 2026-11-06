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
) {
    fun isFavourite(g: Game): Boolean = favoriteTeams.any { it.equals(g.home, true) || it.equals(g.away, true) }

    fun sportVisible(sport: Sport) = sport == Sport.GENERAL || sport.name !in hiddenSports

    fun zone(): ZoneId = runCatching { if (timeZone.isBlank()) null else ZoneId.of(timeZone) }
        .getOrNull() ?: ZoneId.systemDefault()

    companion object {
        const val DEFAULT_UA = "VLC/3.0.21 LibVLC/3.0.21"
        val DEFAULT_FAVOURITES = setOf("Houston Rockets", "Manchester United", "Green Bay Packers")
    }
}

/** Tiny SharedPreferences-backed store. Nothing ever leaves the device. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fitz", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state
    val value: AppSettings get() = _state.value

    private fun load(): AppSettings = prefs.getString("settings", null)
        ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
        ?: AppSettings()

    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit().putString("settings", json.encodeToString(next)).apply()
    }
}
