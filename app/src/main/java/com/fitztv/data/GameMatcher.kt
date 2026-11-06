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
    private val cache = HashMap<String, List<Channel>>()

    fun channelsFor(game: Game): List<Channel> = synchronized(cache) {
        cache.getOrPut(game.id) { compute(game) }
    }

    private fun compute(game: Game): List<Channel> {
        val homeKeys = teamKeys(game.home, game.homeShort)
        val awayKeys = teamKeys(game.away, game.awayShort)
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
            .map { it.first }
            .take(25)
    }

    /** Distinctive names for a team: full name, short name and the nickname ("Lakers"). */
    private fun teamKeys(full: String, short: String): List<String> {
        if (full.isBlank()) return emptyList()
        val keys = linkedSetOf(full, short)
        full.split(' ').lastOrNull()?.takeIf { it.length >= 4 }?.let { keys += it }
        return keys.map { Text.norm(it) }.filter { it.trim().length >= 3 && it.trim() !in generic }
    }

    private val generic = setOf("united", "city", "fc", "the", "club", "real", "athletic", "sporting", "state")
}
