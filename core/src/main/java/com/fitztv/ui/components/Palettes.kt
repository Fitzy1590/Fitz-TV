package com.fitztv.ui.components

/**
 * App colour themes (ARGB). Both apps and the screensaver build their colours from these.
 * accent = main highlight, accent2 = secondary highlight, info = times / links.
 */
data class Palette(
    val id: String,
    val name: String,
    val description: String,
    val bg: Long, val bg2: Long, val surface: Long, val surfaceHi: Long,
    val accent: Long, val accentSoft: Long, val accent2: Long, val info: Long, val live: Long,
    val text: Long, val textDim: Long, val textFaint: Long,
)

object Palettes {
    val MIDNIGHT = Palette(
        "midnight", "Midnight", "Deep navy with electric blue — calm and easy on the eyes (default)",
        0xFF060A14, 0xFF0B1324, 0xFF111B30, 0xFF1A2742,
        0xFF3B82F6, 0xFF1E3A6E, 0xFF22D3EE, 0xFF7DD3FC, 0xFFEF4444,
        0xFFEFF4FF, 0xFFA9B6CF, 0xFF6B7891,
    )
    val GRAPHITE = Palette(
        "graphite", "Graphite", "Neutral charcoal with a teal accent — minimal, lets the sport stand out",
        0xFF0C0D0F, 0xFF141619, 0xFF1B1E22, 0xFF262A30,
        0xFF14B8A6, 0xFF0F4F49, 0xFFF59E0B, 0xFF5EEAD4, 0xFFF43F5E,
        0xFFF2F4F5, 0xFFB0B6BD, 0xFF6E757D,
    )
    val STADIUM = Palette(
        "stadium", "Stadium", "Night-game green with floodlight white",
        0xFF050C08, 0xFF0A1710, 0xFF0F2117, 0xFF173021,
        0xFF22C55E, 0xFF14532D, 0xFFFACC15, 0xFF86EFAC, 0xFFEF4444,
        0xFFF0FDF4, 0xFFA7C4B2, 0xFF66806F,
    )
    val SUNSET = Palette(
        "sunset", "Sunset", "Warm dark with orange and gold",
        0xFF0E0806, 0xFF1A0F0A, 0xFF24150D, 0xFF332015,
        0xFFF97316, 0xFF7C2D12, 0xFFFBBF24, 0xFFFDBA74, 0xFFEF4444,
        0xFFFFF7ED, 0xFFD6B8A3, 0xFF8C7363,
    )
    val NEON = Palette(
        "neon", "Neon Purple", "The original neon purple look",
        0xFF07030F, 0xFF12061F, 0xFF170B2B, 0xFF24113F,
        0xFFB026FF, 0xFF7B2CBF, 0xFFFF2BD6, 0xFF3DF2FF, 0xFFFF2E63,
        0xFFF5EEFF, 0xFFB9A6D9, 0xFF7D6A9E,
    )
    val all = listOf(MIDNIGHT, GRAPHITE, STADIUM, SUNSET, NEON)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: MIDNIGHT
}
