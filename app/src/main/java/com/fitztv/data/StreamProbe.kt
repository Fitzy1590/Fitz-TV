package com.fitztv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class StreamInfo(val height: Int = 0, val fps: Float = 0f, val checkedAt: Long = 0)

/**
 * Works out each stream's real resolution and frame rate so the best one is tried first:
 *  - HLS master playlists are read directly (RESOLUTION / FRAME-RATE tags, a few KB),
 *  - anything that has played before is remembered from the decoder's actual output,
 *  - otherwise the channel name's quality tags (4K / FHD / 50fps …) are used.
 * Results stay on the device.
 */
class StreamProbe(context: Context) {
    private val file = File(context.filesDir, "cache/streams.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val known = ConcurrentHashMap<String, StreamInfo>()
    /** channel id -> time it last failed to play. */
    private val failures = ConcurrentHashMap<String, Long>()
    private val probeClient = Http.client.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()

    init {
        runCatching { known.putAll(json.decodeFromString<Map<String, StreamInfo>>(file.readText())) }
    }

    fun save() {
        runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(known.toMap())) }
    }

    fun record(channel: Channel, height: Int, fps: Float) {
        if (height <= 0) return
        val old = known[channel.id]
        known[channel.id] = StreamInfo(height, if (fps > 0) fps else old?.fps ?: 0f, System.currentTimeMillis())
        failures.remove(channel.id)
    }

    fun markFailed(channel: Channel) { failures[channel.id] = System.currentTimeMillis() }

    /** Failed in the last 10 minutes. */
    fun recentlyFailed(channel: Channel) =
        failures[channel.id]?.let { System.currentTimeMillis() - it < 10 * 60_000 } ?: false

    fun height(c: Channel): Int = known[c.id]?.height?.takeIf { it > 0 } ?: when (c.quality) {
        4 -> 2160; 3 -> 1080; 2 -> 720; else -> 480
    }

    /** Unknown frame rate counts as 30 so a confirmed 50/60 fps stream beats it. */
    fun fps(c: Channel): Float = known[c.id]?.fps?.takeIf { it > 0 } ?: fpsHint(c.name) ?: 30f

    private val fpsRegex = Regex("""(\d{2,3})\s?fps|\b(50|60)p\b""", RegexOption.IGNORE_CASE)
    private fun fpsHint(name: String): Float? =
        fpsRegex.find(name)?.let { m -> (m.groupValues[1].ifBlank { m.groupValues[2] }).toFloatOrNull() }

    /** Reads HLS master playlists for streams we know nothing about (in parallel, ~2.5s max). */
    suspend fun probe(channels: List<Channel>, userAgent: String) = withContext(Dispatchers.IO) {
        val stale = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        val todo = channels.filter { c ->
            val k = known[c.id]
            (k == null || k.checkedAt < stale) && c.streamUrl.substringBefore('?').endsWith(".m3u8", true)
        }.take(16)
        if (todo.isEmpty()) return@withContext
        withTimeoutOrNull(2_500) {
            coroutineScope { todo.map { c -> async { probeOne(c, userAgent) } }.awaitAll() }
        }
    }

    private fun probeOne(c: Channel, ua: String) {
        runCatching {
            val req = Request.Builder().url(c.streamUrl).header("User-Agent", ua).build()
            probeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val text = resp.body?.source()?.let { src -> src.request(32_768); src.buffer.readUtf8(minOf(src.buffer.size, 32_768)) } ?: return
                var bestH = 0; var bestFps = 0f
                text.lineSequence().filter { it.startsWith("#EXT-X-STREAM-INF") }.forEach { line ->
                    val h = Regex("""RESOLUTION=\d+x(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val f = Regex("""FRAME-RATE=([\d.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    if (h > bestH || (h == bestH && f > bestFps)) { bestH = h; bestFps = f }
                }
                if (bestH > 0) known[c.id] = StreamInfo(bestH, bestFps, System.currentTimeMillis())
            }
        }
    }
}
