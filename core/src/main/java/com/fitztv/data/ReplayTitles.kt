package com.fitztv.data

import com.fitztv.sports.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/** Replay-library helpers: is a title about this exact game, and what date is in it. */
object ReplayTitles {
    private val date = Regex("""\b(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{4}|\d{2})\b""")

    /**
     * Matcher for one game, built once and then applied to thousands of titles.
     * Both teams must be named. A weak form ("manchester", "man", an abbreviation) only counts
     * when the title does not name some other team in full — "Aston Villa vs Manchester City" is
     * not Aston Villa v Manchester United. A date in the title must be within 2 days of kick-off.
     */
    class ForGame(private val game: Game, otherTeamNames: List<String>) {
        private val hk = TeamKeys.of(game.home, game.homeShort, game.sport)
        private val ak = TeamKeys.of(game.away, game.awayShort, game.sport)
        private val hw = TeamKeys.weak(game.home, game.homeShort, game.homeAbbr, game.sport)
        private val aw = TeamKeys.weak(game.away, game.awayShort, game.awayAbbr, game.sport)
        private val titleKey = Text.norm(game.title)
        private val otherFull: List<String> = run {
            val ours = listOf(game.home, game.away)
            otherTeamNames.asSequence().distinct().filter { o -> ours.none { sameTeam(it, o) } }
                .map { Text.norm(it).trim() }.filter { it.length >= 6 && it.contains(' ') }.toList()
        }

        /** 0 = not this pairing, 1 = these two teams but another date (an earlier meeting), 2 = this game. */
        fun fit(title: String): Int {
            val n = Text.norm(title)
            if (game.home.isBlank()) return if (n.contains(titleKey)) 2 else 0
            val hs = TeamKeys.mentioned(n, hk); val as_ = TeamKeys.mentioned(n, ak)
            if (!hs && !as_) return 0
            val namesOther = otherFull.any { n.contains(it) }
            val h = hs || (!namesOther && TeamKeys.mentioned(n, hw))
            val a = as_ || (!namesOther && TeamKeys.mentioned(n, aw))
            if (!(h && a)) return 0
            return if (dateOk(title, game.startUtcMillis)) 2 else 1
        }

        fun matches(title: String): Boolean = fit(title) == 2
    }

    fun matches(title: String, game: Game, otherTeamNames: List<String>): Boolean = ForGame(game, otherTeamNames).matches(title)

    fun dateOk(title: String, startUtcMillis: Long): Boolean {
        val m = date.find(title) ?: return true
        val (x, y, z) = m.destructured
        val year = z.toInt().let { if (it < 100) 2000 + it else it }
        val start = Instant.ofEpochMilli(startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        fun ok(day: Int, month: Int) = runCatching { abs(LocalDate.of(year, month, day).toEpochDay() - start.toEpochDay()) <= 2 }.getOrDefault(false)
        return ok(x.toInt(), y.toInt()) || ok(y.toInt(), x.toInt())
    }

    /** The date written in a replay title ("06.10.2024", day first), as UTC midnight; null if none. */
    fun titleDate(title: String): Long? {
        val m = date.find(title) ?: return null
        val (x, y, z) = m.destructured
        val year = z.toInt().let { if (it < 100) 2000 + it else it }
        val d = x.toInt(); val mo = y.toInt()
        val ld = runCatching { LocalDate.of(year, mo, d) }.getOrNull() ?: runCatching { LocalDate.of(year, d, mo) }.getOrNull() ?: return null
        return ld.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}

/** The replay library as shown on the Replays screens. */
object VodLibrary {
    private val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH)

    /** Only the chosen sport (everything when none), newest game first. */
    fun of(vod: List<VodItem>, sport: com.fitztv.sports.Sport?, limit: Int = 300): List<VodItem> =
        vod.asSequence().filter { sport == null || it.sport == sport }.sortedByDescending { it.eventMillis }.take(limit).toList()

    /** "9 Sep 2026", or blank when the item has no date at all. */
    fun dateLabel(v: VodItem): String =
        v.eventMillis.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().format(fmt) }.orEmpty()
}

