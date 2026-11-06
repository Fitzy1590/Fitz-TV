package com.fitztv.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
    primaryContainer = Neon.SurfaceHi,
    onPrimaryContainer = Neon.Text,
    secondary = Neon.Magenta,
    secondaryContainer = Neon.PurpleSoft,
    onSecondaryContainer = Color.White,
    tertiary = Neon.Cyan,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Surface,
    onSurface = Neon.Text,
    surfaceVariant = Neon.SurfaceHi,
    onSurfaceVariant = Neon.TextDim,
    surfaceContainer = Neon.Bg2,
    surfaceContainerHigh = Neon.Surface,
    surfaceContainerHighest = Neon.SurfaceHi,
    outline = Neon.PurpleSoft,
    error = Neon.Live,
)

@Composable
fun FitzMobileTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
