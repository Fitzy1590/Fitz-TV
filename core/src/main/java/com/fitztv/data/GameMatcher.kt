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

    /** Guide entries on a channel overlapping [from, to) (lists are sorted by start time). */
    private fun programsIn(id: String, from: Long, to: Long): List<EpgProgram> {
        val list = epg[id] ?: return emptyList()
        // First entry that could still be on at [from]: no programme runs longer than 12 h.
        var lo = 0; var hi = list.size
        val earliest = from - TimeUnit.HOURS.toMillis(12)
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (list[mid].startMillis < earliest) lo = mid + 1 else hi = mid }
        val out = ArrayList<EpgProgram>(4)
        for (i in lo until list.size) {
            val p = list[i]
            if (p.startMillis >= to) break
            if (p.stopMillis > from) out += p
        }
        return out
    }

    /**
     * Every channel is checked, not only those filed under the game's sport: a channel naming both
     * teams, or whose TV guide lists both teams around kick-off, counts whatever it is filed under.
     * Weaker signals (one team, the broadcaster list, league hints) only count on channels of this
     * sport or general sports channels.
     */
    private fun compute(game: Game): List<Pair<Channel, Int>> {
        val homeKeys = TeamKeys.of(game.home, game.homeShort, game.sport)
        val awayKeys = TeamKeys.of(game.away, game.awayShort, game.sport)
        // Multi-word names ("adelaide united") are safe on their own; "adelaide" alone is not.
        val homeStrong = homeKeys.filter { it.trim().contains(' ') }
        val awayStrong = awayKeys.filter { it.trim().contains(' ') }
        val titleKeys = if (homeKeys.isEmpty()) listOf(game.title).filter { it.length > 3 }.map { Text.norm(it) } else emptyList()
        val hints = Leagues.all.firstOrNull { it.name == game.league }?.channelHints.orEmpty().map { Text.norm(it) }
        val bcasts = game.broadcasts.map { Text.norm(it) }.filter { it.trim().length >= 3 }

        val window0 = game.startUtcMillis - TimeUnit.MINUTES.toMillis(30)
        val window1 = game.startUtcMillis + TimeUnit.HOURS.toMillis(3)

        val scored = ArrayList<Pair<Channel, Int>>()
        for ((ch, n) in indexed) {
            val inPool = ch.sport == game.sport || ch.sport == Sport.GENERAL
            var score = 0
            val h = homeKeys.any { n.contains(it) }
            val a = awayKeys.any { n.contains(it) }
            if (h && a) score = 100
            else if ((h || a) && ch.sport == game.sport) score = 60
            else if (inPool && titleKeys.any { n.contains(it) }) score = 80

            if (score < 90) ch.epgId?.let { id ->
                for (p in programsIn(id, window0, window1)) {
                    val t = Text.norm(p.title + " " + p.desc)
                    val eh = homeKeys.any { t.contains(it) }
                    val ea = awayKeys.any { t.contains(it) }
                    score = maxOf(score, when {
                        eh && ea -> 95
                        inPool && (homeStrong.any { t.contains(it) } || awayStrong.any { t.contains(it) }) -> 70
                        inPool && titleKeys.any { t.contains(it) } -> 85
                        else -> 0
                    })
                }
            }
            val slot = looksLikeEvent(ch.name)
            if (score == 0 && inPool && !slot && bcasts.any { b -> n.contains(b) }) score = 45
            if (score == 0 && inPool && !slot && hints.any { n.contains(it) }) score = 20
            if (score > 0) scored += ch to score
        }
        return scored.sortedWith(compareByDescending<Pair<Channel, Int>> { it.second }.thenByDescending { it.first.quality })
            .take(60)
    }

    /**
     * When nothing matches: channels of this sport / general sport ordered by what their guide says
     * is on now — this league, then this sport — so the list starts with likely candidates.
     */
    fun fallback(game: Game, now: Long): List<Channel> {
        val league = (listOf(game.league) + Leagues.all.firstOrNull { it.name == game.league }?.channelHints.orEmpty())
            .map { Text.norm(it) }.filter { it.trim().length >= 3 }
        val sportWords = (game.sport.keywords + game.sport.label).map { Text.norm(it) }.filter { it.trim().length >= 3 }
        return indexed.asSequence().filter { it.ch.sport == game.sport || it.ch.sport == Sport.GENERAL }
            .map { (ch, n) ->
                val t = ch.epgId?.let { id -> programsIn(id, now, now + 1).firstOrNull() }?.let { Text.norm(it.title + " " + it.desc) }
                val rank = when {
                    t != null && league.any { t.contains(it) } -> 0
                    t != null && sportWords.any { t.contains(it) } -> 1
                    t == null && league.any { n.contains(it) } -> 2
                    t == null -> 3
                    else -> 4   // the guide says it's showing something else
                }
                Triple(ch, rank, ch.quality)
            }
            .sortedWith(compareBy<Triple<Channel, Int, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }.toList()
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
    fun looksLikeEvent(name: String) = eventName.containsMatchIn(name) || eventSlot.containsMatchIn(name)

    /** "ENDED | …", "NEXT | …", "(ESPN+ 016) | …": numbered event slots showing a named event. */
    private val eventSlot = Regex("""^\W*(ended|next|upcoming|replay|starts?)\s*[|:]|\([^)]*\d{2,3}\s*\)\s*[|:]\s*\S+\s+\S+""", RegexOption.IGNORE_CASE)
}
