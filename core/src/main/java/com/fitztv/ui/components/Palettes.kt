package com.fitztv.ui.components

/**
 * App colour themes (ARGB). Both apps and the screensaver build their colours from these.
 *
 * Each theme has ONE accent (selection, primary action, the key number). The chrome stays
 * neutral; colour comes from team crests and the live red. [accent2] equals [accent] and
 * [info] is a light neutral, kept as fields so older call sites keep working.
 */
data class Palette(
    val id: String,
    val name: String,
    val description: String,
    val bg: Long, val bg2: Long, val surface: Long, val surfaceHi: Long,
    val accent: Long, val accentSoft: Long, val accent2: Long, val info: Long, val live: Long,
    val text: Long, val textDim: Long, val textFaint: Long,
    /** Fine outline on cards and the focus ring base. */
    val line: Long = 0xFF2A2D33,
)

object Palettes {
    // Ink: near-black, faintly warm neutrals shared by most themes.
    private const val INK_BG = 0xFF0B0C0E
    private const val INK_BG2 = 0xFF111215
    private const val INK_SURFACE = 0xFF17181C
    private const val INK_SURFACE_HI = 0xFF212328
    private const val INK_TEXT = 0xFFF2F1EE
    private const val INK_DIM = 0xFFA4A6AB
    private const val INK_FAINT = 0xFF6C6F76
    private const val INK_INFO = 0xFFCDCED2
    private const val LIVE = 0xFFE5484D

    private fun ink(id: String, name: String, desc: String, accent: Long, soft: Long) = Palette(
        id, name, desc, INK_BG, INK_BG2, INK_SURFACE, INK_SURFACE_HI,
        accent, soft, accent, INK_INFO, LIVE, INK_TEXT, INK_DIM, INK_FAINT,
    )

    val ESPORTS = Palette(
        "esports", "Esports teal", "Blue-black with one bright teal (default)",
        0xFF070B0F, 0xFF0C1218, 0xFF0F161D, 0xFF18222C,
        0xFF00E5C3, 0xFF0A3A34, 0xFF00E5C3, 0xFFC9D8D8, LIVE,
        0xFFE6F4F4, 0xFFA0B3B6, 0xFF64767A, 0xFF1E2A33,
    )
    val SCOREBOARD = ink("scoreboard", "Scoreboard", "Scoreboard amber on ink black", 0xFFF2B33D, 0xFF3D2F14)
    val MIDNIGHT = Palette(
        "midnight", "Midnight", "Deep navy with one clear blue",
        0xFF080B12, 0xFF0D121C, 0xFF131A27, 0xFF1C2535,
        0xFF5B9BF0, 0xFF1D3355, 0xFF5B9BF0, 0xFFC9D2E0, LIVE,
        0xFFEEF2F8, 0xFFA2ACBD, 0xFF677186, 0xFF263044,
    )
    val GRAPHITE = ink("graphite", "Graphite", "Charcoal with a teal accent", 0xFF2EC4B0, 0xFF103E38)
    val STADIUM = Palette(
        "stadium", "Stadium", "Night-game green with floodlight white",
        0xFF070B09, 0xFF0C130F, 0xFF121B16, 0xFF1A2620,
        0xFF46C277, 0xFF153D25, 0xFF46C277, 0xFFCDD6D0, LIVE,
        0xFFEFF5F1, 0xFFA2B3A8, 0xFF66776C, 0xFF24302A,
    )
    val SUNSET = ink("sunset", "Sunset", "Ink black with a warm orange", 0xFFF0793A, 0xFF4A2412)
    val NEON = Palette(
        "neon", "Violet night", "Deep purple-black with one violet",
        0xFF0D0B14, 0xFF131020, 0xFF181425, 0xFF221D36,
        0xFF8B5CF6, 0xFF2E2552, 0xFF8B5CF6, 0xFFD3CFE3, LIVE,
        0xFFF1EEFB, 0xFFA9A3BD, 0xFF6E6985, 0xFF2A2540,
    )
    val PITCH = ink("pitch", "Pitch green", "Ink black with a bright green accent", 0xFF3EE07F, 0xFF12402A)
    val all = listOf(ESPORTS, NEON, SCOREBOARD, PITCH, MIDNIGHT, GRAPHITE, STADIUM, SUNSET)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: ESPORTS
}
