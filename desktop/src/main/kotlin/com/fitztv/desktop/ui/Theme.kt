package com.fitztv.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.fitztv.ui.components.Palette
import com.fitztv.ui.components.Palettes

/** Saira (SIL Open Font License, third_party/OFL-Saira.txt): body and labels. */
val Saira = FontFamily(
    Font("saira_variable.ttf", FontWeight.Normal),
    Font("saira_variable.ttf", FontWeight.Medium),
    Font("saira_variable.ttf", FontWeight.SemiBold),
    Font("saira_variable.ttf", FontWeight.Bold),
)

/** Saira Condensed: scores, titles and section headings. */
val SairaCondensed = FontFamily(
    Font("saira_condensed_medium.ttf", FontWeight.Medium),
    Font("saira_condensed_semibold.ttf", FontWeight.SemiBold),
    Font("saira_condensed_bold.ttf", FontWeight.Bold),
)

val Tabular = TextStyle(fontFeatureSettings = "tnum")
val Clock = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

/** Same colours as the TV and phone apps: one accent from the theme picker, flat neutrals, live red. */
object Neon {
    var palette by mutableStateOf(Palettes.ESPORTS)

    val Bg get() = Color(palette.bg)
    val Bg2 get() = Color(palette.bg2)
    val Surface get() = Color(palette.surface)
    val SurfaceHi get() = Color(palette.surfaceHi)
    val Purple get() = Color(palette.accent)
    val PurpleSoft get() = Color(palette.accentSoft)
    val Cyan get() = Color(palette.info)
    val Live get() = Color(palette.live)
    val Text get() = Color(palette.text)
    val TextDim get() = Color(palette.textDim)
    val TextFaint get() = Color(palette.textFaint)
    val Line get() = Color(palette.line)
    val background: Brush get() = SolidColor(Bg)
}

private fun scheme(p: Palette) = darkColorScheme(
    primary = Color(p.accent), onPrimary = Color(p.bg),
    secondary = Color(p.accent2), tertiary = Color(p.info),
    background = Color(p.bg), onBackground = Color(p.text),
    surface = Color(p.surface), onSurface = Color(p.text),
    surfaceVariant = Color(p.surfaceHi), onSurfaceVariant = Color(p.textDim),
    outline = Color(p.line), error = Color(p.live),
)

private fun TextStyle.on(f: FontFamily) = copy(fontFamily = f)

private fun typography(b: Typography = Typography()) = b.copy(
    displayLarge = b.displayLarge.on(SairaCondensed), displayMedium = b.displayMedium.on(SairaCondensed), displaySmall = b.displaySmall.on(SairaCondensed),
    headlineLarge = b.headlineLarge.on(SairaCondensed), headlineMedium = b.headlineMedium.on(SairaCondensed), headlineSmall = b.headlineSmall.on(SairaCondensed),
    titleLarge = b.titleLarge.on(Saira), titleMedium = b.titleMedium.on(Saira), titleSmall = b.titleSmall.on(Saira),
    bodyLarge = b.bodyLarge.on(Saira), bodyMedium = b.bodyMedium.on(Saira), bodySmall = b.bodySmall.on(Saira),
    labelLarge = b.labelLarge.on(Saira), labelMedium = b.labelMedium.on(Saira), labelSmall = b.labelSmall.on(Saira),
)

@Composable
fun FitzTheme(content: @Composable () -> Unit) {
    val p = Neon.palette
    val colors = remember(p) { scheme(p) }
    MaterialTheme(colorScheme = colors, typography = remember { typography() }, content = content)
}
