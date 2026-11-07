package com.fitztv.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.fitztv.ui.components.Palette
import com.fitztv.ui.components.Palettes

/** App colours from the selected theme (Settings › Theme). Purple = main accent, Magenta = second, Cyan = info. */
object Neon {
    var palette by mutableStateOf(Palettes.SCOREBOARD)

    val Bg get() = Color(palette.bg)
    val Bg2 get() = Color(palette.bg2)
    val Surface get() = Color(palette.surface)
    val SurfaceHi get() = Color(palette.surfaceHi)
    val Purple get() = Color(palette.accent)
    val PurpleSoft get() = Color(palette.accentSoft)
    val Magenta get() = Color(palette.accent2)
    val Cyan get() = Color(palette.info)
    val Live get() = Color(palette.live)
    val Text get() = Color(palette.text)
    val TextDim get() = Color(palette.textDim)
    val TextFaint get() = Color(palette.textFaint)
    val Line get() = Color(palette.line)

    /** Flat page background — no gradients. */
    val background: Brush get() = SolidColor(Bg)
    /** Kept for old call sites; now a flat neutral rule, not a gradient. */
    val glowBar: Brush get() = SolidColor(Line)
}

private fun scheme(p: Palette) = darkColorScheme(
    primary = Color(p.accent),
    onPrimary = Color(p.bg),
    primaryContainer = Color(p.surfaceHi),
    onPrimaryContainer = Color(p.text),
    secondary = Color(p.accent2),
    secondaryContainer = Color(p.accentSoft),
    onSecondaryContainer = Color(p.text),
    tertiary = Color(p.info),
    background = Color(p.bg),
    onBackground = Color(p.text),
    surface = Color(p.surface),
    onSurface = Color(p.text),
    surfaceVariant = Color(p.surfaceHi),
    onSurfaceVariant = Color(p.textDim),
    surfaceContainer = Color(p.bg2),
    surfaceContainerHigh = Color(p.surface),
    surfaceContainerHighest = Color(p.surfaceHi),
    outline = Color(p.line),
    error = Color(p.live),
)

@Composable
fun FitzMobileTheme(content: @Composable () -> Unit) {
    val p = Neon.palette
    val colors = remember(p) { scheme(p) }
    MaterialTheme(colorScheme = colors, content = content)
}
