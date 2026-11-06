package com.fitztv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.Source
import com.fitztv.data.SourceType
import com.fitztv.pairing.PairingServer
import com.fitztv.pairing.Qr
import com.fitztv.sources.XtreamClient
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/** Shows a QR code; scanning it opens a setup form on the phone (same Wi-Fi / LAN only). */
@Composable
fun QrSetupDialog(app: FitzApp, onDone: () -> Unit) {
    val received = remember { mutableStateListOf<String>() }
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    val server = remember {
        PairingServer { form ->
            // GitHub token for in-app updates (can be sent on its own).
            val token = form["github_token"].orEmpty().trim()
            if (token.isNotEmpty()) {
                app.settings.update { it.copy(githubToken = token) }
                app.scope.launch { received += "GitHub token (updates)"; app.updates.check() }
            }
            val hasSource = (form["url"].orEmpty() + form["m3u"].orEmpty()).isNotBlank()
            if (token.isNotEmpty() && !hasSource) return@PairingServer null
            val type = if (form["type"] == "m3u") SourceType.M3U else SourceType.XTREAM
            val src = Source(
                id = UUID.randomUUID().toString(),
                name = form["name"].orEmpty().ifBlank { if (type == SourceType.M3U) "Playlist" else "My IPTV" },
                type = type,
                url = (if (type == SourceType.M3U) form["m3u"] else form["url"]).orEmpty().trim(),
                username = form["username"].orEmpty().trim(),
                password = form["password"].orEmpty().trim(),
                epgUrl = form["epg"].orEmpty().trim(),
            )
            when {
                src.url.isBlank() -> "Please enter the ${if (type == SourceType.M3U) "playlist" else "server"} URL"
                type == SourceType.XTREAM && (src.username.isBlank() || src.password.isBlank()) -> "Username and password are required"
                else -> {
                    // Runs on the server thread, so the network check is fine here.
                    val err = if (type == SourceType.XTREAM) XtreamClient.verify(src, app.settings.value.userAgent) else null
                    if (err == null) {
                        app.settings.update { it.copy(sources = it.sources + src) }
                        app.scope.launch { received += src.name; app.repo.refreshChannels() }
                    }
                    err
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        val u = kotlinx.coroutines.withContext(Dispatchers.IO) { server.start() }
        if (u == null) failed = true else url = u
    }
    DisposableEffect(Unit) { onDispose { server.stop() } }
    val qr = remember(url) { url?.let { Qr.bitmap(it).asImageBitmap() } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDone) {
        Row(
            Modifier.background(Neon.Bg2, RoundedCornerShape(20.dp))
                .border(2.dp, Neon.Purple, RoundedCornerShape(20.dp))
                .padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(280.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                when {
                    qr != null -> Image(qr, contentDescription = "Setup QR code", modifier = Modifier.size(256.dp))
                    failed -> Text("No network connection", color = Color.Black)
                    else -> Text("Starting…", color = Color.Black)
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set up from your phone", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
                Text(
                    "1. Connect your phone to the same Wi-Fi as the Shield\n2. Scan the code with the camera\n" +
                        "3. Enter your Xtream login or M3U URL (and EPG guide URL), and/or the GitHub token for updates, then tap Send to TV",
                    color = Neon.TextDim, fontSize = 14.sp,
                )
                url?.let { Text(it.substringBefore("?"), color = Neon.Cyan, fontSize = 13.sp) }
                Text("Details go straight from your phone to the TV over your home network.", color = Neon.TextFaint, fontSize = 12.sp)
                received.forEach { Text("✓ Added $it", color = Neon.Cyan, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(6.dp))
                NeonButton("Done", onClick = onDone, selected = true, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}
