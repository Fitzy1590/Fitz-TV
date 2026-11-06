package com.fitztv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object Neon {
    val Bg = Color(0xFF07030F)
    val Bg2 = Color(0xFF12061F)
    val Surface = Color(0xFF170B2B)
    val SurfaceHi = Color(0xFF24113F)
    val Purple = Color(0xFFB026FF)
    val PurpleSoft = Color(0xFF7B2CBF)
    val Magenta = Color(0xFFFF2BD6)
    val Cyan = Color(0xFF3DF2FF)
    val Live = Color(0xFFFF2E63)
    val Text = Color(0xFFF5EEFF)
    val TextDim = Color(0xFFB9A6D9)
    val TextFaint = Color(0xFF7D6A9E)

    val background = Brush.verticalGradient(listOf(Bg2, Bg, Bg))
    val glowBar = Brush.horizontalGradient(listOf(Purple, Magenta))
}

private val scheme = darkColorScheme(
    primary = Neon.Purple,
    onPrimary = Color.White,
    secondary = Neon.Magenta,
    tertiary = Neon.Cyan,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Surface,
    onSurface = Neon.Text,
    surfaceVariant = Neon.SurfaceHi,
    onSurfaceVariant = Neon.TextDim,
    border = Neon.Purple,
    error = Neon.Live,
)

@Composable
fun FitzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
