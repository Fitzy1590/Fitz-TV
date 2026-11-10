package com.fitztv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Settings › Test connection: reads one of your sports streams for about 15 seconds, measures
 * the speed second by second, and says what that means (how many HD streams, buffer size,
 * Wi-Fi advice). Uses one provider connection while it runs.
 */
object ConnectionTest {
    suspend fun run(repo: Repository, settings: AppSettings): String = withContext(Dispatchers.IO) {
        val ch = repo.channels.value.firstOrNull { !it.vod && it.streamUrl.substringBefore('?').endsWith(".ts", true) }
            ?: repo.channels.value.firstOrNull { !it.vod }
            ?: return@withContext "No channels to test with yet. Add a source first."
        var url = ch.streamUrl
        // HLS: follow the playlist to the first media segments.
        val segments = ArrayDeque<String>()
        if (url.substringBefore('?').endsWith(".m3u8", true)) {
            runCatching {
                var text = Http.getString(url, settings.userAgent)
                var base = url.substringBeforeLast('/') + "/"
                val variant = text.lineSequence().dropWhile { !it.startsWith("#EXT-X-STREAM-INF") }.drop(1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                if (variant != null) {
                    val vurl = if (variant.startsWith("http")) variant else base + variant
                    text = Http.getString(vurl, settings.userAgent); base = vurl.substringBeforeLast('/') + "/"
                }
                text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { segments += if (it.startsWith("http")) it else base + it }
            }
            if (segments.isEmpty()) return@withContext "Couldn't read ${ch.label}'s playlist. The provider may be refusing connections."
        }
        val perSecond = mutableListOf<Double>()
        val started = System.currentTimeMillis()
        var bytesThisSecond = 0L; var secondStart = started
        fun feed(n: Int) {
            bytesThisSecond += n
            val now = System.currentTimeMillis()
            if (now - secondStart >= 1_000) { perSecond += bytesThisSecond * 8.0 / ((now - secondStart) / 1000.0) / 1e6; bytesThisSecond = 0; secondStart = now }
        }
        val result = runCatching {
            val urls = if (segments.isNotEmpty()) segments.toList() else listOf(url)
            val buf = ByteArray(64 * 1024)
            outer@ for (u in urls) {
                Http.client.newBuilder().readTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                    .newCall(Request.Builder().url(u).header("User-Agent", settings.userAgent).build()).execute().use { r ->
                        if (!r.isSuccessful) error("HTTP ${r.code}")
                        val src = r.body!!.byteStream()
                        while (System.currentTimeMillis() - started < 15_000) {
                            val n = src.read(buf); if (n < 0) break
                            feed(n)
                        }
                    }
                if (System.currentTimeMillis() - started >= 15_000) break@outer
            }
        }
        val err = result.exceptionOrNull()
        if (perSecond.size < 3) return@withContext "Test failed${err?.message?.let { ": $it" } ?: ""}. The provider refused or dropped the connection."
        val avg = perSecond.average(); val low = perSecond.minOrNull() ?: 0.0
        val hd = (avg / 8.0).toInt().coerceAtLeast(0)
        val limit = settings.sources.filter { it.type == SourceType.XTREAM }.mapNotNull { runCatching { com.fitztv.sources.XtreamClient.connections(it, settings.userAgent) }.getOrNull()?.first }.firstOrNull()
        buildString {
            append("Speed from your provider: %.0f Mbps average, %.0f Mbps at the slowest second. ".format(avg, low))
            append(if (hd >= 4) "Enough for about $hd HD streams at once. " else if (hd >= 1) "Enough for about $hd HD stream${if (hd > 1) "s" else ""} at once: keep multiview to $hd window${if (hd > 1) "s" else ""}. " else "Too slow for HD. ")
            if (low < avg / 3) append("The speed swings a lot, which causes brief drops: use Ethernet or the 5 GHz Wi-Fi band if you can. ")
            append("Suggested buffer: " + (if (low < avg / 3 || avg < 15) "45" else if (avg < 30) "30" else "20") + " seconds (Settings › Playback). ")
            if (limit != null) append("Your plan allows $limit streams at once.")
            if (err != null) append(" (Stopped early: ${err.message}.)")
        }.trim()
    }
}
