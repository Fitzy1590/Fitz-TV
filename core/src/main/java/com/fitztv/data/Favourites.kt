package com.fitztv.data

/**
 * Favourite names that differ from how fixtures name the team. "Socceroos" is "Australia" in
 * soccer only (so it never picks up Australian cricket or basketball games).
 */
object TeamAliases {
    data class Alias(val team: String, val sport: com.fitztv.sports.Sport, val tsdbSport: String)
    val map = mapOf(
        "socceroos" to Alias("Australia", com.fitztv.sports.Sport.SOCCER, "Soccer"),
        "matildas" to Alias("Australia Women", com.fitztv.sports.Sport.SOCCER, "Soccer"),
        "boomers" to Alias("Australia", com.fitztv.sports.Sport.NBA, "Basketball"),
    )
    fun of(fav: String) = map[com.fitztv.sports.Text.norm(fav).trim()]
}

private val womenSuffix = Regex("""\s+(women|womens|w|aflw|ladies)$""", RegexOption.IGNORE_CASE)

/** Does favourite [fav] refer to [teamName] in a game of [sport]? ("Port Adelaide" also covers its AFLW side.) */
fun isTeam(fav: String, teamName: String, sport: com.fitztv.sports.Sport): Boolean {
    if (teamName.isBlank()) return false
    TeamAliases.of(fav)?.let { a -> return a.sport == sport && sameTeam(a.team, teamName) }
    if (TeamAliases.map.values.any { sameTeam(it.team, teamName) } && sameTeam(fav, teamName).not()) return false
    return sameTeam(fav, teamName) || sameTeam(fav, teamName.replace(womenSuffix, ""))
}

data class FavouriteEntry(val team: String, val logo: String?, val game: Game?)

/** For each favourite team: its live game, else next game, else most recent result. */
fun favouriteEntries(games: List<Game>, settings: AppSettings, now: Long, logos: LogoStore? = null): List<FavouriteEntry> =
    settings.favoriteTeams.sorted().map { team ->
        val mine = games.filter { isTeam(team, it.home, it.sport) || isTeam(team, it.away, it.sport) }
        val g = mine.firstOrNull { it.gameState == GameState.LIVE }
            ?: mine.filter { it.gameState == GameState.PRE && it.startUtcMillis > now - 3_600_000 }.minByOrNull { it.startUtcMillis }
            ?: mine.filter { it.gameState == GameState.FINAL }.maxByOrNull { it.startUtcMillis }
        val logo = logos?.get(team) ?: mine.firstNotNullOfOrNull { if (isTeam(team, it.home, it.sport)) it.homeLogo else it.awayLogo }
        FavouriteEntry(team, logo, g)
    }.sortedBy { e ->
        when (e.game?.gameState) { GameState.LIVE -> 0; GameState.PRE -> 1; GameState.FINAL -> 2; null -> 3 }
    }

