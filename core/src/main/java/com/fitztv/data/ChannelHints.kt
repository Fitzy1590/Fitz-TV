package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Remembers regular (linear) channels that played a league / team well, as a hint for later
 * games: "NBL was great on AU: ESPN 1080p50 last time". Event-slot channels that get renamed
 * every day aren't remembered — their names already say what they show. Stored on the device only.
 */
class ChannelHints(context: Context) {
    @Serializable
    data class Hint(val plays: Int = 0, val bestHeight: Int = 0, val bestFps: Float = 0f, val lastAt: Long = 0)

    private val file = File(context.filesDir, "cache/channel-hints.json")
    private val json = Json { ignoreUnknownKeys = true }
    /** "lg:<league>" / "tm:<sport>:<team>" -> channel key -> hint. */
    private val data = ConcurrentHashMap<String, MutableMap<String, Hint>>()

    init {
        runCatching {
            json.decodeFromString<Map<String, Map<String, Hint>>>(file.readText())
                .forEach { (k, v) -> data[k] = ConcurrentHashMap(v) }
        }
    }

    private fun save() = runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString<Map<String, Map<String, Hint>>>(data.mapValues { it.value.toMap() })) }

    private val clubWords = setOf("fc", "afc", "sc", "cf", "football", "club", "the", "women", "w")
    private fun team(name: String) = Text.norm(name).trim().split(' ').filter { it.isNotEmpty() && it !in clubWords }.joinToString(" ")
    private fun keys(g: Game) = buildList {
        add("lg:${g.league}")
        if (g.home.isNotBlank()) { add("tm:${g.sport}:${team(g.home)}"); add("tm:${g.sport}:${team(g.away)}") }
    }
    private fun channelKey(c: Channel) = c.sourceId + "|" + Text.norm(c.name).trim()

    /** After a stream has played the game smoothly for a few minutes. */
    fun record(game: Game, c: Channel, height: Int, fps: Float, isEventChannel: Boolean) {
        if (height in 1 until 700 || isEventChannel) return
        // Channels that name a team are event channels: their name already finds them.
        val n = Text.norm(c.name)
        if (TeamKeys.mentioned(n, TeamKeys.of(game.home, game.homeShort, game.sport) + TeamKeys.of(game.away, game.awayShort, game.sport))) return
        val ck = channelKey(c)
        val now = System.currentTimeMillis()
        for (k in keys(game)) {
            val m = data.getOrPut(k) { ConcurrentHashMap() }
            val old = m[ck] ?: Hint()
            m[ck] = Hint(old.plays + 1, maxOf(old.bestHeight, height), maxOf(old.bestFps, fps), now)
            // Keep the 12 most recent per league / team.
            if (m.size > 12) m.entries.sortedBy { it.value.lastAt }.take(m.size - 12).forEach { m.remove(it.key) }
        }
        save()
    }

    /**
     * Channels remembered for this game's teams or league, with a rank (higher = stronger):
     * team hints beat league hints; more plays and better quality rank higher. Hints older than
     * 120 days are ignored.
     */
    fun ranked(game: Game, channels: List<Channel>): Map<String, Int> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(120)
        val ks = keys(game)
        val byKey = HashMap<String, Int>()
        for (k in ks) {
            val teamLevel = k.startsWith("tm:")
            data[k]?.forEach { (ck, h) ->
                if (h.lastAt < cutoff) return@forEach
                val q = (if (h.bestHeight >= 2000) 3 else if (h.bestHeight >= 1000) 2 else 1) + (if (h.bestFps >= 48f) 1 else 0)
                val r = (if (teamLevel) 20 else 10) + minOf(h.plays, 5) + q
                byKey[ck] = maxOf(byKey[ck] ?: 0, r)
            }
        }
        if (byKey.isEmpty()) return emptyMap()
        return channels.mapNotNull { c -> byKey[channelKey(c)]?.let { c.id to it } }.toMap()
    }
}
