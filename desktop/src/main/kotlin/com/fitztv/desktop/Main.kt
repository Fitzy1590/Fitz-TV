package com.fitztv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.fitztv.FitzApp
import com.fitztv.desktop.ui.FitzTheme
import com.fitztv.desktop.ui.MainScreen
import com.fitztv.desktop.ui.MultiviewScreen
import com.fitztv.desktop.ui.Neon
import com.fitztv.desktop.ui.PlayerScreen
import com.fitztv.player.Multiview
import com.fitztv.player.PlaybackQueue
import com.fitztv.ui.components.Palettes
import kotlinx.coroutines.launch

fun main() {
    application {
        val app = remember { FitzApp() }
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        val settings by app.settings.state.collectAsState()
        val playing by PlaybackQueue.open.collectAsState()
        val multi by Multiview.open.collectAsState()
        val fullscreen = windowState.placement == WindowPlacement.Fullscreen
        val toggleFullscreen = { windowState.placement = if (fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen }

        setSingletonImageLoaderFactory { ctx -> ImageLoader.Builder(ctx).components { add(OkHttpNetworkFetcherFactory()) }.build() }

        // Theme picker and the startup update check.
        LaunchedEffect(settings.theme) { Neon.palette = Palettes.byId(settings.theme) }
        LaunchedEffect(Unit) {
            val s = app.settings.value
            if (s.autoUpdateCheck) app.updates.autoUpdate()
        }

        Window(onCloseRequest = {
            // Stop streaming and recording cleanly, then quit.
            exitApplication()
        }, state = windowState, title = "Fitz TV") {
            FitzTheme {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Neon.Bg)) {
                    when {
                        playing -> PlayerScreen(fullscreen, toggleFullscreen, onClose = { PlaybackQueue.close() })
                        multi -> MultiviewScreen(fullscreen, toggleFullscreen, onClose = { Multiview.close() })
                        else -> MainScreen(app)
                    }
                }
            }
        }
    }
}
