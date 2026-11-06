package com.fitztv.sources

import android.util.Xml
import com.fitztv.data.EpgProgram
import com.fitztv.data.Http
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/** Streaming XMLTV parser that only keeps programmes for the channels we care about. */
object XmltvParser {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    private val fmtNoZone = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun load(url: String, ua: String, wantedIds: Set<String>, fromMillis: Long, toMillis: Long): List<EpgProgram> =
        Http.get(url, ua, big = true).use { resp ->
            parse(resp.body!!.byteStream(), wantedIds, fromMillis, toMillis)
        }

    fun parse(input: InputStream, wantedIds: Set<String>, from: Long, to: Long): List<EpgProgram> {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(2)
        val gz = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val stream = if (gz) GZIPInputStream(buffered, 64 * 1024) else buffered

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)
        val out = ArrayList<EpgProgram>()
        var chan: String? = null
        var start = 0L
        var stop = 0L
        var title = ""
        var desc = ""
        var keep = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        chan = parser.getAttributeValue(null, "channel")
                        start = time(parser.getAttributeValue(null, "start"))
                        stop = time(parser.getAttributeValue(null, "stop"))
                        keep = chan != null && chan in wantedIds && stop >= from && start <= to
                        title = ""; desc = ""
                    }
                    "title" -> if (keep && title.isEmpty()) title = parser.nextText().orEmpty()
                    "desc" -> if (keep && desc.isEmpty()) desc = parser.nextText().orEmpty().take(300)
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                if (keep && title.isNotBlank()) out += EpgProgram(chan!!, title.trim(), desc.trim(), start, stop)
                keep = false
            }
            event = parser.next()
        }
        return out
    }

    private fun time(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val t = s.trim()
        return runCatching { OffsetDateTime.parse(t, fmt).toInstant().toEpochMilli() }
            .recoverCatching {
                java.time.LocalDateTime.parse(t.take(14), fmtNoZone).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            }.getOrDefault(0)
    }
}
