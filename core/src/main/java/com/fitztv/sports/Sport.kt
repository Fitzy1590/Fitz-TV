package com.fitztv.sports

import kotlinx.serialization.Serializable

/**
 * Sports the app knows about. [keywords] are matched (as whole words) against channel
 * names and playlist group titles to decide which sport a channel belongs to.
 */
@Serializable
enum class Sport(val label: String, val emoji: String, val keywords: List<String>) {
    NBA("NBA", "🏀", listOf("nba", "nba tv", "basketball", "wnba", "ncaab", "league pass", "euroleague", "space city", "schn", "rockets")),
    NBL("NBL", "🏀", listOf("nbl", "nbl1", "aussie hoops", "australian basketball")),
    NFL("NFL", "🏈", listOf("nfl", "redzone", "red zone", "ncaaf", "college football", "nfl network", "sunday ticket")),
    SOCCER("Soccer", "⚽", listOf(
        "soccer", "football", "premier league", "epl", "la liga", "laliga", "serie a", "bundesliga",
        "ligue 1", "champions league", "europa", "mls", "a league", "a-league", "fa cup", "efl",
        "uefa", "fifa", "copa", "eredivisie", "liga mx", "spfl", "bein", "dazn", "optus", "premier sports",
    )),
    CRICKET("BBL / Cricket", "🏏", listOf("cricket", "willow", "ipl", "big bash", "bbl", "wbbl", "test match", "t20", "odi", "star sports", "sky sports cricket", "fox cricket")),
    AFL("AFL", "🏉", listOf("afl", "aussie rules", "footy", "fox footy")),
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
    /**
     * TheSportsDB league ids. Asked per league (eventsday.php?l=id) because the per-sport day list
     * on the free key only returns 3 events worldwide, which silently dropped games.
     */
    val sportsDbIds: List<String> = emptyList(),
    /** Find this season's international cricket series on ESPN (they get new ids every tour). */
    val espnCricketSeries: Boolean = false,
    /** Words that identify this league's dedicated channels, e.g. "nba league pass". */
    val channelHints: List<String> = emptyList(),
    /**
     * Featured leagues fill the guide and upcoming rows. Others only appear when they're
     * live (the Live now row), or when one of your favourite teams is playing.
     */
    val featured: Boolean = true,
    /** Only shown when a favourite team plays (e.g. international friendlies). */
    val favouritesOnly: Boolean = false,
)

object Leagues {
    val all = listOf(
        League(Sport.NBA, "NBA", espnPath = "basketball/nba", channelHints = listOf("nba")),
        League(
            Sport.NBL, "NBL", espnPath = "basketball/nbl", sportsDbSport = "Basketball", sportsDbIds = listOf("4434"),
            sportsDbLeague = listOf("NBL", "National Basketball League"), sportsDbExclude = listOf("NBL1", "Czech", "Women"),
            channelHints = listOf("nbl", "espn"),
        ),
        League(Sport.NFL, "NFL", espnPath = "football/nfl", channelHints = listOf("nfl", "redzone")),
        League(
            Sport.CRICKET, "BBL", espnPath = "cricket/8044", sportsDbSport = "Cricket", sportsDbIds = listOf("4461"),
            sportsDbLeague = listOf("Big Bash"), sportsDbExclude = listOf("Women"),
            channelHints = listOf("big bash", "bbl", "cricket"),
        ),
        League(
            Sport.CRICKET, "WBBL", espnPath = "cricket/21284", sportsDbSport = "Cricket",
            sportsDbLeague = listOf("Women's Big Bash", "Womens Big Bash", "WBBL", "Big Bash League Women", "Women Big Bash"),
            channelHints = listOf("wbbl", "big bash", "cricket"),
        ),
        League(
            Sport.AFL, "AFL", espnPath = "australian-football/afl", sportsDbSport = "Australian Football", sportsDbIds = listOf("4456"),
            sportsDbLeague = listOf("AFL"), sportsDbExclude = listOf("AFLW", "Women"),
            channelHints = listOf("afl", "footy"),
        ),
        // Soccer kept for Manchester United: league plus the cups they play in.
        League(Sport.SOCCER, "Premier League", espnPath = "soccer/eng.1", channelHints = listOf("premier league", "epl")),
        League(Sport.SOCCER, "FA Cup", espnPath = "soccer/eng.fa", channelHints = listOf("fa cup")),
        League(Sport.SOCCER, "League Cup", espnPath = "soccer/eng.league_cup", channelHints = listOf("carabao", "efl")),
        League(Sport.SOCCER, "Champions League", espnPath = "soccer/uefa.champions", channelHints = listOf("champions league", "ucl")),
        League(Sport.SOCCER, "Europa League", espnPath = "soccer/uefa.europa", channelHints = listOf("europa")),

        // ---- Other top leagues: shown when live, or when a favourite team plays ----------
        League(Sport.SOCCER, "A-League", espnPath = "soccer/aus.1", channelHints = listOf("a league", "isuzu"), featured = false),
        League(Sport.SOCCER, "LaLiga", espnPath = "soccer/esp.1", channelHints = listOf("la liga", "laliga"), featured = false),
        League(Sport.SOCCER, "Serie A", espnPath = "soccer/ita.1", channelHints = listOf("serie a"), featured = false),
        League(Sport.SOCCER, "Bundesliga", espnPath = "soccer/ger.1", channelHints = listOf("bundesliga"), featured = false),
        League(Sport.SOCCER, "Ligue 1", espnPath = "soccer/fra.1", channelHints = listOf("ligue 1"), featured = false),
        League(Sport.SOCCER, "MLS", espnPath = "soccer/usa.1", channelHints = listOf("mls"), featured = false),
        League(Sport.NBA, "WNBA", espnPath = "basketball/wnba", channelHints = listOf("wnba"), featured = false),
        League(Sport.NHL, "NHL", espnPath = "hockey/nhl", channelHints = listOf("nhl"), featured = false),
        League(Sport.MLB, "MLB", espnPath = "baseball/mlb", channelHints = listOf("mlb"), featured = false),
        League(
            Sport.NRL, "NRL", espnPath = "rugby-league/3", sportsDbSport = "Rugby", sportsDbIds = listOf("4416"), sportsDbLeague = listOf("NRL", "National Rugby League"),
            sportsDbExclude = listOf("Women", "NRLW"), channelHints = listOf("nrl", "fox league"), featured = false,
        ),
        League(Sport.FIGHT, "UFC", espnPath = "mma/ufc", channelHints = listOf("ufc"), featured = false),

        // ---- Internationals: shown in their sport tabs like any other league -------------
        League(
            Sport.CRICKET, "International Cricket", sportsDbSport = "Cricket", espnCricketSeries = true,
            // ODI series, T20I series, Test series, World Test Championship, Cricket World Cup
            sportsDbIds = listOf("4801", "4979", "4844", "5100", "4575"),
            sportsDbLeague = listOf("Test", "ODI", "One Day", "T20I", "Twenty20 International", "T20 International", "ICC", "World Cup",
                "Champions Trophy", "Ashes", "Border-Gavaskar", "International", "Tri-Series", "Tri Series", "Tour"),
            sportsDbExclude = listOf("Women", "Big Bash", "County", "Sheffield", "Ranji", "Plunket", "Marsh", "U19", "Under-19", "A Team"),
            channelHints = listOf("cricket", "willow", "fox cricket", "sky sports cricket", "star sports"),
        ),
        League(Sport.SOCCER, "WC Qualifying", espnPath = "soccer/fifa.worldq.afc", channelHints = listOf("world cup", "qualifier", "socceroos")),
        League(Sport.SOCCER, "WC Qualifying (Europe)", espnPath = "soccer/fifa.worldq.uefa", channelHints = listOf("world cup", "qualifier"), featured = false),
        League(Sport.SOCCER, "Nations League", espnPath = "soccer/uefa.nations", channelHints = listOf("nations league"), featured = false),
        League(Sport.SOCCER, "Asian Cup", espnPath = "soccer/afc.asian.cup", channelHints = listOf("asian cup", "socceroos")),
        League(Sport.SOCCER, "Friendly", espnPath = "soccer/fifa.friendly", channelHints = listOf("friendly", "socceroos")),
        League(Sport.SOCCER, "World Cup", espnPath = "soccer/fifa.world", channelHints = listOf("world cup", "socceroos")),
        League(
            Sport.NBA, "FIBA", sportsDbSport = "Basketball", sportsDbLeague = listOf("FIBA", "Olympic"), sportsDbExclude = listOf("Women"),
            channelHints = listOf("fiba", "boomers"),
        ),

        // ---- Followed via favourite teams only (Port Adelaide AFLW) ----------------------
        League(
            Sport.AFL, "AFLW", sportsDbSport = "Australian Football", sportsDbIds = listOf("5311"), sportsDbLeague = listOf("AFLW", "Women"),
            channelHints = listOf("aflw", "afl"), featured = false, favouritesOnly = true,
        ),
    )

    fun byName(name: String): League? = all.firstOrNull { it.name == name }
}
