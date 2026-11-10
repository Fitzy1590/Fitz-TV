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

    /** Short forms channel names use. Weak keys: only trusted when BOTH teams appear. */
    private val shortForms = mapOf(
        "mavericks" to listOf("mavs"), "timberwolves" to listOf("wolves", "t wolves"), "76ers" to listOf("sixers"),
        "cavaliers" to listOf("cavs"), "trail blazers" to listOf("blazers"), "pelicans" to listOf("pels"),
        "grizzlies" to listOf("grizz"), "buccaneers" to listOf("bucs"), "49ers" to listOf("niners"),
        "patriots" to listOf("pats"), "jaguars" to listOf("jags"), "commanders" to listOf("washington"),
        "manchester united" to listOf("man utd", "man united"), "manchester city" to listOf("man city"),
        "tottenham hotspur" to listOf("spurs", "tottenham"), "wolverhampton wanderers" to listOf("wolves"),
        "brighton hove albion" to listOf("brighton"), "nottingham forest" to listOf("forest", "notts forest"),
        "newcastle united" to listOf("newcastle"), "west ham united" to listOf("west ham"),
        "western sydney wanderers" to listOf("wsw", "western sydney"), "port adelaide" to listOf("port", "power"),
    )

    /**
     * Weak keys: the city ("brooklyn", "golden state"), the feed's abbreviation ("bkn") and short
     * forms ("mavs"). Event channels often use these ("BKN @ DAL", "Brooklyn vs Dallas"); on their
     * own they're ambiguous, so they only count when the other team appears too.
     */
    fun weak(full: String, short: String, abbr: String?, sport: Sport? = null): List<String> {
        if (full.isBlank()) return emptyList()
        val base = full.replace(suffix, "").trim().ifBlank { full }
        val words = base.split(' ').filter { it.isNotBlank() }
        val out = linkedSetOf<String>()
        if (words.size >= 2) words.dropLast(1).joinToString(" ").takeIf { it.length >= 4 }?.let { out += it }
        abbr?.takeIf { it.length in 2..4 }?.let { out += it }
        val n = Text.norm(base)
        shortForms.forEach { (k, v) -> if (n.contains(" $k ")) out += v }
        short.takeIf { it.isNotBlank() && !it.equals(full, true) }?.let { out += it }
        return out.map { Text.norm(it) }.filter { it.trim().length >= 2 && it.trim() !in generic }.distinct()
    }

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
