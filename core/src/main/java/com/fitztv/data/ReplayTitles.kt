package com.fitztv.data

import com.fitztv.sports.Sport
import com.fitztv.sports.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/** Is a replay-library title about this exact game? */
object ReplayTitles {
    private val date = Regex("""\b(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{4}|\d{2})\b""")

    /**
     * Both teams must be named. A weak form ("manchester", "man", an abbreviation) only counts
     * when the title does not name some other team in full — "Aston Villa vs Manchester City" is
     * not Aston Villa v Manchester United. A date in the title must be within 2 days of kick-off
     * (an old fixture between the same teams is a different game).
     */
    fun matches(title: String, game: Game, otherTeamNames: List<String>): Boolean {
        val n = Text.norm(title)
        if (game.home.isBlank()) return n.contains(Text.norm(game.title))
        val hk = TeamKeys.of(game.home, game.homeShort, game.sport)
        val ak = TeamKeys.of(game.away, game.awayShort, game.sport)
        val hw = TeamKeys.weak(game.home, game.homeShort, game.homeAbbr, game.sport)
        val aw = TeamKeys.weak(game.away, game.awayShort, game.awayAbbr, game.sport)
        val ours = listOf(game.home, game.away)
        val otherFull = otherTeamNames.filter { o -> ours.none { sameTeam(it, o) } }
            .map { Text.norm(it).trim() }.filter { it.length >= 6 && it.contains(' ') }
        val namesOther = otherFull.any { n.contains(it) }
        val h = TeamKeys.mentioned(n, hk) || (!namesOther && TeamKeys.mentioned(n, hw))
        val a = TeamKeys.mentioned(n, ak) || (!namesOther && TeamKeys.mentioned(n, aw))
        if (!(h && a) || !(TeamKeys.mentioned(n, hk) || TeamKeys.mentioned(n, ak))) return false
        return dateOk(title, game.startUtcMillis)
    }

    fun dateOk(title: String, startUtcMillis: Long): Boolean {
        val m = date.find(title) ?: return true
        val (x, y, z) = m.destructured
        val year = z.toInt().let { if (it < 100) 2000 + it else it }
        val start = Instant.ofEpochMilli(startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        fun ok(day: Int, month: Int) = runCatching { abs(LocalDate.of(year, month, day).toEpochDay() - start.toEpochDay()) <= 2 }.getOrDefault(false)
        return ok(x.toInt(), y.toInt()) || ok(y.toInt(), x.toInt())
    }
}
