package com.fitztv.data

import android.content.Context
import com.fitztv.sports.Text
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns when a provider category tends to stall (for example match-day evenings): stalls are
 * counted per weekday and two-hour slot with a 14-day half-life. A category with repeated
 * stalls in the current slot ranks lower within its resolution class. Stored on the device only.
 */
class PeakMemory(context: Context) {
    @Serializable
    data class Stat(val count: Double = 0.0, val at: Long = 0)

    private val file = File(context.filesDir, "cache/peak-memory.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val data = ConcurrentHashMap<String, Stat>().also { m ->
        runCatching { json.decodeFromString<Map<String, Stat>>(file.readText()).forEach { (k, v) -> m[k] = v } }
    }

    private fun slot(now: Long): Pair<Int, Int> {
        val t = Instant.ofEpochMilli(now).atZone(AppSettings.ZONE)
        return t.dayOfWeek.value to t.hour / 2
    }
    private fun key(c: Channel, dow: Int, h: Int) = c.sourceId + "|" + Text.norm(c.group).trim() + "|" + dow + "|" + h
    private fun decayed(s: Stat, now: Long) = s.count * Math.pow(0.5, ((now - s.at).coerceAtLeast(0)) / (14.0 * 86_400_000.0))

    fun record(c: Channel, now: Long = System.currentTimeMillis()) {
        val (d, h) = slot(now)
        val k = key(c, d, h)
        val old = data[k]
        data[k] = Stat((if (old == null) 0.0 else decayed(old, now)) + 1.0, now)
        if (data.size > 600) data.entries.minByOrNull { it.value.at }?.let { data.remove(it.key) }
        runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString<Map<String, Stat>>(data.toMap())) }
    }

    /** True when this channel's category has stalled repeatedly at about this time of the week. */
    fun troubled(c: Channel, now: Long = System.currentTimeMillis()): Boolean {
        val (d, h) = slot(now)
        val total = (-1..1).sumOf { dh -> data[key(c, d, (h + dh).mod(12))]?.let { decayed(it, now) } ?: 0.0 }
        return total >= 4.0
    }
}
