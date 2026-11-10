package com.fitztv.data

/** One short line about a stream for the stream lists: is it this game, and its language. */
object StreamNotes {
    fun line(repo: Repository, c: Channel, game: Game?): String {
        val v = game?.let { repo.verdict(c, it) }
        val match = when (v) {
            GameMatcher.Verdict.CONFIRMED -> "Confirmed"
            GameMatcher.Verdict.CONTRADICTED -> "Showing something else"
            else -> null
        }
        val lang = when (StreamLanguage.tier(c)) { StreamLanguage.ENGLISH -> "English"; StreamLanguage.OTHER -> "Other language"; else -> null }
        return listOfNotNull(match, lang, repo.nowPlaying(c)?.title).joinToString(" · ")
    }

    fun contradicted(repo: Repository, c: Channel, game: Game?) =
        game != null && repo.verdict(c, game) == GameMatcher.Verdict.CONTRADICTED
}
