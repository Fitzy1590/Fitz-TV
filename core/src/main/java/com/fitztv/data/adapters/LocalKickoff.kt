package com.fitztv.data.adapters

import com.fitztv.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Kick-off times in the viewer's own zone. The zone comes from WorldTimeAPI (by IP) when it
 * answers, otherwise from the device's system zone, so it never blocks or fails.
 */
object LocalKickoff {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var cached: ZoneId? = null

    /** The viewer's zone; blocking on first call (call off the main thread), then cached. */
    fun zone(): ZoneId = cached ?: lookup().also { cached = it }

    private fun lookup(): ZoneId = runCatching {
        val o = json.parseToJsonElement(Http.getString("https://worldtimeapi.org/api/ip", "FitzTV")) as JsonObject
        ZoneId.of((o["timezone"] as JsonPrimitive).contentOrNull!!)
    }.getOrElse { ZoneId.systemDefault() }

    /** UTC kick-off (epoch millis) as local date-time in [zone]. */
    fun local(utcMillis: Long, zone: ZoneId = zone()): ZonedDateTime = Instant.ofEpochMilli(utcMillis).atZone(zone)
}
