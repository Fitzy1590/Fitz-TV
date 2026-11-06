package com.fitztv.sources

import com.fitztv.data.Channel
import com.fitztv.data.Http
import com.fitztv.data.Source
import com.fitztv.sports.Text
import java.io.BufferedReader

object M3uParser {
    private val attr = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val vodHints = listOf("/movie/", "/series/", ".mp4", ".mkv", ".avi")

    data class Result(val channels: List<Channel>, val epgUrl: String?, val vod: List<com.fitztv.data.VodItem> = emptyList())

    fun load(source: Source, ua: String): Result =
        Http.get(source.url.trim(), ua, big = true).use { resp ->
            parse(source, resp.body!!.charStream().buffered(64 * 1024))
        }

    fun parse(source: Source, reader: BufferedReader): Result {
        val out = ArrayList<Channel>()
        val vod = ArrayList<com.fitztv.data.VodItem>()
        var epg: String? = null
        var headerCatchup: String? = null
        var headerCatchupSource: String? = null
        var headerCatchupDays = 0
        var attrs: Map<String, String> = emptyMap()
        var title: String? = null
        var group: String? = null
        var index = 0
        reader.useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTM3U") -> {
                        val a = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        epg = a["url-tvg"] ?: a["x-tvg-url"]
                        epg = epg?.split(',')?.firstOrNull()?.trim()
                        headerCatchup = a["catchup"]
                        headerCatchupSource = a["catchup-source"]
                        headerCatchupDays = a["catchup-days"]?.toIntOrNull() ?: 0
                    }
                    line.startsWith("#EXTINF") -> {
                        attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        title = line.substringAfterLast("\",", "").ifBlank { line.substringAfter(',', "") }.trim()
                        group = attrs["group-title"]
                    }
                    line.startsWith("#EXTGRP:") -> group = line.removePrefix("#EXTGRP:").trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        val name = (title ?: attrs["tvg-name"]).orEmpty().ifBlank { attrs["tvg-name"].orEmpty() }
                        val g = group.orEmpty()
                        val isVod = vodHints.any { line.contains(it, ignoreCase = true) }
                        val sport = if (name.isBlank() || isVod) null else Text.classify(name, g)
                        if (isVod && name.isNotBlank()) {
                            Text.classify(name, g)?.let { vs ->
                                vod += com.fitztv.data.VodItem(
                                    id = "${source.id}:vod:$index", title = name, category = g, sport = vs,
                                    url = line, poster = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                                )
                            }
                        }
                        if (sport != null) {
                            val cu = attrs["catchup"] ?: attrs["catchup-type"] ?: headerCatchup
                            val cuDays = attrs["catchup-days"]?.toIntOrNull() ?: attrs["tvg-rec"]?.toIntOrNull()
                                ?: attrs["timeshift"]?.toIntOrNull() ?: if (cu != null) headerCatchupDays.coerceAtLeast(1) else 0
                            out += Channel(
                                id = "${source.id}:${index}",
                                sourceId = source.id,
                                name = name,
                                group = g,
                                logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                                streamUrl = line,
                                epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                                sport = sport,
                                quality = Text.quality(name),
                                variantKey = Text.variantKey(name),
                                archiveDays = if (cu != null || cuDays > 0) cuDays.coerceAtLeast(1) else 0,
                                catchup = cu ?: if (cuDays > 0 && line.contains("/live/")) "xc" else null,
                                catchupSource = attrs["catchup-source"] ?: headerCatchupSource,
                            )
                        }
                        index++
                        attrs = emptyMap(); title = null; group = null
                    }
                }
            }
        }
        return Result(out, epg, vod)
    }
}
