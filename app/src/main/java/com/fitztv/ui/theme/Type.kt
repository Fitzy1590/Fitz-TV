package com.fitztv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.fitztv.core.R as CoreR

/** Saira (SIL Open Font License, see third_party/OFL-Saira.txt): body and labels. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun saira(w: Int) = Font(CoreR.font.saira_variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Saira = FontFamily(saira(400), saira(500), saira(600), saira(700))

/** Saira Condensed: scores, titles and section headings. */
val SairaCondensed = FontFamily(
    Font(CoreR.font.saira_condensed_medium, FontWeight.Medium),
    Font(CoreR.font.saira_condensed_semibold, FontWeight.SemiBold),
    Font(CoreR.font.saira_condensed_bold, FontWeight.Bold),
)

private fun TextStyle.on(f: FontFamily) = copy(fontFamily = f)

/** Condensed for display / headline styles, Saira for everything else. */
fun appTypography(b: Typography = Typography()) = b.copy(
    displayLarge = b.displayLarge.on(SairaCondensed), displayMedium = b.displayMedium.on(SairaCondensed), displaySmall = b.displaySmall.on(SairaCondensed),
    headlineLarge = b.headlineLarge.on(SairaCondensed), headlineMedium = b.headlineMedium.on(SairaCondensed), headlineSmall = b.headlineSmall.on(SairaCondensed),
    titleLarge = b.titleLarge.on(Saira), titleMedium = b.titleMedium.on(Saira), titleSmall = b.titleSmall.on(Saira),
    bodyLarge = b.bodyLarge.on(Saira), bodyMedium = b.bodyMedium.on(Saira), bodySmall = b.bodySmall.on(Saira),
    labelLarge = b.labelLarge.on(Saira), labelMedium = b.labelMedium.on(Saira), labelSmall = b.labelSmall.on(Saira),
)
