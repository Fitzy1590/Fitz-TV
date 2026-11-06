package com.fitztv.sports

import kotlinx.serialization.Serializable

/**
 * Sports the app knows about. [keywords] are matched (as whole words) against channel
 * names and playlist group titles to decide which sport a channel belongs to.
 */
@Serializable
enum class Sport(val label: String, val emoji: String, val keywords: List<String>) {
    NBA("NBA", "🏀", listOf("nba", "basketball", "wnba", "ncaab", "league pass", "euroleague")),
    NBL("NBL", "🏀", listOf("nbl", "nbl1", "aussie hoops", "australian basketball")),
    NFL("NFL", "🏈", listOf("nfl", "redzone", "red zone", "ncaaf", "college football", "nfl network", "sunday ticket")),
    SOCCER("Soccer", "⚽", listOf(
        "soccer", "football", "premier league", "epl", "la liga", "laliga", "serie a", "bundesliga",
        "ligue 1", "champions league", "europa", "mls", "a league", "a-league", "fa cup", "efl",
        "uefa", "fifa", "copa", "eredivisie", "liga mx", "spfl", "bein", "dazn", "optus", "premier sports",
    )),
    CRICKET("BBL / Cricket", "🏏", listOf("cricket", "willow", "ipl", "big bash", "bbl", "wbbl", "test match", "t20", "odi", "star sports", "sky sports cricket", "fox cricket")),
    AFL("AFL", "🏉", listOf("afl", "aussie rules", "footy", "fox footy", "kayo")),
    NRL("Rugby", "🏉", listOf("nrl", "rugby", "super rugby", "six nations", "state of origin", "fox league")),
    NHL("NHL", "🏒", listOf("nhl", "hockey", "center ice")),
    MLB("MLB", "⚾", listOf("mlb", "baseball", "extra innings")),
    FIGHT("UFC / Boxing", "🥊", listOf("ufc", "mma", "boxing", "ppv", "fight", "wwe", "bellator", "pfl")),
    MOTOR("Motorsport", "🏎", listOf("f1", "formula 1", "formula one", "motogp", "nascar", "indycar", "supercars", "motorsport")),
    TENNIS("Tennis", "🎾", listOf("tennis", "atp", "wta", "wimbledon", "us open", "australian open", "roland garros")),
    GOLF("Golf", "⛳", listOf("golf", "pga", "liv golf", "masters")),

    /** Sports channel that doesn't name a specific sport (ESPN, Sky Sports Main Event, ...). */
    GENERAL("Multi-sport", "🏆", listOf(
        "sport", "sports", "espn", "fox sports", "sky sports", "tnt sports", "bt sport", "supersport",
        "tsn", "sportsnet", "eurosport", "arena sport", "sport tv", "movistar deportes", "deportes",
        "nbcsn", "cbs sports", "abc sports", "peacock", "paramount+", "events", "event", "flosports", "stan sport",
    ));

    companion object {
        /** Tab order on the home screen. */
        val tabs = listOf(NBA, NBL, NFL, CRICKET, AFL, SOCCER, NRL, NHL, MLB, FIGHT, MOTOR, TENNIS, GOLF)

        /** Sports hidden until switched on in Settings. */
        val hiddenByDefault = setOf(NRL, NHL, MLB, FIGHT, MOTOR, TENNIS, GOLF).map { it.name }.toSet()
    }
}

/** A league whose schedule we download. */
data class League(
    val sport: Sport,
    val name: String,
    /** ESPN site API path, e.g. "basketball/nba". */
    val espnPath: String? = null,
    /** TheSportsDB sport name for eventsday.php (used where ESPN has no coverage). */
    val sportsDbSport: String? = null,
    /** Keep only TheSportsDB events whose league name contains one of these. */
    val sportsDbLeague: List<String> = emptyList(),
    /** ...and none of these. */
    val sportsDbExclude: List<String> = emptyList(),
    /** Words that identify this league's dedicated channels, e.g. "nba league pass". */
    val channelHints: List<String> = emptyList(),
)

object Leagues {
    val all = listOf(
        League(Sport.NBA, "NBA", espnPath = "basketball/nba", channelHints = listOf("nba")),
        League(
            Sport.NBL, "NBL", sportsDbSport = "Basketball",
            sportsDbLeague = listOf("NBL", "National Basketball League"), sportsDbExclude = listOf("NBL1", "Czech", "Women"),
            channelHints = listOf("nbl", "espn"),
        ),
        League(Sport.NFL, "NFL", espnPath = "football/nfl", channelHints = listOf("nfl", "redzone")),
        League(
            Sport.CRICKET, "BBL", sportsDbSport = "Cricket",
            sportsDbLeague = listOf("Big Bash"), sportsDbExclude = listOf("Women"),
            channelHints = listOf("big bash", "bbl", "cricket"),
        ),
        League(
            Sport.AFL, "AFL", espnPath = "australian-football/afl", sportsDbSport = "Australian Football",
            sportsDbLeague = listOf("AFL"), sportsDbExclude = listOf("AFLW", "Women"),
            channelHints = listOf("afl", "footy"),
        ),
        // Soccer kept for Manchester United: league plus the cups they play in.
        League(Sport.SOCCER, "Premier League", espnPath = "soccer/eng.1", channelHints = listOf("premier league", "epl")),
        League(Sport.SOCCER, "FA Cup", espnPath = "soccer/eng.fa", channelHints = listOf("fa cup")),
        League(Sport.SOCCER, "League Cup", espnPath = "soccer/eng.league_cup", channelHints = listOf("carabao", "efl")),
        League(Sport.SOCCER, "Champions League", espnPath = "soccer/uefa.champions", channelHints = listOf("champions league", "ucl")),
        League(Sport.SOCCER, "Europa League", espnPath = "soccer/uefa.europa", channelHints = listOf("europa")),
    )
}
