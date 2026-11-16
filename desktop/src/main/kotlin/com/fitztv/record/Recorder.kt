package com.fitztv.record

import android.content.Context
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.data.Game
import com.fitztv.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class Recording(
    val id: String,
    val title: String,
    val sport: String,
    val file: String,
    val startMillis: Long,
    val endMillis: Long,
    /** scheduled / recording / done / failed */
    val status: String,
    val sizeBytes: Long = 0,
    val gameId: String? = null,
)

/**
 * DVR: records a live stream to the device (app storage, no permissions needed) using a
 * coroutine while the app is open (closing the app stops a recording). MPEG-TS streams are
 * written straight to disk; HLS streams are recorded segment by segment into one .ts file.
 * Each recording uses one connection to your IPTV provider while it runs.
 */
object Recorder {
    private val json = Json { ignoreUnknownKeys = true }
    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings
    private lateinit var index: File
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun init(context: Context) {
        index = File(context.filesDir, "recordings.json")
        _recordings.value = runCatching { json.decodeFromString<List<Recording>>(index.readText()) }.getOrDefault(emptyList())
            .map { if (it.status == "recording") it.copy(status = "failed") else it }
    }

    fun dir(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

    @Synchronized
    private fun save(update: (List<Recording>) -> List<Recording>) {
        _recordings.value = update(_recordings.value)
        runCatching { index.writeText(json.encodeToString(_recordings.value)) }
    }

    fun update(id: String, f: (Recording) -> Recording) = save { l -> l.map { if (it.id == id) f(it) else it } }

    /** Record [channels] (best first) from [startMs] (now if in the past) until [endMs]. */
    fun schedule(context: Context, title: String, sport: String, channels: List<Channel>, startMs: Long, endMs: Long, gameId: String? = null): Recording {
        val id = "rec-" + System.currentTimeMillis()
        val safe = title.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifBlank { "Recording" }
        val file = File(dir(context), "$safe ${java.text.SimpleDateFormat("yyyy-MM-dd HHmm", java.util.Locale.US).format(java.util.Date(startMs))}.ts")
        val rec = Recording(id, title, sport, file.absolutePath, startMs, endMs, "scheduled", gameId = gameId)
        save { it + rec }
        val urls = channels.take(5).map { recordUrl(it) }
        jobs[id] = FitzApp.instance.scope.launch {
            delay((startMs - System.currentTimeMillis()).coerceAtLeast(0))
            record(id, urls) { true }
        }
        return rec
    }

    /** Record a game: from 5 minutes before the start until a typical game length after. */
    fun scheduleGame(context: Context, game: Game, channels: List<Channel>): Recording {
        val start = maxOf(System.currentTimeMillis(), game.startUtcMillis - 5 * 60_000)
        val hours = when (game.sport) { com.fitztv.sports.Sport.NFL -> 4.0; com.fitztv.sports.Sport.CRICKET -> 4.5; else -> 3.0 }
        return schedule(context, game.matchup, game.sport.name, channels, start, game.startUtcMillis + (hours * 3_600_000).toLong(), game.id)
    }

    fun stop(context: Context, id: String) {
        jobs.remove(id)?.cancel()
        update(id) { if (it.status == "scheduled") it.copy(status = "cancelled") else it.copy(status = "done") }
    }

    fun delete(context: Context, id: String) {
        jobs.remove(id)?.cancel()
        _recordings.value.firstOrNull { it.id == id }?.let { runCatching { File(it.file).delete() } }
        save { l -> l.filter { it.id != id } }
    }

    /** Xtream channels record best as MPEG-TS (one simple stream). */
    private fun recordUrl(c: Channel): String {
        val u = c.streamUrl
        return if (u.contains("/live/") && u.endsWith(".m3u8")) u.removeSuffix(".m3u8") + ".ts" else u
    }

    // ---- the actual recording --------------------------------------------------------------

    internal suspend fun record(id: String, urls: List<String>, isActive: () -> Boolean) = withContext(Dispatchers.IO) {
        val rec = _recordings.value.firstOrNull { it.id == id } ?: return@withContext
        update(id) { it.copy(status = "recording") }
        val ua = FitzApp.instance.settings.value.userAgent
        val file = File(rec.file)
        var urlIndex = 0
        var failures = 0
        FileOutputStream(file, true).use { out ->
            while (isActive() && System.currentTimeMillis() < rec.endMillis && urlIndex < urls.size) {
                val url = urls[urlIndex]
                val ok = runCatching {
                    if (url.substringBefore('?').endsWith(".m3u8", true)) recordHls(url, ua, out, rec.endMillis, isActive)
                    else recordTs(url, ua, out, rec.endMillis, isActive)
                }.getOrElse { if (it is CancellationException) throw it; false }
                update(id) { it.copy(sizeBytes = file.length()) }
                if (!ok) {
                    failures++
                    if (failures >= 4) { failures = 0; urlIndex++ }   // try the next-best stream
                    delay(2_000)
                } else failures = 0
            }
        }
        update(id) { it.copy(status = if (file.length() > 1_000_000) "done" else "failed", sizeBytes = file.length()) }
    }

    /** Straight copy of a TS stream; returns false when the connection drops early. */
    private fun recordTs(url: String, ua: String, out: FileOutputStream, end: Long, active: () -> Boolean): Boolean {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        Http.bulk.newBuilder().readTimeout(30, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            if (!r.isSuccessful) return false
            val input = r.body!!.byteStream()
            val buf = ByteArray(188 * 1024)
            while (active() && System.currentTimeMillis() < end) {
                val n = input.read(buf)
                if (n < 0) return false
                out.write(buf, 0, n)
            }
        }
        return true
    }

    /** Polls an HLS playlist and appends each new segment (TS segments concatenate cleanly). */
    private suspend fun recordHls(url: String, ua: String, out: FileOutputStream, end: Long, active: () -> Boolean): Boolean {
        var media = url
        val seen = LinkedHashSet<String>()
        while (active() && System.currentTimeMillis() < end) {
            val text = Http.getString(media, ua)
            if (text.contains("#EXT-X-STREAM-INF")) {
                // Master playlist: pick the highest-bandwidth rendition.
                val lines = text.lines()
                var best = -1L; var bestUri: String? = null
                lines.forEachIndexed { i, l ->
                    if (l.startsWith("#EXT-X-STREAM-INF")) {
                        val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                        val uri = lines.getOrNull(i + 1)?.trim()
                        if (uri != null && bw > best) { best = bw; bestUri = uri }
                    }
                }
                media = resolve(media, bestUri ?: return false)
                continue
            }
            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6
            val segs = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { resolve(media, it.trim()) }
            for (s in segs) {
                if (!seen.add(s)) continue
                Http.get(s, ua, big = true).use { r -> r.body!!.byteStream().copyTo(out) }
            }
            while (seen.size > 200) seen.remove(seen.first())
            if (text.contains("#EXT-X-ENDLIST")) return true
            delay(target * 1000 / 2)
        }
        return true
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http")) ref else java.net.URI(base).resolve(ref).toString()
}
