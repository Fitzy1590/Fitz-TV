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
import com.fitztv.ui.components.Palette
import com.fitztv.ui.components.Palettes

/** App colours from the selected theme (Settings › Theme). Purple = main accent, Magenta = second, Cyan = info. */
object Neon {
    var palette by mutableStateOf(Palettes.MIDNIGHT)

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
    val background get() = Brush.verticalGradient(listOf(Bg2, Bg, Bg))
    val glowBar get() = Brush.horizontalGradient(listOf(Purple, Magenta))
}

private fun scheme(p: Palette) = darkColorScheme(
    primary = Color(p.accent),
    onPrimary = Color.White,
    primaryContainer = Color(p.surfaceHi),
    onPrimaryContainer = Color(p.text),
    secondary = Color(p.accent2),
    secondaryContainer = Color(p.accentSoft),
    onSecondaryContainer = Color.White,
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
    outline = Color(p.accentSoft),
    error = Color(p.live),
)

@Composable
fun FitzMobileTheme(content: @Composable () -> Unit) {
    val p = Neon.palette
    val colors = remember(p) { scheme(p) }
    MaterialTheme(colorScheme = colors, content = content)
}
