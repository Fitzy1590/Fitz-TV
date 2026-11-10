package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Channels you up-voted in the player because they worked well in high resolution. They rank
 * first (among HD or better streams) for later games, and the category they came from is
 * remembered for that league. Stored on the device only.
 */
class ChannelVotes(context: Context) {
    @Serializable
    data class Vote(val name: String, val group: String = "", val height: Int = 0, val fps: Float = 0f, val at: Long = 0)

    private val file = File(context.filesDir, "cache/channel-votes.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val votes = ConcurrentHashMap<String, Vote>().also { m ->
        runCatching { json.decodeFromString<Map<String, Vote>>(file.readText()).forEach { (k, v) -> m[k] = v } }
    }

    /** Provider + normalised name: the same channel across restarts and list refreshes. */
    private fun key(c: Channel) = c.sourceId + "|" + Text.norm(c.name).trim()

    private fun save() { runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString<Map<String, Vote>>(votes.toMap())) } }

    fun has(c: Channel) = votes.containsKey(key(c))
    val count: Int get() = votes.size

    /** Up-vote (true) or take the vote back (false). */
    fun toggle(c: Channel, height: Int, fps: Float): Boolean {
        val k = key(c)
        return if (votes.remove(k) != null) { save(); false }
        else { votes[k] = Vote(c.label, c.group, height, fps, System.currentTimeMillis()); save(); true }
    }

    fun clear() { votes.clear(); save() }

    /** Up-voted names, newest first (for Settings). */
    fun names(): List<String> = votes.values.sortedByDescending { it.at }.map { it.name }
}
