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
        val ext = if (format == XtreamFormat.HLS) "m3u8" else "ts"
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
            )
        }
        return out
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
}
