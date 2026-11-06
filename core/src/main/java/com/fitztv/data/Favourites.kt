package com.fitztv.data

data class FavouriteEntry(val team: String, val logo: String?, val game: Game?)

/** For each favourite team: its live game, else next game, else most recent result. */
fun favouriteEntries(games: List<Game>, settings: AppSettings, now: Long, logos: LogoStore? = null): List<FavouriteEntry> =
    settings.favoriteTeams.sorted().map { team ->
        val mine = games.filter { sameTeam(it.home, team) || sameTeam(it.away, team) }
        val g = mine.firstOrNull { it.gameState == GameState.LIVE }
            ?: mine.filter { it.gameState == GameState.PRE && it.startUtcMillis > now - 3_600_000 }.minByOrNull { it.startUtcMillis }
            ?: mine.filter { it.gameState == GameState.FINAL }.maxByOrNull { it.startUtcMillis }
        val logo = mine.firstNotNullOfOrNull { if (sameTeam(it.home, team)) it.homeLogo else it.awayLogo } ?: logos?.get(team)
        FavouriteEntry(team, logo, g)
    }.sortedBy { e ->
        when (e.game?.gameState) { GameState.LIVE -> 0; GameState.PRE -> 1; GameState.FINAL -> 2; null -> 3 }
    }

