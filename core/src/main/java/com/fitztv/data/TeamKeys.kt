package com.fitztv.data

import com.fitztv.sports.Sport
import com.fitztv.sports.Text

/** Normalised names that identify a team in channel names and TV guide listings. */
object TeamKeys {
    private val generic = setOf(
        "united", "city", "fc", "the", "club", "real", "athletic", "sporting", "state", "women", "men",
        "cricket", "basketball", "football", "rugby", "hockey", "soccer", "national", "team",
    )
    /** "India Cricket", "Adelaide Strikers Women", "Australia National Team" → the team's own name. */
    private val suffix = Regex("""(\s+(women|womens|women's|w|cricket|basketball|football|rugby|national team|national|nt|xi))+$""", RegexOption.IGNORE_CASE)

    /** Nicknames national teams go by in TV listings. */
    private val nicknames = mapOf(
        (Sport.CRICKET to "new zealand") to listOf("blackcaps", "black caps", "white ferns"),
        (Sport.CRICKET to "south africa") to listOf("proteas"),
        (Sport.CRICKET to "west indies") to listOf("windies"),
        (Sport.SOCCER to "australia") to listOf("socceroos", "matildas"),
        (Sport.SOCCER to "new zealand") to listOf("all whites"),
        (Sport.NBA to "australia") to listOf("boomers", "opals"),
        (Sport.NBL to "australia") to listOf("boomers", "opals"),
        (Sport.NBA to "new zealand") to listOf("tall blacks"),
        (Sport.NRL to "australia") to listOf("kangaroos"),
        (Sport.NRL to "new zealand") to listOf("kiwis"),
    )

    /** Full name, short name, nickname ("Lakers") and the "City X" form guides use ("Melbourne.R"). */
    fun of(full: String, short: String = "", sport: Sport? = null): List<String> {
        if (full.isBlank()) return emptyList()
        val base = full.replace(suffix, "").trim().ifBlank { full }
        val keys = linkedSetOf(full, base, short, short.replace(suffix, "").trim())
        if (sport != null) nicknames[sport to base.lowercase()]?.let { keys += it }
        val words = base.split(' ').filter { it.isNotBlank() }
        words.lastOrNull()?.takeIf { it.length >= 4 }?.let { keys += it }
        if (words.size >= 2 && words.first().length >= 4) keys += words.first() + " " + words.last().first()
        return keys.filter { it.isNotBlank() }.map { Text.norm(it) }
            .filter { it.trim().length >= 3 && it.trim() !in generic }.distinct()
    }

    fun mentioned(normText: String, keys: List<String>) = keys.any { normText.contains(it) }

    /** Common guide abbreviations ("Melbourne.R") expanded to the team's name. */
    private val abbreviations = mapOf(
        "melbourne r" to "Melbourne Renegades", "melbourne s" to "Melbourne Stars", "adelaide s" to "Adelaide Strikers",
        "sydney s" to "Sydney Sixers", "sydney t" to "Sydney Thunder", "brisbane h" to "Brisbane Heat",
        "hobart h" to "Hobart Hurricanes", "perth s" to "Perth Scorchers",
    )

    fun expand(name: String): String {
        val n = Text.norm(name).trim()
        return abbreviations[n] ?: name.replace('.', ' ').replace(Regex("\\s+"), " ").trim()
    }
}
