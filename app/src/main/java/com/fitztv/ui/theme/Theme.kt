package com.fitztv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.fitztv.ui.components.Palette
import com.fitztv.ui.components.Palettes

/**
 * App colours, read from the selected theme (Settings › Theme). The names are kept from the
 * original neon look: Purple = main accent, Magenta = second accent, Cyan = times / info.
 */
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
    secondary = Color(p.accent2),
    tertiary = Color(p.info),
    background = Color(p.bg),
    onBackground = Color(p.text),
    surface = Color(p.surface),
    onSurface = Color(p.text),
    surfaceVariant = Color(p.surfaceHi),
    onSurfaceVariant = Color(p.textDim),
    border = Color(p.accent),
    error = Color(p.live),
)

@Composable
fun FitzTheme(content: @Composable () -> Unit) {
    val p = Neon.palette
    val colors = remember(p) { scheme(p) }
    MaterialTheme(colorScheme = colors, content = content)
}
