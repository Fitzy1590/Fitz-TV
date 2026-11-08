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
data class StreamInfo(
    val height: Int = 0,
    val fps: Float = 0f,
    val checkedAt: Long = 0,
    val bitrateKbps: Int = 0,
    val codec: String = "",
    /** Playback history on this device: times watched and stalls (rebuffers / freezes). */
    val plays: Int = 0,
    val stalls: Int = 0,
)

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
        val old = known[channel.id] ?: StreamInfo()
        val now = System.currentTimeMillis()
        // Keep the stream's ceiling: adaptive HLS starts low and steps down when the network dips,
        // so a recent higher reading is the truer one.
        val h = if (now - old.checkedAt < 12 * 3_600_000L) maxOf(old.height, height) else height
        known[channel.id] = old.copy(height = h, fps = if (fps > 0) maxOf(fps, if (h == old.height) old.fps else 0f) else old.fps, checkedAt = now)
        failures.remove(channel.id)
    }

    /** Bitrate reported by the player (e.g. the HLS rendition actually playing). */
    fun recordBitrate(channel: Channel, kbps: Int) {
        if (kbps <= 0) return
        known[channel.id] = (known[channel.id] ?: StreamInfo()).copy(bitrateKbps = kbps)
    }

    fun recordPlay(channel: Channel) { known[channel.id] = (known[channel.id] ?: StreamInfo()).let { it.copy(plays = it.plays + 1) } }
    fun recordStall(channel: Channel) { known[channel.id] = (known[channel.id] ?: StreamInfo()).let { it.copy(stalls = it.stalls + 1) } }

    /** 0 = reliable, 1 = some stalls, 2 = stalls a lot (per time watched on this device). */
    fun stallClass(c: Channel): Int {
        val k = known[c.id] ?: return 0
        if (k.plays < 2) return 0
        val rate = k.stalls.toFloat() / k.plays
        return when { rate >= 3f -> 2; rate >= 1f -> 1; else -> 0 }
    }

    /** Bitrate weighted by codec efficiency (HEVC ≈ 1.6× H.264, MPEG-2 ≈ 0.5×). */
    fun effectiveKbps(c: Channel): Int {
        val k = known[c.id] ?: return 0
        val factor = when (k.codec) { "hevc" -> 1.6; "mpeg2" -> 0.5; else -> 1.0 }
        return (k.bitrateKbps * factor).toInt()
    }

    fun markFailed(channel: Channel) { failures[channel.id] = System.currentTimeMillis() }

    /** Failed in the last 10 minutes. */
    fun recentlyFailed(channel: Channel) =
        failures[channel.id]?.let { System.currentTimeMillis() - it < 10 * 60_000 } ?: false

    /** Measured height, else the name's quality tag, else 0 (unknown — no tag, never played). */
    fun height(c: Channel): Int = known[c.id]?.height?.takeIf { it > 0 }
        ?: com.fitztv.sports.Text.qualityTag(c.name)?.let { when (it) { 4 -> 2160; 3 -> 1080; 2 -> 720; else -> 480 } }
        ?: 0

    /** 3 = 4K, 2 = 1080p, 1 = 720p, 0 = SD. Measured height when we have it, else the name's tag. */
    fun resClass(c: Channel): Int = height(c).let { h -> when { h == 0 -> -1; h >= 2000 -> 3; h >= 1000 -> 2; h >= 700 -> 1; else -> 0 } }

    /**
     * Sort key for resolution: 4K 6, 1080p 4, unknown 3, 720p 2, SD 0. An untagged, unmeasured
     * stream sits between 720p and 1080p instead of being assumed 720p.
     */
    fun rank(c: Channel): Int = when (resClass(c)) { 3 -> 6; 2 -> 4; 1 -> 2; 0 -> 0; else -> 3 }

    /** Unknown frame rate counts as 30 so a confirmed 50/60 fps stream beats it. */
    fun fps(c: Channel): Float = known[c.id]?.fps?.takeIf { it > 0 } ?: fpsHint(c.name) ?: 30f

    private val fpsRegex = Regex("""(\d{2,3})\s?fps|\b(50|60)p\b""", RegexOption.IGNORE_CASE)
    private fun fpsHint(name: String): Float? =
        fpsRegex.find(name)?.let { m -> (m.groupValues[1].ifBlank { m.groupValues[2] }).toFloatOrNull() }

    /** Reads HLS master playlists for streams we know nothing about (in parallel, ~2.5s max). */
    /** Quick check at selection time: HLS master playlists only (a few KB, no video). */
    suspend fun probe(channels: List<Channel>, userAgent: String) = withContext(Dispatchers.IO) {
        val todo = channels.filter { needsProbe(it) && it.streamUrl.substringBefore('?').endsWith(".m3u8", true) && !it.streamUrl.contains("/live/") }.take(6)
        if (todo.isEmpty()) return@withContext
        withTimeoutOrNull(2_000) { coroutineScope { todo.map { c -> async { masterOnly(c, userAgent) } }.awaitAll() } }
    }

    private fun masterOnly(c: Channel, ua: String) {
        runCatching {
            probeClient.newCall(Request.Builder().url(c.streamUrl).header("User-Agent", ua).build()).execute().use { resp ->
                if (!resp.isSuccessful) return
                val text = resp.body?.string()?.take(65_536) ?: return
                var bestH = 0; var bestFps = 0f
                text.lineSequence().filter { it.startsWith("#EXT-X-STREAM-INF") }.forEach { line ->
                    val h = Regex("""RESOLUTION=\d+x(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val f = Regex("""FRAME-RATE=([\d.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    if (h > bestH || (h == bestH && f > bestFps)) { bestH = h; bestFps = f }
                }
                if (bestH > 0) store(c, bestH, bestFps)
            }
        }
    }

    /** True if this stream's resolution was actually measured (not guessed from its name). */
    fun isMeasured(c: Channel) = (known[c.id]?.height ?: 0) > 0

    /** "1080p • 50fps", or "~720p" when only estimated from the channel name. */
    fun label(c: Channel): String {
        val h = height(c)
        val f = fps(c)
        val res = com.fitztv.ui.components.Fmt.resolution(h)
        val k = known[c.id]
        val mbps = k?.bitrateKbps?.takeIf { it > 0 }?.let { " • %.1f Mbps".format(it / 1000f) } ?: ""
        val codec = if (k?.codec == "hevc") " • HEVC" else ""
        return if (isMeasured(c)) res + (if (k!!.fps > 0) " • ${f.roundToIntSafe()}fps" else "") + mbps + codec
        else if (h == 0) "Not checked" else "~$res"
    }

    private fun Float.roundToIntSafe() = kotlin.math.round(this).toInt()

    fun needsProbe(c: Channel): Boolean {
        val k = known[c.id] ?: return true
        return k.checkedAt < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
    }

    /**
     * Measures one stream: HLS master playlists give RESOLUTION / FRAME-RATE directly;
     * otherwise (MPEG-TS, or an HLS media playlist) the first part of the video is read and
     * its real resolution / frame rate decoded. Uses one provider connection for ~2 s.
     */
    /** The measuring request in progress (cancelled the moment a player starts). */
    @Volatile private var inFlight: okhttp3.Call? = null

    /** Free the provider connection immediately (a stream is about to play). */
    fun cancelInFlight() { inFlight?.cancel(); inFlight = null }

    fun probeOne(c: Channel, ua: String) {
        runCatching {
            val req = Request.Builder().url(c.streamUrl).header("User-Agent", ua).build()
            val call = sniffClient.newCall(req).also { inFlight = it }
            call.execute().use { resp ->
                if (!resp.isSuccessful) return
                val isHls = c.streamUrl.substringBefore('?').endsWith(".m3u8", true) ||
                    resp.header("Content-Type").orEmpty().contains("mpegurl", true)
                if (!isHls) {
                    TsSniffer.sniff(resp.body!!.byteStream())?.let { r -> store(c, r.height, r.fps, r.bitrateKbps, r.codec) }
                    return
                }
                val text = resp.body?.source()?.let { src -> src.request(65_536); src.buffer.readUtf8(minOf(src.buffer.size, 65_536)) } ?: return
                var bestH = 0; var bestFps = 0f; var bestBw = 0; var bestCodec = ""
                text.lineSequence().filter { it.startsWith("#EXT-X-STREAM-INF") }.forEach { line ->
                    val h = Regex("""RESOLUTION=\d+x(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val f = Regex("""FRAME-RATE=([\d.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    val bw = Regex("""[^-]BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()?.div(1000)?.toInt() ?: 0
                    if (h > bestH || (h == bestH && (f > bestFps || (f == bestFps && bw > bestBw)))) {
                        bestH = h; bestFps = f; bestBw = bw
                        bestCodec = if (line.contains("hvc1") || line.contains("hev1")) "hevc" else "h264"
                    }
                }
                if (bestH > 0) { store(c, bestH, bestFps, bestBw, bestCodec); return }
                // Media playlist: sniff the first segment.
                val seg = text.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: return
                val segUrl = if (seg.startsWith("http")) seg else java.net.URI(resp.request.url.toString()).resolve(seg).toString()
                val segCall = sniffClient.newCall(Request.Builder().url(segUrl).header("User-Agent", ua).build()).also { inFlight = it }
                segCall.execute().use { s ->
                    if (s.isSuccessful) TsSniffer.sniff(s.body!!.byteStream())?.let { r -> store(c, r.height, r.fps, r.bitrateKbps, r.codec) }
                }
            }
        }
    }

    private fun store(c: Channel, h: Int, fps: Float, kbps: Int = 0, codec: String = "") {
        inFlight = null
        val old = known[c.id] ?: StreamInfo()
        known[c.id] = old.copy(
            height = if (h > 0) h else old.height, fps = if (fps > 0) fps else old.fps, checkedAt = System.currentTimeMillis(),
            bitrateKbps = if (kbps > 0) kbps else old.bitrateKbps, codec = codec.ifBlank { old.codec },
        )
    }

    private val sniffClient = Http.client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
}
