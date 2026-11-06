package com.fitztv.sports

import kotlinx.serialization.Serializable

/**
 * Sports the app knows about. [keywords] are matched (as whole words) against channel
 * names and playlist group titles to decide which sport a channel belongs to.
 */
@Serializable
enum class Sport(val label: String, val emoji: String, val keywords: List<String>) {
    NBA("NBA", "🏀", listOf("nba", "basketball", "wnba", "ncaab", "league pass", "nbl", "euroleague")),
    NFL("NFL", "🏈", listOf("nfl", "redzone", "red zone", "ncaaf", "college football", "nfl network", "sunday ticket")),
    SOCCER("Soccer", "⚽", listOf(
        "soccer", "football", "premier league", "epl", "la liga", "laliga", "serie a", "bundesliga",
        "ligue 1", "champions league", "europa", "mls", "a league", "a-league", "fa cup", "efl",
        "uefa", "fifa", "copa", "eredivisie", "liga mx", "spfl", "bein", "dazn", "optus", "premier sports",
    )),
    CRICKET("Cricket", "🏏", listOf("cricket", "willow", "ipl", "big bash", "bbl", "test match", "t20", "odi", "star sports", "sky sports cricket", "fox cricket")),
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
        val tabs = listOf(NBA, NFL, SOCCER, CRICKET, AFL, NRL, NHL, MLB, FIGHT, MOTOR, TENNIS, GOLF)
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
    /** Words that identify this league's dedicated channels, e.g. "nba league pass". */
    val channelHints: List<String> = emptyList(),
)

object Leagues {
    val all = listOf(
        League(Sport.NBA, "NBA", espnPath = "basketball/nba", channelHints = listOf("nba")),
        League(Sport.NBA, "WNBA", espnPath = "basketball/wnba", channelHints = listOf("wnba")),
        League(Sport.NBA, "NCAA Basketball", espnPath = "basketball/mens-college-basketball", channelHints = listOf("ncaab")),
        League(Sport.NFL, "NFL", espnPath = "football/nfl", channelHints = listOf("nfl", "redzone")),
        League(Sport.NFL, "College Football", espnPath = "football/college-football", channelHints = listOf("ncaaf")),
        League(Sport.SOCCER, "Premier League", espnPath = "soccer/eng.1", channelHints = listOf("premier league", "epl")),
        League(Sport.SOCCER, "Champions League", espnPath = "soccer/uefa.champions", channelHints = listOf("champions league", "ucl")),
        League(Sport.SOCCER, "Europa League", espnPath = "soccer/uefa.europa", channelHints = listOf("europa")),
        League(Sport.SOCCER, "La Liga", espnPath = "soccer/esp.1", channelHints = listOf("la liga", "laliga")),
        League(Sport.SOCCER, "Serie A", espnPath = "soccer/ita.1", channelHints = listOf("serie a")),
        League(Sport.SOCCER, "Bundesliga", espnPath = "soccer/ger.1", channelHints = listOf("bundesliga")),
        League(Sport.SOCCER, "Ligue 1", espnPath = "soccer/fra.1", channelHints = listOf("ligue 1")),
        League(Sport.SOCCER, "MLS", espnPath = "soccer/usa.1", channelHints = listOf("mls")),
        League(Sport.SOCCER, "A-League", espnPath = "soccer/aus.1", channelHints = listOf("a league")),
        League(Sport.SOCCER, "FA Cup", espnPath = "soccer/eng.fa", channelHints = listOf("fa cup")),
        League(Sport.SOCCER, "Championship", espnPath = "soccer/eng.2", channelHints = listOf("efl")),
        League(Sport.AFL, "AFL", espnPath = "australian-football/afl", sportsDbSport = "Australian Football", channelHints = listOf("afl", "footy")),
        League(Sport.CRICKET, "Cricket", sportsDbSport = "Cricket", channelHints = listOf("cricket", "willow")),
        League(Sport.NRL, "Rugby", sportsDbSport = "Rugby", channelHints = listOf("nrl", "rugby")),
        League(Sport.NHL, "NHL", espnPath = "hockey/nhl", channelHints = listOf("nhl")),
        League(Sport.MLB, "MLB", espnPath = "baseball/mlb", channelHints = listOf("mlb")),
        League(Sport.FIGHT, "UFC", espnPath = "mma/ufc", channelHints = listOf("ufc")),
        League(Sport.MOTOR, "Formula 1", espnPath = "racing/f1", channelHints = listOf("f1", "formula")),
        League(Sport.GOLF, "PGA Tour", espnPath = "golf/pga", channelHints = listOf("golf", "pga")),
    )
}
