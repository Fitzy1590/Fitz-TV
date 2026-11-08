package com.fitztv.data

import com.fitztv.sports.Leagues
import com.fitztv.sports.Sport
import com.fitztv.sports.Text
import java.util.concurrent.TimeUnit

/**
 * Works out which IPTV channels are likely to be showing a game, using (in order of
 * confidence) event channels that name both teams, EPG listings, the official broadcaster
 * list from the schedule feed, and finally league-dedicated channels.
 */
class GameMatcher(channels: List<Channel>, private val epg: Map<String, List<EpgProgram>>) {

    private data class Indexed(val ch: Channel, val norm: String)

    private val indexed = channels.map { Indexed(it, Text.norm(it.name)) }
    private val bySport = indexed.groupBy { it.ch.sport }
    private val cache = HashMap<String, List<Pair<Channel, Int>>>()

    /** Matching channels with a confidence score (100 = names both teams). */
    fun scoredFor(game: Game): List<Pair<Channel, Int>> = synchronized(cache) {
        cache.getOrPut(game.id) { compute(game) }
    }

    fun channelsFor(game: Game): List<Channel> = scoredFor(game).map { it.first }

    private fun compute(game: Game): List<Pair<Channel, Int>> {
        val homeKeys = TeamKeys.of(game.home, game.homeShort, game.sport)
        val awayKeys = TeamKeys.of(game.away, game.awayShort, game.sport)
        val titleKeys = if (homeKeys.isEmpty()) listOf(game.title).filter { it.length > 3 }.map { Text.norm(it) } else emptyList()
        val hints = Leagues.all.firstOrNull { it.name == game.league }?.channelHints.orEmpty().map { Text.norm(it) }
        val bcasts = game.broadcasts.map { Text.norm(it) }.filter { it.trim().length >= 3 }

        val window0 = game.startUtcMillis - TimeUnit.MINUTES.toMillis(30)
        val window1 = game.startUtcMillis + TimeUnit.HOURS.toMillis(3)

        val pool = bySport[game.sport].orEmpty() + bySport[Sport.GENERAL].orEmpty()
        val scored = ArrayList<Pair<Channel, Int>>()
        for ((ch, n) in pool) {
            var score = 0
            val h = homeKeys.any { n.contains(it) }
            val a = awayKeys.any { n.contains(it) }
            if (h && a) score = 100
            else if ((h || a) && ch.sport == game.sport) score = 60
            else if (titleKeys.any { n.contains(it) }) score = 80

            if (score < 90) ch.epgId?.let { id ->
                epg[id]?.forEach { p ->
                    if (p.stopMillis < window0 || p.startMillis > window1) return@forEach
                    val t = Text.norm(p.title + " " + p.desc)
                    val eh = homeKeys.any { t.contains(it) }
                    val ea = awayKeys.any { t.contains(it) }
                    score = maxOf(score, when {
                        eh && ea -> 95
                        eh || ea -> 70
                        titleKeys.any { t.contains(it) } -> 85
                        else -> 0
                    })
                }
            }
            if (score == 0 && bcasts.any { b -> n.contains(b) }) score = 45
            if (score == 0 && ch.sport == game.sport && hints.any { n.contains(it) }) score = 20
            if (score > 0) scored += ch to score
        }
        return scored.sortedWith(compareByDescending<Pair<Channel, Int>> { it.second }.thenByDescending { it.first.quality })
            .take(40)
    }

    enum class Verdict { CONFIRMED, UNKNOWN, CONTRADICTED }

    /**
     * Is this channel actually showing [game]? Checked at kick-off (or now, once it has started):
     * - its name names a team → confirmed; an event-style name about something else → contradicted
     * - its TV guide listing at that time names a team → confirmed; names something else → contradicted
     * - no guide data → unknown
     */
    fun verdict(ch: Channel, game: Game, now: Long): Verdict {
        val keys = TeamKeys.of(game.home, game.homeShort, game.sport) + TeamKeys.of(game.away, game.awayShort, game.sport) +
            (if (game.home.isBlank() && game.title.length > 3) listOf(Text.norm(game.title)) else emptyList())
        if (keys.isEmpty()) return Verdict.UNKNOWN
        val n = Text.norm(ch.name)
        if (TeamKeys.mentioned(n, keys)) return Verdict.CONFIRMED
        if (looksLikeEvent(ch.name)) return Verdict.CONTRADICTED
        val at = if (now >= game.startUtcMillis) now else game.startUtcMillis + TimeUnit.MINUTES.toMillis(15)
        val p = ch.epgId?.let { id -> epg[id]?.firstOrNull { at in it.startMillis until it.stopMillis } } ?: return Verdict.UNKNOWN
        val t = Text.norm(p.title + " " + p.desc)
        if (TeamKeys.mentioned(t, keys)) return Verdict.CONFIRMED
        // A generic listing for this league ("A-League Live") can't confirm or rule it out.
        val hints = Leagues.all.firstOrNull { it.name == game.league }?.channelHints.orEmpty()
            .map { Text.norm(it) }.filter { it.trim().length >= 4 && it.trim() !in broadHints }
        if (hints.any { t.contains(it) } || t.contains(Text.norm(game.league))) return Verdict.UNKNOWN
        return Verdict.CONTRADICTED
    }

    private val broadHints = setOf("cricket", "espn", "footy", "sport", "sports")
    private val eventName = Regex(
        """\b(vs?\.?|@)\s|[a-z]{4,}:\s*\S+\s+\S+|\b(mon|tue|wed|thu|fri|sat|sun)\w*\s+\d{1,2}\b|\b\d{1,2}\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)""",
        RegexOption.IGNORE_CASE,
    )

    /** "ESPN+ 004 | Football: Good Morning Football", "NEXT | FA CUP GOALS SHOW | Sat 07 Nov". */
    private fun looksLikeEvent(name: String) = eventName.containsMatchIn(name) || eventSlot.containsMatchIn(name)

    /** "ENDED | …", "NEXT | …", "(ESPN+ 016) | …": numbered event slots showing a named event. */
    private val eventSlot = Regex("""^\W*(ended|next|upcoming|replay|starts?)\s*[|:]|\([^)]*\d{2,3}\s*\)\s*[|:]\s*\S+\s+\S+""", RegexOption.IGNORE_CASE)
}
