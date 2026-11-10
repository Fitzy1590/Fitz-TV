package com.fitztv.player

import android.content.Context
import com.fitztv.data.Channel
import com.fitztv.sports.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * How hard a stream is being nursed. Level 0 is normal. Each step up (after repeated reconnects)
 * uses another User-Agent, a bigger buffer, and waits longer before resuming after a stall.
 */
@Serializable
data class StreamTuning(val level: Int = 0, val uaIndex: Int = 0) {
    fun next() = StreamTuning(minOf(level + 1, MAX_LEVEL), uaIndex + 1)

    /** The User-Agent to send: the normal one at index 0, then the alternatives in turn. */
    fun userAgent(default: String): String = if (uaIndex == 0) default else AGENTS[(uaIndex - 1) % AGENTS.size]

    /** Buffer target multiplier: 1x, 1.5x, 2x, 3x. */
    val bufferFactor: Double get() = when (level) { 0 -> 1.0; 1 -> 1.5; 2 -> 2.0; else -> 3.0 }
    /** Buffered video needed before first play. */
    val startMs: Int get() = when (level) { 0 -> 1_500; 1 -> 2_500; 2 -> 4_000; else -> 6_000 }
    /** Buffered video needed to resume after a stall ("buffer re-time"). */
    val rebufferMs: Int get() = when (level) { 0 -> 4_000; 1 -> 7_000; 2 -> 10_000; else -> 15_000 }

    companion object {
        const val MAX_LEVEL = 3
        /** Player identities providers commonly allow. */
        val AGENTS = listOf(
            "VLC/3.0.20 LibVLC/3.0.20",
            "Lavf/60.16.100",
            "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV Build/RQ1A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "okhttp/4.12.0",
        )
    }
}

/** Tunings that rescued a channel before, so it starts steady next time. Stored on the device only. */
class StreamFixes(context: Context) {
    private val file = File(context.filesDir, "cache/stream-fixes.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val data = ConcurrentHashMap<String, StreamTuning>().also { m ->
        runCatching { json.decodeFromString<Map<String, StreamTuning>>(file.readText()).forEach { (k, v) -> m[k] = v } }
    }

    private fun key(c: Channel) = c.sourceId + "|" + Text.norm(c.name).trim()

    fun get(c: Channel): StreamTuning? = data[key(c)]

    fun remember(c: Channel, t: StreamTuning) {
        if (t.level == 0 || data[key(c)] == t) return
        data[key(c)] = t
        if (data.size > 200) data.keys.take(data.size - 200).forEach { data.remove(it) }
        runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString<Map<String, StreamTuning>>(data.toMap())) }
    }
}
