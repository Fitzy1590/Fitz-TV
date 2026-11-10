package com.fitztv.sports

import com.fitztv.data.Game
import com.fitztv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/** One scoring moment, ready to show: who, how much, their current numbers and whether they are hot. */
data class ScoreEvent(
    val id: String,
    /** When it should appear (play time + your stream delay). */
    val dueAt: Long,
    val points: Int,
    /** "+3", "TOUCHDOWN", "GOAL". */
    val headline: String,
    val name: String,
    val team: String,
    val teamLogo: String?,
    val teamColor: String?,
    val headshot: String?,
    /** Compact current stats: "28 PTS · 5 REB · 7 AST · 6/11 FG". */
    val line: String,
    /** On fire: shown with a flame and kept on screen a little longer. */
    val hot: Boolean,
)

/**
 * Watches a live game's play-by-play and turns each new scoring play into a [ScoreEvent].
 * NBA / basketball: scorer with running points, rebounds, assists and shooting, plus a hot flag
 * (three makes in a row, or 10+ points in about four minutes). NFL, soccer and other sports:
 * the scoring play and the score. ESPN runs ~10-20 s behind real time; [delayMs] holds each
 * event back to match your stream.
 */
object ScoreWatcher {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "FitzTV"
    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private val JsonElement?.arr: JsonArray? get() = this as? JsonArray
    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.content

    internal class Snapshot(val events: List<Raw>, val finished: Boolean)
    internal class Raw(val id: String, val atMillis: Long?, val build: () -> ScoreEvent)

    /** New scoring events as they become due. Existing ones when the watch starts are not replayed. */
    fun events(game: Game, delayMs: Long): Flow<ScoreEvent> = flow {
        if (!game.id.startsWith("espn:") || game.espnPath == null || game.sport == Sport.CRICKET) return@flow
        val seen = HashSet<String>()
        val queue = ArrayDeque<ScoreEvent>()
        var first = true
        var lastPoll = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastPoll >= 8_000) {
                lastPoll = now
                val snap = withContext(Dispatchers.IO) { runCatching { fetch(game) }.getOrNull() }
                if (snap != null) {
                    if (first) { snap.events.forEach { seen += it.id }; first = false }
                    else snap.events.filter { seen.add(it.id) }.forEach { r ->
                        val ev = r.build()
                        // Plays carry a wall-clock time; otherwise use when we noticed them.
                        queue += ev.copy(dueAt = (r.atMillis ?: now) + delayMs)
                    }
                    if (snap.finished && queue.isEmpty()) return@flow
                }
            }
            val t = System.currentTimeMillis()
            // Far behind (paused, a long delay change): keep only the latest two.
            while (queue.size > 3) queue.removeFirst()
            while (queue.isNotEmpty() && queue.first().dueAt <= t) emit(queue.removeFirst())
            delay(1_000)
        }
    }

    internal fun fetch(game: Game): Snapshot? {
        val url = "https://site.api.espn.com/apis/site/v2/sports/${game.espnPath}/summary?event=${game.id.removePrefix("espn:")}"
        val root = json.parseToJsonElement(Http.getString(url, UA)).obj ?: return null
        val comp = root["header"].obj?.get("competitions").arr?.firstOrNull()?.obj
        val finished = comp?.get("status").obj?.get("type").obj?.s("state") == "post"
        val competitors = comp?.get("competitors").arr.orEmpty().mapNotNull { it.obj }
        val teamInfo = competitors.mapNotNull { c -> c["team"].obj?.let { t -> t.s("id") to t } }.toMap()
        fun color(teamId: String?) = teamInfo[teamId]?.s("color")
        fun logo(teamId: String?) = teamInfo[teamId]?.get("logos").arr?.firstOrNull()?.obj?.s("href")
        fun abbr(teamId: String?) = teamInfo[teamId]?.s("abbreviation") ?: ""
        val events = when (game.sport) {
            Sport.NBA, Sport.NBL -> basketball(game, root, ::color, ::logo, ::abbr)
            Sport.NFL -> football(root, competitors, ::color, ::logo, ::abbr)
            Sport.SOCCER -> soccer(root, competitors, ::color, ::logo, ::abbr)
            else -> emptyList()
        }
        return Snapshot(events, finished)
    }

    // ---- basketball ----------------------------------------------------------------------

    private class Player(val name: String, val short: String, val headshot: String?, val stats: Map<String, String>)

    private fun gameSeconds(period: Int, clock: String?): Double {
        val left = clock?.let { c ->
            if (c.contains(':')) c.substringBefore(':').toDoubleOrNull()?.times(60)?.plus(c.substringAfter(':').toDoubleOrNull() ?: 0.0) else c.toDoubleOrNull()
        } ?: 0.0
        return if (period <= 4) (period - 1) * 720.0 + (720 - left) else 2880.0 + (period - 5) * 300.0 + (300 - left)
    }

    private fun basketball(
        game: Game, root: JsonObject, color: (String?) -> String?, logo: (String?) -> String?, abbr: (String?) -> String,
    ): List<Raw> {
        // Players with their current box-score numbers.
        val players = HashMap<String, Player>()
        root["boxscore"].obj?.get("players").arr.orEmpty().mapNotNull { it.obj }.forEach { t ->
            val st = t["statistics"].arr?.firstOrNull()?.obj ?: return@forEach
            val keys = st["keys"].arr.orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            st["athletes"].arr.orEmpty().mapNotNull { it.obj }.forEach { a ->
                val ath = a["athlete"].obj ?: return@forEach
                val id = ath.s("id") ?: return@forEach
                val vals = a["stats"].arr.orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty() }
                players[id] = Player(
                    ath.s("displayName").orEmpty(), ath.s("shortName") ?: ath.s("displayName").orEmpty(),
                    ath["headshot"].obj?.s("href"), keys.zip(vals).toMap(),
                )
            }
        }
        val plays = root["plays"].arr.orEmpty().mapNotNull { it.obj }
        fun athlete(p: JsonObject) = p["participants"].arr?.firstOrNull()?.obj?.get("athlete").obj?.s("id")
        fun seconds(p: JsonObject) = gameSeconds(p["period"].obj?.get("number")?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 1, p["clock"].obj?.s("displayValue"))
        val isFt = { p: JsonObject -> p.s("text")?.contains("free throw", true) == true }
        val scoring = plays.filter { (it["scoringPlay"] as? JsonPrimitive)?.content == "true" && athlete(it) != null }
        return scoring.map { p ->
            val id = p.s("id") ?: p.s("sequenceNumber").orEmpty()
            val at = p.s("wallclock")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            Raw(id, at) {
                val aid = athlete(p)!!
                val pl = players[aid]
                val pts = p.s("scoreValue")?.toIntOrNull() ?: 0
                val tid = p["team"].obj?.s("id")
                // Hot: last three field-goal tries all made, or 10+ points in the last four minutes.
                val mine = plays.filter { athlete(it) == aid && (it["shootingPlay"] as? JsonPrimitive)?.content == "true" && !isFt(it) && it.s("sequenceNumber")?.toLongOrNull()?.let { s -> s <= (p.s("sequenceNumber")?.toLongOrNull() ?: Long.MAX_VALUE) } == true }
                val streak = mine.takeLast(3).let { l -> l.size == 3 && l.all { (it["scoringPlay"] as? JsonPrimitive)?.content == "true" } }
                val t0 = seconds(p)
                val recent = scoring.filter { athlete(it) == aid && seconds(it) in (t0 - 240)..t0 }.sumOf { it.s("scoreValue")?.toIntOrNull() ?: 0 }
                val stats = pl?.stats.orEmpty()
                val line = listOfNotNull(
                    stats["points"]?.let { "$it PTS" }, stats["rebounds"]?.let { "$it REB" }, stats["assists"]?.let { "$it AST" },
                    stats["fieldGoalsMade-fieldGoalsAttempted"]?.let { "$it FG" },
                ).joinToString(" · ")
                ScoreEvent(id, 0, pts, "+$pts", pl?.short ?: p.s("text")?.substringBefore(" makes").orEmpty(), abbr(tid), logo(tid), color(tid), pl?.headshot, line, streak || recent >= 10)
            }
        }
    }

    // ---- NFL -----------------------------------------------------------------------------

    private fun football(
        root: JsonObject, competitors: List<JsonObject>, color: (String?) -> String?, logo: (String?) -> String?, abbr: (String?) -> String,
    ): List<Raw> = root["scoringPlays"].arr.orEmpty().mapNotNull { it.obj }.map { p ->
        val id = p.s("id").orEmpty()
        Raw(id, null) {
            val type = p["type"].obj?.s("abbreviation").orEmpty()
            val (head, pts) = when (type) { "TD" -> "TOUCHDOWN" to 6; "FG" -> "FIELD GOAL" to 3; "SF" -> "SAFETY" to 2; else -> (p["type"].obj?.s("text")?.uppercase() ?: "SCORE") to 0 }
            val text = p.s("text").orEmpty()
            val name = Regex("""^(\D+?)\s+\d""").find(text)?.groupValues?.get(1)?.trim() ?: text.substringBefore(" (").trim()
            val detail = text.substringAfter(name, "").substringBefore("(").trim()
            val tid = p["team"].obj?.s("id")
            val away = competitors.firstOrNull { it.s("homeAway") == "away" }?.get("team").obj?.s("abbreviation").orEmpty()
            val home = competitors.firstOrNull { it.s("homeAway") == "home" }?.get("team").obj?.s("abbreviation").orEmpty()
            ScoreEvent(id, 0, pts, head, name, abbr(tid), logo(tid) ?: p["team"].obj?.s("logo"), color(tid), null,
                listOf(detail, "$away ${p.s("awayScore")} – ${p.s("homeScore")} $home").filter { it.isNotBlank() }.joinToString(" · "), false)
        }
    }

    // ---- soccer --------------------------------------------------------------------------

    private fun soccer(
        root: JsonObject, competitors: List<JsonObject>, color: (String?) -> String?, logo: (String?) -> String?, abbr: (String?) -> String,
    ): List<Raw> {
        val goals = root["keyEvents"].arr.orEmpty().mapNotNull { it.obj }.filter { (it["scoringPlay"] as? JsonPrimitive)?.content == "true" }
        val tally = HashMap<String, Int>()
        return goals.map { g ->
            val id = g.s("id").orEmpty()
            val who = g["participants"].arr?.firstOrNull()?.obj?.get("athlete").obj
            val name = who?.s("displayName") ?: g.s("shortText")?.removeSuffix(" Goal").orEmpty()
            val nth = (tally[name] ?: 0) + 1; tally[name] = nth
            Raw(id, null) {
                val tid = g["team"].obj?.s("id")
                val minute = g["clock"].obj?.s("displayValue").orEmpty()
                val own = g["type"].obj?.s("text")?.contains("own", true) == true
                val ord = when (nth) { 1 -> ""; 2 -> " · 2nd goal"; 3 -> " · hat-trick"; else -> " · ${nth}th goal" }
                ScoreEvent(id, 0, 1, if (own) "OWN GOAL" else "GOAL", name, abbr(tid), logo(tid), color(tid), null, listOf(minute, ord.removePrefix(" · ")).filter { it.isNotBlank() }.joinToString(" · "), nth >= 2)
            }
        }
    }
}
