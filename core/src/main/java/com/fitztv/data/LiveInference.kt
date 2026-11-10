package com.fitztv.data

import com.fitztv.sports.Sport
import com.fitztv.sports.Text
import java.util.concurrent.TimeUnit

/**
 * Live state and start times for fixtures from feeds that don't report them (TheSportsDB:
 * cricket, NBL, AFLW, NRL...), using your provider's TV guide as the source of truth:
 *  - a guide listing that names both teams fixes the start time to the broadcast's
 *  - while such a listing is airing, the game is live
 *  - otherwise the game is live from its start for a typical game length, then ended
 * Live games the feeds miss entirely ("Live WBBL Melbourne.R v Adelaide.S") are added from
 * the guide so they still appear in Live now.
 */
object LiveInference {
    private val H = TimeUnit.HOURS.toMillis(1)
    private val M = TimeUnit.MINUTES.toMillis(1)

    /** Latest a game can plausibly still be live: typical length plus 3 h (delays, extra time, rain). */
    fun liveUntil(g: Game): Long = g.startUtcMillis + typicalLength(g) + 3 * H

    private fun typicalLength(g: Game): Long {
        val t = (g.title + " " + g.league).lowercase()
        return when (g.sport) {
            Sport.CRICKET -> when { "t20" in t || "bash" in t || "bbl" in t -> 4 * H; "test" in t -> 8 * H; else -> 8 * H }
            Sport.NBA, Sport.NBL -> 150 * M
            Sport.NFL -> 210 * M
            Sport.AFL -> 3 * H
            Sport.NRL -> 2 * H
            Sport.SOCCER -> 2 * H
            Sport.NHL -> 3 * H
            Sport.MLB -> 200 * M
            Sport.FIGHT -> 5 * H
            Sport.TENNIS -> 4 * H
            Sport.GOLF -> 10 * H
            else -> 3 * H
        }
    }

    private class Listing(val ch: Channel, val p: EpgProgram, val norm: String)

    // Listings are rebuilt only when the channels / guide change or every 10 minutes, not on every
    // live refresh (normalising every guide entry each time was slow on big guides).
    @Volatile private var cacheKey: Triple<Int, Int, Long>? = null
    @Volatile private var cached: List<Listing> = emptyList()

    private fun listings(channels: List<Channel>, epg: Map<String, List<EpgProgram>>, now: Long): List<Listing> {
        val key = Triple(System.identityHashCode(channels), System.identityHashCode(epg), now / (10 * M))
        if (key == cacheKey) return cached
        return buildListings(channels, epg, now).also { cached = it; cacheKey = key }
    }

    /** Guide listings on sports channels, from 18 h ago to 18 h ahead. */
    private fun buildListings(channels: List<Channel>, epg: Map<String, List<EpgProgram>>, now: Long): List<Listing> {
        val out = ArrayList<Listing>()
        val seen = HashSet<String>()
        for (ch in channels) {
            val id = ch.epgId ?: continue
            if (!seen.add(id + "|" + ch.sport)) continue
            epg[id]?.forEach { p ->
                if (p.stopMillis > now - 18 * H && p.startMillis < now + 18 * H) out += Listing(ch, p, Text.norm(p.title + " " + p.desc))
            }
        }
        return out
    }

    private val liveWord = Regex("""\blive\b""", RegexOption.IGNORE_CASE)

    fun apply(games: List<Game>, channels: List<Channel>, epg: Map<String, List<EpgProgram>>, now: Long): List<Game> {
        val ls = if (epg.isEmpty()) emptyList() else listings(channels, epg, now)
        val fixed = games.map { g -> if (g.id.startsWith("tsdb:") && g.state != "post") infer(g, ls, now) else g }
            .filterNot { it.id.startsWith("epg:") }
        return (fixed + fromGuide(fixed, ls, now)).sortedBy { it.startUtcMillis }
    }

    private fun infer(g: Game, ls: List<Listing>, now: Long): Game {
        if (g.home.isBlank()) return timeOnly(g, now)
        val hk = TeamKeys.of(g.home, g.homeShort, g.sport)
        val ak = TeamKeys.of(g.away, g.awayShort, g.sport)
        // Broadcast listings naming both teams within 18 h of the feed's start time; live ones first.
        val matches = ls.filter { (it.ch.sport == g.sport || it.ch.sport == Sport.GENERAL) &&
            Math.abs(it.p.startMillis - g.startUtcMillis) < 18 * H && TeamKeys.mentioned(it.norm, hk) && TeamKeys.mentioned(it.norm, ak) }
        if (matches.isEmpty()) return timeOnly(g, now)
        val live = matches.filter { liveWord.containsMatchIn(it.p.title) }.ifEmpty { matches }
        val first = live.minByOrNull { it.p.startMillis }!!
        val start = if (Math.abs(first.p.startMillis - g.startUtcMillis) > 10 * M) first.p.startMillis else g.startUtcMillis
        val onNow = live.any { now in it.p.startMillis until it.p.stopMillis }
        val ended = !onNow && live.all { it.p.stopMillis <= now } && now > start
        return when {
            onNow -> g.copy(startUtcMillis = start, state = "in", statusText = g.statusText.takeIf { g.state == "in" && it.isNotBlank() } ?: "Live")
            ended -> g.copy(startUtcMillis = start, state = "post", statusText = "Ended")
            else -> g.copy(startUtcMillis = start)
        }
    }

    /** No guide listing: live from the start for a typical game length, then ended. */
    private fun timeOnly(g: Game, now: Long): Game = when {
        g.state == "in" || now < g.startUtcMillis -> g
        now < g.startUtcMillis + typicalLength(g) -> g.copy(state = "in", statusText = "In progress")
        else -> g.copy(state = "post", statusText = "Ended")
    }

    private val teamsPattern = Regex("""^(.+?)\s+(?:v|vs|vs\.|v\.)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val liveLead = Regex("""^(?:live[:\s-]*)+""", RegexOption.IGNORE_CASE)
    private val tagLead = Regex("""^(?:[A-Z0-9\-]{2,6}:?\s+)+""")

    /** Live broadcasts ("Live WBBL Melbourne.R v Adelaide.S") that no schedule feed listed. */
    private fun fromGuide(games: List<Game>, ls: List<Listing>, now: Long): List<Game> {
        val known = games.filter { it.home.isNotBlank() && Math.abs(it.startUtcMillis - now) < 18 * H }
            .map { TeamKeys.of(it.home, it.homeShort, it.sport) to TeamKeys.of(it.away, it.awayShort, it.sport) }
        val out = LinkedHashMap<String, Game>()
        for (l in ls) {
            if (now !in l.p.startMillis until l.p.stopMillis) continue
            if (!l.p.title.trimStart().startsWith("live", ignoreCase = true)) continue
            val sport = l.ch.sport.takeIf { it != Sport.GENERAL } ?: Text.classify(l.p.title, l.ch.group) ?: continue
            if (sport == Sport.GENERAL) continue
            var t = l.p.title.trim().replace(liveLead, "").trim().replace(tagLead, "").trim()
            t = t.substringAfter(": ", t).trim()
            val m = teamsPattern.find(t) ?: continue
            val away = TeamKeys.expand(m.groupValues[1].trim())
            val home = TeamKeys.expand(m.groupValues[2].split(" - ", " | ", " (", ",").first().trim())
            if (away.length < 3 || home.length < 3 || away.length > 40 || home.length > 40) continue
            val n = Text.norm("$away $home")
            if (known.any { (h, a) -> TeamKeys.mentioned(n, h) && TeamKeys.mentioned(n, a) }) continue
            val id = "epg:" + Text.norm(away).trim().replace(' ', '-') + ":" + Text.norm(home).trim().replace(' ', '-')
            if (id in out) continue
            out[id] = Game(
                id = id, sport = sport, league = leagueTag(l.p.title) ?: sport.label, title = "$away v $home",
                home = home, away = away, homeShort = home, awayShort = away,
                startUtcMillis = l.p.startMillis, state = "in", statusText = "Live", featured = false,
            )
        }
        return out.values.toList()
    }

    private val tags = listOf("WBBL", "BBL", "NBL", "AFLW", "AFL", "NRL", "A-League", "EPL", "NBA", "NFL", "T20", "ODI", "Test")
    private fun leagueTag(title: String): String? = tags.firstOrNull { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(title) }
}
