package com.fitztv.data.adapters

import com.fitztv.data.Game
import com.fitztv.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** One team-shooting line: "FG" 30-79 38%. */
data class ShootingSplit(val label: String, val away: String, val home: String, val awayPct: String, val homePct: String)

/** What the stream-buffer logic needs to know about the game right now. */
data class Situation(
    val live: Boolean,
    val period: Int,
    val clockSeconds: Int,
    val scoreDelta: Int,
    /** Halftime, end of period, or a timeout / commercial break. */
    val inBreak: Boolean,
    /** Basketball-style game (4 periods, then overtime). */
    val periodic: Boolean = true,
) {
    companion object { val NONE = Situation(false, 0, 0, 0, false) }
}

/** Live numbers from ESPN's public summary feed (no key). */
data class LiveSnapshot(
    val state: String,
    val statusDetail: String,
    val period: Int,
    val clock: String,
    val awayName: String,
    val homeName: String,
    val awayScore: Int,
    val homeScore: Int,
    /** Latest home win probability 0..1, when ESPN publishes it. */
    val homeWinProb: Double?,
    /** Home win probability over the game, oldest first, thinned to at most 60 points. */
    val momentum: List<Double>,
    val shooting: List<ShootingSplit>,
    val inBreak: Boolean,
    val clockSeconds: Int,
) {
    val live get() = state == "in"
    fun situation() = Situation(live, period, clockSeconds, kotlin.math.abs(homeScore - awayScore), inBreak)
}

object EspnLive {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "FitzTV"

    /** Fetches and parses the summary for an `espn:` game; null for other feeds or on any failure. */
    fun fetch(game: Game): LiveSnapshot? {
        val path = game.espnPath ?: return null
        if (!game.id.startsWith("espn:")) return null
        val url = "https://site.api.espn.com/apis/site/v2/sports/$path/summary?event=${game.id.removePrefix("espn:")}"
        return runCatching { parse(json.parseToJsonElement(Http.getString(url, UA))) }.getOrNull()
    }

    private class Cached(val at: Long, val snap: LiveSnapshot?)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    /**
     * Shared by the stats drawer and the buffer logic so one request serves both: returns the last
     * snapshot if it is younger than [maxAgeMs] (a failed fetch keeps the previous good one). Blocking.
     */
    fun latest(game: Game, maxAgeMs: Long = 8_000): LiveSnapshot? {
        val now = System.currentTimeMillis()
        cache[game.id]?.let { if (now - it.at < maxAgeMs) return it.snap }
        val fresh = fetch(game)
        val kept = fresh ?: cache[game.id]?.snap
        cache[game.id] = Cached(now, kept)
        return kept
    }

    fun parse(root: JsonElement): LiveSnapshot? {
        val r = root as? JsonObject ?: return null
        val comp = (r["header"].obj?.get("competitions") as? JsonArray)?.firstOrNull().obj ?: return null
        val status = comp["status"].obj
        val type = status?.get("type").obj
        val state = type?.s("state") ?: "pre"
        val competitors = (comp["competitors"] as? JsonArray).orEmpty().mapNotNull { it.obj }
        val away = competitors.firstOrNull { it.s("homeAway") == "away" } ?: competitors.getOrNull(1)
        val home = competitors.firstOrNull { it.s("homeAway") == "home" } ?: competitors.getOrNull(0)
        fun name(c: JsonObject?) = c?.get("team").obj?.let { it.s("abbreviation") ?: it.s("shortDisplayName") } ?: ""
        val clock = status?.s("displayClock") ?: ""
        val period = status?.get("period").let { (it as? JsonPrimitive)?.intOrNull } ?: 0

        // Probability series: thin to <= 60 points so the drawer can draw it cheaply.
        val wp = (r["winprobability"] as? JsonArray).orEmpty().mapNotNull { (it.obj?.get("homeWinPercentage") as? JsonPrimitive)?.doubleOrNull }
        val step = (wp.size + 59) / 60
        val momentum = if (step <= 1) wp else wp.filterIndexed { i, _ -> i % step == 0 || i == wp.lastIndex }

        // Shooting: pair the two teams' stats by name.
        val teams = (r["boxscore"].obj?.get("teams") as? JsonArray).orEmpty().mapNotNull { it.obj }
        val awayAbbr = away?.get("team").obj?.s("abbreviation")
        val boxAway = teams.firstOrNull { it.s("homeAway") == "away" || it["team"].obj?.s("abbreviation") == awayAbbr } ?: teams.getOrNull(0)
        val boxHome = teams.firstOrNull { it !== boxAway }
        fun stats(t: JsonObject?) = (t?.get("statistics") as? JsonArray).orEmpty().mapNotNull { it.obj }.associateBy { it.s("name") }
        val a = stats(boxAway)
        val h = stats(boxHome)
        fun split(label: String, made: String, pct: String): ShootingSplit? {
            val am = a[made]?.s("displayValue") ?: return null
            val hm = h[made]?.s("displayValue") ?: return null
            return ShootingSplit(label, am, hm, a[pct]?.s("displayValue")?.let { "$it%" } ?: "", h[pct]?.s("displayValue")?.let { "$it%" } ?: "")
        }
        val shooting = listOfNotNull(
            split("FG", "fieldGoalsMade-fieldGoalsAttempted", "fieldGoalPct"),
            split("3PT", "threePointFieldGoalsMade-threePointFieldGoalsAttempted", "threePointFieldGoalPct"),
            split("FT", "freeThrowsMade-freeThrowsAttempted", "freeThrowPct"),
        )

        val lastPlay = (r["plays"] as? JsonArray)?.lastOrNull().obj?.get("type").obj?.s("text").orEmpty().lowercase()
        val name = type?.s("name").orEmpty()
        val inBreak = name == "STATUS_HALFTIME" || name == "STATUS_END_PERIOD" ||
            (state == "in" && ("timeout" in lastPlay || lastPlay == "end period"))

        return LiveSnapshot(
            state = state,
            statusDetail = type?.s("shortDetail") ?: type?.s("detail") ?: "",
            period = period, clock = clock,
            awayName = name(away), homeName = name(home),
            awayScore = away?.s("score")?.toIntOrNull() ?: 0,
            homeScore = home?.s("score")?.toIntOrNull() ?: 0,
            homeWinProb = wp.lastOrNull(),
            momentum = momentum,
            shooting = shooting,
            inBreak = inBreak,
            clockSeconds = clockToSeconds(clock),
        )
    }

    /** "4:12" -> 252, "0.0" -> 0, "38.5" -> 38. */
    fun clockToSeconds(clock: String): Int {
        val parts = clock.trim().split(':')
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toDoubleOrNull()?.toInt() ?: 0)
            1 -> parts[0].toDoubleOrNull()?.toInt() ?: 0
            else -> 0
        }
    }

    private val JsonElement?.obj get() = this as? JsonObject
    private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}
