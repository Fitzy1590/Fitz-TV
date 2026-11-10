package com.fitztv.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Builds provider catch-up (TV archive) URLs, used for "play from start", rewinding past the
 * live window and full-game replays.
 */
object Catchup {
    private val xtreamUrl = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.(\w+))?$""")
    private val xcTime = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")

    /** Archive URL starting at [startMs] for [durationMin] minutes, or null if unsupported. */
    fun url(ch: Channel, startMs: Long, durationMin: Int): String? {
        if (!ch.hasCatchup || ch.vod) return null
        val type = ch.catchup?.lowercase()
        return when {
            type == null || type == "xc" -> xtream(ch, startMs, durationMin)
            type == "append" -> ch.catchupSource?.let { ch.streamUrl + fill(it, startMs, durationMin) }
            type == "default" -> ch.catchupSource?.let { fill(it, startMs, durationMin) } ?: xtream(ch, startMs, durationMin)
            type == "shift" || type == "timeshift" -> {
                val sep = if ('?' in ch.streamUrl) "&" else "?"
                ch.streamUrl + sep + "utc=${startMs / 1000}&lutc=${System.currentTimeMillis() / 1000}"
            }
            type == "flussonic" || type == "fs" -> {
                // http://host/channel/index.m3u8 -> http://host/channel/index-START-DURATION.m3u8
                val u = ch.streamUrl.substringBefore('?')
                val base = u.substringBeforeLast('/')
                val file = u.substringAfterLast('/')
                val ext = file.substringAfterLast('.', "m3u8")
                "$base/${file.substringBeforeLast('.')}-${startMs / 1000}-${durationMin * 60}.$ext"
            }
            else -> xtream(ch, startMs, durationMin)
        }
    }

    private fun xtream(ch: Channel, startMs: Long, durationMin: Int): String? {
        val m = xtreamUrl.find(ch.streamUrl.substringBefore('?')) ?: return null
        val (base, user, pass, id) = m.destructured
        val zone = ch.serverTz?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        val start = Instant.ofEpochMilli(startMs).atZone(zone).format(xcTime)
        return "$base/timeshift/$user/$pass/$durationMin/$start/$id.ts"
    }

    /** Fills the common catch-up placeholders ({utc}, ${start}, {Y}-{m}-{d}, {duration} …). */
    private fun fill(template: String, startMs: Long, durationMin: Int): String {
        val start = startMs / 1000
        val end = start + durationMin * 60
        val now = System.currentTimeMillis() / 1000
        val t = Instant.ofEpochSecond(start).atZone(ZoneOffset.UTC)
        return template
            .replace("{utc}", "$start").replace("\${start}", "$start").replace("{start}", "$start")
            .replace("{utcend}", "$end").replace("\${end}", "$end").replace("{end}", "$end")
            .replace("{lutc}", "$now").replace("\${now}", "$now").replace("\${timestamp}", "$now")
            .replace("{duration}", "${durationMin * 60}").replace("\${duration}", "${durationMin * 60}")
            .replace("{Y}", "%04d".format(t.year)).replace("{m}", "%02d".format(t.monthValue)).replace("{d}", "%02d".format(t.dayOfMonth))
            .replace("{H}", "%02d".format(t.hour)).replace("{M}", "%02d".format(t.minute)).replace("{S}", "%02d".format(t.second))
    }
}
