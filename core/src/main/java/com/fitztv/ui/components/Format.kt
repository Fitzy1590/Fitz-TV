package com.fitztv.ui.components

import com.fitztv.data.Game
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val t12 = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val t24 = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun time(millis: Long, zone: ZoneId, use24h: Boolean): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(if (use24h) t24 else t12)

    fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun dayLabel(date: LocalDate, zone: ZoneId): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayFmt)
        }
    }

    fun whenLabel(g: Game, zone: ZoneId, use24h: Boolean): String {
        val d = localDate(g.startUtcMillis, zone)
        val day = dayLabel(d, zone)
        return "$day • ${time(g.startUtcMillis, zone, use24h)}"
    }

    fun quality(q: Int) = when (q) { 4 -> "4K"; 3 -> "FHD"; 2 -> "HD"; else -> "SD" }

    fun resolution(h: Int) = when {
        h >= 2000 -> "4K"
        h >= 1000 -> "1080p"
        h >= 700 -> "720p"
        h > 0 -> "${h}p"
        else -> "—"
    }
}
