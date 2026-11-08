package com.fitztv.sports

import com.fitztv.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** League ladder from ESPN (`apis/v2/.../standings`): one table per conference / group. */
object StandingsClient {
    data class Row(val team: String, val logo: String?, val values: List<String>)
    data class Table(val name: String, val headers: List<String>, val rows: List<Row>)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Columns shown per sport (ESPN stat abbreviations), in order; missing ones are skipped. */
    private fun columns(sport: Sport): List<String> = when (sport) {
        Sport.SOCCER -> listOf("GP", "W", "D", "L", "GD", "P")
        Sport.AFL -> listOf("GP", "W", "L", "D", "PCT", "PTS")
        Sport.NBA, Sport.NBL -> listOf("W", "L", "PCT", "GB", "STRK")
        Sport.NFL -> listOf("W", "L", "T", "PCT", "DIFF")
        else -> listOf("GP", "W", "L", "PCT", "PTS")
    }

    fun fetch(league: League): List<Table> {
        val path = league.espnPath ?: return emptyList()
        val root = json.parseToJsonElement(Http.getString("https://site.api.espn.com/apis/v2/sports/$path/standings", "FitzTV")).obj ?: return emptyList()
        val groups = (root["children"] as? JsonArray)?.mapNotNull { it.obj }?.takeIf { it.isNotEmpty() } ?: listOf(root)
        val wanted = columns(league.sport)
        return groups.mapNotNull { g ->
            val entries = (g["standings"].obj?.get("entries") as? JsonArray).orEmpty().mapNotNull { it.obj }
            if (entries.isEmpty()) return@mapNotNull null
            data class E(val team: String, val logo: String?, val stats: Map<String, String>, val byName: Map<String, Double>)
            val parsed = entries.map { e ->
                val t = e["team"].obj
                val stats = (e["stats"] as? JsonArray).orEmpty().mapNotNull { it.obj }
                E(
                    team = t?.s("shortDisplayName") ?: t?.s("displayName").orEmpty(),
                    logo = (t?.get("logos") as? JsonArray)?.firstOrNull()?.obj?.s("href"),
                    stats = stats.mapNotNull { st -> (st.s("abbreviation") ?: return@mapNotNull null) to (st.s("displayValue") ?: "") }.toMap(),
                    byName = stats.mapNotNull { st -> (st.s("name") ?: return@mapNotNull null) to (st.s("value")?.toDoubleOrNull() ?: return@mapNotNull null) }.toMap(),
                )
            }
            // ESPN doesn't return entries in ladder order: points, then win %, then wins.
            val sorted = parsed.sortedWith(
                compareByDescending<E> { it.byName["points"] ?: Double.NEGATIVE_INFINITY }
                    .thenByDescending { it.byName["pointDifferential"] ?: it.byName["differential"] ?: 0.0 }
                    .thenByDescending { it.byName["winPercent"] ?: 0.0 }
                    .thenByDescending { it.byName["wins"] ?: 0.0 }
            )
            val headers = wanted.filter { h -> sorted.any { it.stats.containsKey(h) } }
            Table(g.s("name") ?: league.name, headers, sorted.map { e -> Row(e.team, e.logo, headers.map { e.stats[it] ?: "" }) })
        }
    }

    private val JsonElement?.obj: JsonObject? get() = this as? JsonObject
    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
}
