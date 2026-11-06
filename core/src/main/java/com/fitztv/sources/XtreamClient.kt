package com.fitztv.sources

import com.fitztv.data.Channel
import com.fitztv.data.Http
import com.fitztv.data.Source
import com.fitztv.data.XtreamFormat
import com.fitztv.sports.Text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/** Xtream Codes "player_api.php" client (live TV only). */
object XtreamClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun base(source: Source) = source.url.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private fun api(source: Source, action: String? = null): String {
        val u = URLEncoder.encode(source.username, "UTF-8")
        val p = URLEncoder.encode(source.password, "UTF-8")
        return "${base(source)}/player_api.php?username=$u&password=$p" + (action?.let { "&action=$it" } ?: "")
    }

    fun epgUrl(source: Source): String {
        if (source.epgUrl.isNotBlank()) return source.epgUrl
        val u = URLEncoder.encode(source.username, "UTF-8")
        val p = URLEncoder.encode(source.password, "UTF-8")
        return "${base(source)}/xmltv.php?username=$u&password=$p"
    }

    /** Verifies the login. Returns an error message, or null when the account is usable. */
    fun verify(source: Source, ua: String): String? = runCatching {
        val obj = json.parseToJsonElement(Http.getString(api(source), ua)).jsonObject
        val info = obj["user_info"]?.jsonObject ?: return "Unexpected server response"
        if (info.str("auth") == "0") return "Login rejected"
        val status = info.str("status")
        if (status != null && !status.equals("Active", true)) "Account status: $status" else null
    }.getOrElse { "Could not reach server (${it.message})" }

    fun loadSportsChannels(source: Source, ua: String, format: XtreamFormat): List<Channel> {
        val cats = json.parseToJsonElement(Http.getString(api(source, "get_live_categories"), ua, big = true))
            .let { it as? JsonArray ?: JsonArray(emptyList()) }
            .associate { it.jsonObject.str("category_id").orEmpty() to it.jsonObject.str("category_name").orEmpty() }

        val streams = json.parseToJsonElement(Http.getString(api(source, "get_live_streams"), ua, big = true)).jsonArray
        val info = serverInfo(source, ua)
        val ext = when (format) {
            XtreamFormat.HLS -> "m3u8"
            XtreamFormat.TS -> "ts"
            XtreamFormat.AUTO -> if (info.allowsHls) "m3u8" else "ts"
        }
        val u = source.username
        val p = source.password
        val out = ArrayList<Channel>()
        for (el in streams) {
            val o = el as? JsonObject ?: continue
            val name = o.str("name")?.trim().orEmpty()
            if (name.isEmpty()) continue
            val group = cats[o.str("category_id")].orEmpty()
            val sport = Text.classify(name, group) ?: continue
            val sid = o.str("stream_id") ?: continue
            out += Channel(
                id = "${source.id}:$sid",
                sourceId = source.id,
                name = name,
                group = group,
                logo = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                streamUrl = "${base(source)}/live/$u/$p/$sid.$ext",
                epgId = o.str("epg_channel_id")?.takeIf { it.isNotBlank() },
                sport = sport,
                quality = Text.quality(name),
                variantKey = Text.variantKey(name),
                archiveDays = if (o.str("tv_archive") == "1") (o.str("tv_archive_duration")?.toIntOrNull() ?: 1).coerceAtLeast(1) else 0,
                serverTz = info.timezone,
            )
        }
        return out
    }

    data class ServerInfo(val allowsHls: Boolean, val timezone: String?)

    /**
     * user_info.allowed_output_formats (HLS segments survive network blips better than one
     * long TS connection) and server_info.timezone (needed for catch-up URLs).
     */
    private fun serverInfo(source: Source, ua: String): ServerInfo = runCatching {
        val root = json.parseToJsonElement(Http.getString(api(source), ua)).jsonObject
        val info = root["user_info"]?.jsonObject
        val formats = (info?.get("allowed_output_formats") as? JsonArray)?.map { (it as JsonPrimitive).content }
        ServerInfo(
            allowsHls = formats == null || formats.isEmpty() || formats.any { it.equals("m3u8", true) },
            timezone = root["server_info"]?.jsonObject?.str("timezone"),
        )
    }.getOrDefault(ServerInfo(true, null))

    // ---- VOD (replays) ---------------------------------------------------------------

    /** Sport replay videos from the provider's VOD library (sport-looking categories only). */
    fun loadSportsVod(source: Source, ua: String): List<com.fitztv.data.VodItem> {
        val cats = json.parseToJsonElement(Http.getString(api(source, "get_vod_categories"), ua, big = true))
            .let { it as? JsonArray ?: JsonArray(emptyList()) }
            .mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o.str("category_name").orEmpty()
                val sport = Text.classify(name, "") ?: return@mapNotNull null
                Triple(o.str("category_id") ?: return@mapNotNull null, name, sport)
            }.take(40)
        val u = source.username
        val p = source.password
        return cats.flatMap { (id, catName, sport) ->
            runCatching {
                json.parseToJsonElement(Http.getString(api(source, "get_vod_streams") + "&category_id=$id", ua, big = true)).jsonArray
                    .mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val sid = o.str("stream_id") ?: return@mapNotNull null
                        val ext = o.str("container_extension") ?: "mp4"
                        com.fitztv.data.VodItem(
                            id = "${source.id}:vod:$sid",
                            title = o.str("name")?.trim().orEmpty(),
                            category = catName,
                            sport = Text.classify(o.str("name").orEmpty(), catName) ?: sport,
                            url = "${base(source)}/movie/$u/$p/$sid.$ext",
                            poster = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                            addedMillis = (o.str("added")?.toLongOrNull() ?: 0L) * 1000,
                        )
                    }
            }.getOrDefault(emptyList())
        }.filter { it.title.isNotBlank() }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
}
