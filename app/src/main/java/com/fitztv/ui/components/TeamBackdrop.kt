package com.fitztv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitztv.FitzApp
import com.fitztv.data.ThemeArt
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.delay

/**
 * Hourly rotating photo of one of your teams behind the whole UI. A single cached image
 * at screen size, drawn once: no cost to scrolling or switching tabs.
 */
@Composable
fun TeamBackdrop() {
    val app = FitzApp.instance
    val settings by app.settings.state.collectAsState()
    if (!settings.teamTheme) return
    var hour by remember { mutableLongStateOf(System.currentTimeMillis() / 3_600_000) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(3_600_000 - now % 3_600_000 + 1_000)
            hour = System.currentTimeMillis() / 3_600_000
        }
    }
    var art by remember { mutableStateOf<ThemeArt?>(null) }
    LaunchedEffect(hour, settings.favoriteTeams) { art = runCatching { app.repo.themeArt(hour) }.getOrNull() }

    art.let { a ->
        if (a != null) Box(Modifier.fillMaxSize()) {
            AsyncImage(a.url, null, Modifier.fillMaxSize().alpha(0.42f), contentScale = ContentScale.Crop)
            // Flat scrim so text stays readable over the photo.
            Box(Modifier.fillMaxSize().background(Neon.Bg.copy(alpha = 0.8f)))
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                a.logo?.let { AsyncImage(it, null, Modifier.size(22.dp).alpha(0.8f)); Spacer(Modifier.width(8.dp)) }
                Text(a.team, color = Neon.TextFaint, fontSize = 12.sp)
            }
        }
    }
}
