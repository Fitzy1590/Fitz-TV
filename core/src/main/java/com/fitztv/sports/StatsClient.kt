package com.fitztv.sports

import com.fitztv.data.Game
import com.fitztv.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class StatRow(val label: String, val away: String, val home: String)

/** One player's basketball line (FG split into made / missed). */
data class PlayerLine(
    val name: String, val starter: Boolean, val min: String,
    val pts: Int, val reb: Int, val ast: Int, val stl: Int, val blk: Int,
    val fgm: Int, val fgMissed: Int, val plusMinus: String,
)

data class TeamBox(val team: String, val players: List<PlayerLine>)

data class LineupPlayer(val name: String, val jersey: String, val position: String, val place: Int)

data class Lineup(val team: String, val formation: String?, val starters: List<LineupPlayer>, val subs: List<LineupPlayer>) {
    /** Starters arranged from goalkeeper to attack, following the formation (e.g. 4-2-3-1). */
    fun lines(): List<List<LineupPlayer>> {
        val sorted = starters.sortedBy { it.place }
        val gk = sorted.firstOrNull { it.place == 1 || it.position.uppercase() in setOf("G", "GK") } ?: sorted.firstOrNull() ?: return emptyList()
        val outfield = sorted.filter { it !== gk }
        val counts = formation?.split('-', ' ')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        val lines = mutableListOf(listOf(gk))
        if (counts.isNotEmpty() && counts.sum() == outfield.size) {
            var i = 0
            counts.forEach { n -> lines += outfield.subList(i, i + n); i += n }
        } else {
            // No usable formation: group by position letter (D / M / F).
            fun group(p: LineupPlayer) = when (p.position.uppercase().firstOrNull()) { 'D', 'C', 'L', 'R' -> 0; 'M' -> 1; else -> 2 }
            outfield.groupBy(::group).toSortedMap().values.forEach { lines += it }
        }
        return lines
    }
}

data class LiveStats(
    val status: String,
    val live: Boolean,
    val awayName: String,
    val homeName: String,
    val awayScore: String,
    val homeScore: String,
    val awayLogo: String?,
    val homeLogo: String?,
    /** Score by period: header labels plus away/home values. */
    val periods: List<String> = emptyList(),
    val awayPeriods: List<String> = emptyList(),
    val homePeriods: List<String> = emptyList(),
    val rows: List<StatRow> = emptyList(),
    val leaders: List<String> = emptyList(),
    val plays: List<String> = emptyList(),
    /** Basketball player box scores, each team sorted by points. */
    val teamBoxes: List<TeamBox> = emptyList(),
    /** Soccer line-ups and formations (away first, then home). */
    val lineups: List<Lineup> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Live box score for the game on screen. Only the game id is sent to the public feed. */
object StatsClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "FitzTV"

    fun fetch(game: Game): LiveStats? = runCatching {
        when {
            game.id.startsWith("espn:") && game.espnPath != null -> espn(game)
            game.id.startsWith("tsdb:") -> sportsDb(game)
            else -> null
        }
    }.getOrNull()

    private fun espn(game: Game): LiveStats? {
        val id = game.id.removePrefix("espn:")
        val url = "https://site.api.espn.com/apis/site/v2/sports/${game.espnPath}/summary?event=$id"
        val root = json.parseToJsonElement(Http.getString(url, UA)).obj ?: return null

        val comp = root["header"].obj?.get("competitions").arr?.firstOrNull()?.obj
        if (game.sport == Sport.CRICKET) return cricket(game, root, comp)
        val status = comp?.get("status").obj?.get("type").obj
        val competitors = comp?.get("competitors").arr.orEmpty().mapNotNull { it.obj }
        val away = competitors.firstOrNull { it.s("homeAway") == "away" } ?: competitors.getOrNull(1)
        val home = competitors.firstOrNull { it.s("homeAway") == "home" } ?: competitors.getOrNull(0)
        fun name(c: JsonObject?) = c?.get("team").obj?.let { it.s("shortDisplayName") ?: it.s("displayName") } ?: ""
        fun logo(c: JsonObject?) = c?.get("team").obj?.get("logos").arr?.firstOrNull()?.obj?.s("href")
        fun lines(c: JsonObject?) = c?.get("linescores").arr.orEmpty().mapNotNull { it.obj?.s("displayValue") ?: it.obj?.s("value") }

        val awayLines = lines(away)
        val homeLines = lines(home)
        val periodCount = maxOf(awayLines.size, homeLines.size)
        val periodLabels = (1..periodCount).map { i ->
            when {
                game.sport == Sport.SOCCER -> if (i == 1) "1H" else if (i == 2) "2H" else "ET$i"
                i <= 4 -> "Q$i"
                else -> "OT${if (i == 5) "" else i - 4}"
            }
        }

        // Team box score: align the two teams' stat lists by stat name.
        val teams = root["boxscore"].obj?.get("teams").arr.orEmpty().mapNotNull { it.obj }
        val awayAbbr = away?.get("team").obj?.s("abbreviation")
        val boxAway = teams.firstOrNull { it.s("homeAway") == "away" || it["team"].obj?.s("abbreviation") == awayAbbr } ?: teams.getOrNull(0)
        val boxHome = teams.firstOrNull { it !== boxAway }
        fun statMap(t: JsonObject?) = t?.get("statistics").arr.orEmpty().mapNotNull { it.obj }
            .mapNotNull { st -> (st.s("name") ?: st.s("label") ?: return@mapNotNull null) to st }
        val awayStats = statMap(boxAway)
        val homeStats = statMap(boxHome).toMap()
        val rows = awayStats.mapNotNull { (key, st) ->
            val h = homeStats[key] ?: return@mapNotNull null
            val label = st.s("label") ?: st.s("abbreviation") ?: key
            val av = st.s("displayValue") ?: return@mapNotNull null
            val hv = h.s("displayValue") ?: return@mapNotNull null
            StatRow(label, av, hv)
        }.filter { it.label.length <= 22 }.take(12)

        // Top performers per team.
        val leaders = root["leaders"].arr.orEmpty().mapNotNull { it.obj }.flatMap { team ->
            val abbr = team["team"].obj?.s("abbreviation").orEmpty()
            team["leaders"].arr.orEmpty().mapNotNull { it.obj }.take(3).mapNotNull { cat ->
                val top = cat["leaders"].arr?.firstOrNull()?.obj ?: return@mapNotNull null
                val who = top["athlete"].obj?.let { it.s("shortName") ?: it.s("displayName") } ?: return@mapNotNull null
                "$abbr  ${cat.s("abbreviation") ?: cat.s("displayName") ?: ""}: $who ${top.s("displayValue") ?: ""}".trim()
            }
        }.take(6)

        // Latest plays (NBA "plays", NFL current drive, soccer key events, otherwise scoring plays).
        val playSources = listOfNotNull(
            root["plays"].arr,
            root["drives"].obj?.get("current").obj?.get("plays").arr,
            root["keyEvents"].arr,
            root["scoringPlays"].arr,
        )
        val plays = playSources.firstOrNull { it.isNotEmpty() }.orEmpty()
            .mapNotNull { it.obj?.s("text") ?: it.obj?.get("type").obj?.s("text") }
            .takeLast(4).reversed()

        return LiveStats(
            teamBoxes = if (game.sport == Sport.NBA || game.sport == Sport.NBL) playerBoxes(root) else emptyList(),
            lineups = if (game.sport == Sport.SOCCER) lineups(root) else emptyList(),
            status = status?.s("shortDetail") ?: status?.s("detail") ?: game.statusText,
            live = status?.s("state") == "in",
            awayName = name(away).ifBlank { game.awayShort },
            homeName = name(home).ifBlank { game.homeShort },
            awayScore = away?.s("score") ?: game.awayScore.orEmpty(),
            homeScore = home?.s("score") ?: game.homeScore.orEmpty(),
            awayLogo = logo(away) ?: game.awayLogo,
            homeLogo = logo(home) ?: game.homeLogo,
            periods = periodLabels,
            awayPeriods = awayLines,
            homePeriods = homeLines,
            rows = rows,
            leaders = leaders,
            plays = plays,
        )
    }

    // ---- Cricket (ESPN / Cricinfo) ---------------------------------------------------------
    //
    // The summary's scorecard ("matchcards") lags during play, so the panel is built from the
    // ball-by-ball feed (batters at the crease, bowlers, run rates, the last balls), the innings
    // totals on each team, the match situation ("Heat require 8 runs") and top performers.

    private fun cricket(game: Game, root: JsonObject, comp: JsonObject?): LiveStats {
        val statusObj = comp?.get("status").obj
        val type = statusObj?.get("type").obj
        val competitors = comp?.get("competitors").arr.orEmpty().mapNotNull { it.obj }
        val away = competitors.firstOrNull { it.s("homeAway") == "away" } ?: competitors.getOrNull(1)
        val home = competitors.firstOrNull { it.s("homeAway") == "home" } ?: competitors.getOrNull(0)
        fun name(c: JsonObject?) = c?.get("team").obj?.let { it.s("shortDisplayName") ?: it.s("displayName") }.orEmpty()
            .removeSuffix(" Women").trim()
        fun logo(c: JsonObject?) = c?.get("team").obj?.get("logos").arr?.firstOrNull()?.obj?.s("href")
        fun score(c: JsonObject?) = c?.s("score")?.substringBefore(" (")?.trim().orEmpty()

        // Innings the team batted: runs, wickets, overs.
        data class Inn(val runs: Int, val wkts: Int, val overs: Double, val target: Int)
        fun innings(c: JsonObject?) = c?.get("linescores").arr.orEmpty().mapNotNull { it.obj }
            .filter { it.s("isBatting") == "true" }
            .map { Inn(it.s("runs")?.toIntOrNull() ?: 0, it.s("wickets")?.toIntOrNull() ?: 0, it.s("overs")?.toDoubleOrNull() ?: 0.0, it.s("target")?.toIntOrNull() ?: 0) }
        fun balls(overs: Double): Int { val o = overs.toInt(); return o * 6 + Math.round((overs - o) * 10).toInt() }
        fun oversText(overs: Double) = balls(overs).let { b -> "${b / 6}.${b % 6}" }
        fun rr(i: Inn?) = i?.let { b -> balls(b.overs).takeIf { it > 0 }?.let { "%.2f".format(b.runs * 6.0 / it) } } ?: "–"
        val aInn = innings(away).lastOrNull()
        val hInn = innings(home).lastOrNull()
        val rows = buildList {
            if (aInn != null || hInn != null) {
                add(StatRow("Overs", aInn?.let { oversText(it.overs) } ?: "–", hInn?.let { oversText(it.overs) } ?: "–"))
                add(StatRow("Run rate", rr(aInn), rr(hInn)))
            }
        }

        // Ball by ball, newest last.
        val balls = (comp?.get("commentaries") as? JsonObject)?.values.orEmpty().mapNotNull { it.obj }
            .sortedBy { it.s("sequence")?.toLongOrNull() ?: 0L }
        val last = balls.lastOrNull()
        fun who(o: JsonObject?) = o?.get("athlete").obj?.let { it.s("lastName") ?: it.s("displayName") }
        fun bat(o: JsonObject?) = who(o)?.let { "$it ${o!!.s("totalRuns") ?: "0"} (${o.s("faced") ?: "0"})" }
        fun bowl(o: JsonObject?) = who(o)?.let { n ->
            val b = o!!.s("balls")?.toIntOrNull() ?: 0
            "$n ${o.s("wickets") ?: "0"}/${o.s("conceded") ?: "0"} (${b / 6}${if (b % 6 > 0) ".${b % 6}" else ""})"
        }
        val inn = last?.get("innings").obj
        val chase = inn?.s("target")?.toIntOrNull()?.takeIf { it > 0 }
        val reqRate = inn?.s("requiredRunRate")?.toDoubleOrNull()?.takeIf { chase != null && it > 0 }
        val rowsAll = if (reqRate != null) rows + StatRow("Required rate",
            if ((aInn?.target ?: 0) > 0) "%.2f".format(reqRate) else "–", if ((hInn?.target ?: 0) > 0) "%.2f".format(reqRate) else "–") else rows

        val leaders = buildList {
            if (type?.s("state") == "in" && last != null) {
                listOfNotNull(bat(last["batsman"].obj), bat(last["otherBatsman"].obj)).takeIf { it.isNotEmpty() }
                    ?.let { add("Batting  " + it.joinToString("  ·  ")) }
                listOfNotNull(bowl(last["bowler"].obj), bowl(last["otherBowler"].obj)).takeIf { it.isNotEmpty() }
                    ?.let { add("Bowling  " + it.joinToString("  ·  ")) }
            }
            // Top run scorer / wicket taker of each innings.
            root["leaders"].arr.orEmpty().mapNotNull { it.obj }.forEach { team ->
                val abbr = team["team"].obj?.s("abbreviation").orEmpty().removeSuffix("-W")
                team["linescores"].arr.orEmpty().mapNotNull { it.obj }.forEach { ls ->
                    ls["leaders"].arr.orEmpty().mapNotNull { it.obj }.forEach { cat ->
                        val top = cat["leaders"].arr.orEmpty().mapNotNull { it.obj }.take(2).mapNotNull { l ->
                            l["athlete"].obj?.let { it.s("lastName") ?: it.s("displayName") }?.let { n -> "$n ${l.s("displayValue").orEmpty()}" }
                        }
                        if (top.isNotEmpty()) add("$abbr  ${if (cat.s("name") == "wickets") "Wkts" else "Runs"}: ${top.joinToString(", ")}")
                    }
                }
            }
        }.take(8)

        val plays = balls.takeLast(4).reversed().mapNotNull { b ->
            val text = b.s("shortText") ?: return@mapNotNull null
            val over = b["over"].obj?.s("actual") ?: b["over"].obj?.s("overs")
            if (over != null) "$over  $text" else text
        }.ifEmpty {
            root["notes"].arr.orEmpty().mapNotNull { it.obj }.filter { it.s("type") == "toss" || it.s("type") == "matchdays" }
                .mapNotNull { it.s("text") }.take(3)
        }

        val summary = statusObj?.s("summary")
        return LiveStats(
            status = listOfNotNull(type?.s("shortDetail")?.takeIf { it != "Live" || summary == null }, summary).joinToString(" · ").ifBlank { game.statusText },
            live = type?.s("state") == "in",
            awayName = name(away).ifBlank { game.awayShort },
            homeName = name(home).ifBlank { game.homeShort },
            awayScore = score(away).ifBlank { game.awayScore.orEmpty() },
            homeScore = score(home).ifBlank { game.homeScore.orEmpty() },
            awayLogo = logo(away) ?: game.awayLogo,
            homeLogo = logo(home) ?: game.homeLogo,
            rows = rowsAll,
            leaders = leaders,
            plays = plays,
        )
    }

    private fun playerBoxes(root: JsonObject): List<TeamBox> =
        root["boxscore"].obj?.get("players").arr.orEmpty().mapNotNull { it.obj }.mapNotNull { t ->
            val team = t["team"].obj?.let { it.s("abbreviation") ?: it.s("shortDisplayName") } ?: return@mapNotNull null
            val stat = t["statistics"].arr?.firstOrNull()?.obj ?: return@mapNotNull null
            val keys = stat["keys"].arr.orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty() }
            val labels = stat["labels"].arr.orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty().uppercase() }
            fun idx(key: String, label: String) = keys.indexOf(key).takeIf { it >= 0 } ?: labels.indexOf(label)
            val iMin = idx("minutes", "MIN"); val iPts = idx("points", "PTS"); val iReb = idx("rebounds", "REB")
            val iAst = idx("assists", "AST"); val iStl = idx("steals", "STL"); val iBlk = idx("blocks", "BLK")
            val iFg = idx("fieldGoalsMade-fieldGoalsAttempted", "FG"); val iPm = idx("plusMinus", "+/-")
            val players = stat["athletes"].arr.orEmpty().mapNotNull { it.obj }.mapNotNull { a ->
                val vals = a["stats"].arr.orEmpty().map { (it as? JsonPrimitive)?.content.orEmpty() }
                if (vals.isEmpty()) return@mapNotNull null   // did not play
                fun n(i: Int) = vals.getOrNull(i)?.toIntOrNull() ?: 0
                val fg = vals.getOrNull(iFg)?.split('-')
                val made = fg?.getOrNull(0)?.toIntOrNull() ?: 0
                val att = fg?.getOrNull(1)?.toIntOrNull() ?: made
                PlayerLine(
                    name = a["athlete"].obj?.let { it.s("shortName") ?: it.s("displayName") } ?: return@mapNotNull null,
                    starter = (a["starter"] as? JsonPrimitive)?.content == "true",
                    min = vals.getOrNull(iMin).orEmpty(),
                    pts = n(iPts), reb = n(iReb), ast = n(iAst), stl = n(iStl), blk = n(iBlk),
                    fgm = made, fgMissed = (att - made).coerceAtLeast(0), plusMinus = vals.getOrNull(iPm).orEmpty(),
                )
            }.sortedWith(compareByDescending<PlayerLine> { it.pts }.thenByDescending { it.reb + it.ast })
            TeamBox(team, players)
        }

    private fun lineups(root: JsonObject): List<Lineup> {
        val rosters = root["rosters"].arr.orEmpty().mapNotNull { it.obj }
        val ordered = rosters.sortedBy { if (it.s("homeAway") == "away") 0 else 1 }
        return ordered.mapNotNull { r ->
            val team = r["team"].obj?.let { it.s("shortDisplayName") ?: it.s("displayName") } ?: return@mapNotNull null
            val players = r["roster"].arr.orEmpty().mapNotNull { it.obj }.map { p ->
                val ath = p["athlete"].obj
                val starter = (p["starter"] as? JsonPrimitive)?.content == "true"
                starter to LineupPlayer(
                    name = ath?.let { it.s("lastName") ?: it.s("shortName") ?: it.s("displayName") }.orEmpty(),
                    jersey = p.s("jersey").orEmpty(),
                    position = p["position"].obj?.s("abbreviation").orEmpty(),
                    place = p.s("formationPlace")?.toIntOrNull() ?: 99,
                )
            }
            if (players.isEmpty()) return@mapNotNull null
            Lineup(
                team = team,
                formation = r.s("formation") ?: r["formation"].obj?.s("name"),
                starters = players.filter { it.first }.map { it.second },
                subs = players.filterNot { it.first }.map { it.second },
            )
        }
    }

    private fun sportsDb(game: Game): LiveStats? {
        val id = game.id.removePrefix("tsdb:")
        Http.throttleSportsDb()
        val root = json.parseToJsonElement(
            Http.getString("https://www.thesportsdb.com/api/v1/json/123/lookupevent.php?id=$id", UA)
        ).obj ?: return null
        val e = root["events"].arr?.firstOrNull()?.obj ?: return null
        val st = e.s("strStatus") ?: game.statusText
        return LiveStats(
            status = st,
            live = game.gameState == com.fitztv.data.GameState.LIVE,
            awayName = game.awayShort, homeName = game.homeShort,
            awayScore = e.s("intAwayScore") ?: e.s("strAwayScore") ?: "",
            homeScore = e.s("intHomeScore") ?: e.s("strHomeScore") ?: "",
            awayLogo = game.awayLogo, homeLogo = game.homeLogo,
            rows = listOfNotNull(e.s("strResult")?.let { StatRow("Summary", it, "") }),
        )
    }

    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private val JsonElement?.arr: JsonArray? get() = this as? JsonArray
    private fun JsonObject.s(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it.isNotBlank() }
}
