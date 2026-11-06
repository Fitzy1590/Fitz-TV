package com.fitztv.sources

import com.fitztv.data.Channel
import com.fitztv.data.Http
import com.fitztv.data.Source
import com.fitztv.sports.Text
import java.io.BufferedReader

object M3uParser {
    private val attr = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val vodHints = listOf("/movie/", "/series/", ".mp4", ".mkv", ".avi")

    data class Result(val channels: List<Channel>, val epgUrl: String?)

    fun load(source: Source, ua: String): Result =
        Http.get(source.url.trim(), ua, big = true).use { resp ->
            parse(source, resp.body!!.charStream().buffered(64 * 1024))
        }

    fun parse(source: Source, reader: BufferedReader): Result {
        val out = ArrayList<Channel>()
        var epg: String? = null
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
                        if (sport != null) {
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
                            )
                        }
                        index++
                        attrs = emptyMap(); title = null; group = null
                    }
                }
            }
        }
        return Result(out, epg)
    }
}
