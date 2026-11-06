package com.fitztv.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Engine
import com.fitztv.data.Source
import com.fitztv.data.SourceType
import com.fitztv.data.XtreamFormat
import com.fitztv.sources.XtreamClient
import com.fitztv.sports.Sport
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.components.Pill
import com.fitztv.ui.components.SectionTitle
import com.fitztv.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.material3.Text as M3Text

private val zones = listOf(
    "", "Australia/Sydney", "Australia/Melbourne", "Australia/Brisbane", "Australia/Adelaide", "Australia/Perth",
    "Pacific/Auckland", "Asia/Kolkata", "Europe/London", "Europe/Dublin", "America/New_York", "America/Chicago",
    "America/Denver", "America/Los_Angeles", "UTC",
)

private val userAgents = listOf(
    AppSettings.DEFAULT_UA,
    "Mozilla/5.0 (Linux; Android 11; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    "IPTVSmartersPro",
    "okhttp/4.12.0",
)

private fun <T> List<T>.after(v: T): T = this[(indexOf(v) + 1).mod(size)]

@Composable
fun SettingsScreen(app: FitzApp) {
    val s by app.settings.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf<SourceType?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var showFavs by remember { mutableStateOf(false) }
    fun update(block: (AppSettings) -> AppSettings) = app.settings.update(block)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Sources") }
        if (s.sources.isEmpty()) item {
            Text("No sources yet. Add an Xtream Codes login or an M3U playlist URL.", color = Neon.TextDim, modifier = Modifier.padding(start = 48.dp))
        }
        items(s.sources, key = { it.id }) { src ->
            SettingRow(
                title = src.name,
                value = if (src.type == SourceType.XTREAM) "Xtream • ${src.url}" else "M3U • ${src.url.take(60)}",
                action = if (confirmRemove == src.id) "Press again to remove" else "Remove",
                onClick = {
                    if (confirmRemove != src.id) { confirmRemove = src.id; return@SettingRow }
                    confirmRemove = null
                    update { st -> st.copy(sources = st.sources.filter { it.id != src.id }) }
                    scope.launch { app.repo.refreshChannels() }
                },
            )
        }
        item {
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("📱 Set up from phone (QR)", selected = true, onClick = { showQr = true })
                NeonButton("+ Xtream Codes login", onClick = { adding = SourceType.XTREAM })
                NeonButton("+ M3U playlist", onClick = { adding = SourceType.M3U })
                NeonButton("⟳ Refresh everything", onClick = { scope.launch { app.repo.refreshIfStale(force = true) } })
            }
        }

        item { SectionTitle("Playback") }
        item {
            SettingRow("Player engine", when (s.engine) {
                Engine.AUTO -> "Auto (ExoPlayer, VLC if a stream won't play)"
                Engine.EXOPLAYER -> "ExoPlayer only"
                Engine.VLC -> "VLC only"
            }) { update { it.copy(engine = Engine.entries.after(it.engine)) } }
        }
        item {
            SettingRow("Xtream stream format", when (s.xtreamFormat) {
                XtreamFormat.AUTO -> "Auto — HLS when your provider allows it, otherwise MPEG-TS"
                XtreamFormat.HLS -> "HLS (.m3u8) — segment based, recovers best from network blips"
                XtreamFormat.TS -> "MPEG-TS (.ts) — lowest delay, single continuous connection"
            } + "  •  falls back to the other format automatically if a stream fails") {
                update { it.copy(xtreamFormat = XtreamFormat.entries.after(it.xtreamFormat)) }
                scope.launch { app.repo.refreshChannels() }
            }
        }
        item {
            SettingRow("Buffer ahead", "${s.bufferSeconds}s  (more = fewer stalls, slightly slower switching)") {
                update { it.copy(bufferSeconds = listOf(15, 30, 45, 60).after(it.bufferSeconds.takeIf { b -> b in listOf(15, 30, 45, 60) } ?: 30)) }
            }
        }
        item {
            SettingRow("Audio boost", if (s.audioBoostDb == 0) "Off" else "+${s.audioBoostDb} dB  (for quiet streams; not applied when bitstreaming to an AV receiver)") {
                update { it.copy(audioBoostDb = listOf(0, 3, 6, 9).after(it.audioBoostDb.takeIf { d -> d in listOf(0, 3, 6, 9) } ?: 0)) }
            }
        }
        item {
            SettingRow("Match frame rate", if (s.matchFrameRate) "On — switch the TV to the stream's refresh rate (smooth motion)" else "Off") {
                update { it.copy(matchFrameRate = !it.matchFrameRate) }
            }
        }
        item {
            SettingRow("User agent", s.userAgent.take(60), action = "Change") {
                update { it.copy(userAgent = userAgents.after(it.userAgent.takeIf { ua -> ua in userAgents } ?: userAgents.last())) }
            }
        }
        item {
            SettingRow(
                "NVIDIA AI upscaling",
                "Video is decoded in hardware onto a SurfaceView so the Shield's AI upscaler can enhance it. " +
                    "Turn it on in Settings › Device Preferences › Display & Sound › AI upscaling (choose AI-Enhanced, High).",
                action = "Open",
            ) {
                val intents = listOf(Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                intents.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
            }
        }

        item { SectionTitle("Guide & time") }
        item {
            SettingRow("Time zone", if (s.timeZone.isBlank()) "Automatic (${java.time.ZoneId.systemDefault().id})" else s.timeZone) {
                update { it.copy(timeZone = zones.after(it.timeZone.takeIf { z -> z in zones } ?: "")) }
            }
        }
        item { SettingRow("Clock", if (s.use24h) "24-hour" else "12-hour") { update { it.copy(use24h = !it.use24h) } } }
        item {
            val report by app.repo.guideReport.collectAsState()
            SettingRow("Guide status", report ?: "Not refreshed since the app opened", action = "Refresh") { scope.launch { app.repo.refreshSchedule() } }
        }

        item { SectionTitle("Favourite teams") }
        item {
            SettingRow("My teams", s.favoriteTeams.sorted().joinToString(", ").ifBlank { "None" }, action = "Edit") { showFavs = true }
        }
        item {
            SettingRow("Team theme", if (s.teamTheme) "On — background rotates hourly between your teams" else "Off — plain neon background") {
                update { it.copy(teamTheme = !it.teamTheme) }
            }
        }

        item { SectionTitle("Sports shown") }
        item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Sport.tabs) { sp ->
                    val shown = sp.name !in s.hiddenSports
                    NeonButton("${sp.emoji} ${sp.label}", selected = shown, onClick = {
                        update { it.copy(hiddenSports = if (shown) it.hiddenSports + sp.name else it.hiddenSports - sp.name) }
                        scope.launch { app.repo.refreshSchedule() }
                    })
                }
            }
        }

        item { SectionTitle("Privacy") }
        item {
            Text(
                "No analytics, no crash reporting, no logging. The only network traffic is your IPTV provider and the public " +
                    "schedule feeds (ESPN, TheSportsDB) used to build the TV guide once a day.",
                color = Neon.TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }

    adding?.let { type -> AddSourceDialog(app, type, onDone = { adding = null }) }
    if (showQr) QrSetupDialog(app, onDone = { showQr = false })
    if (showFavs) FavouritesDialog(app, onDone = { showFavs = false })
}

@Composable
private fun SettingRow(title: String, value: String, action: String? = null, onClick: () -> Unit) {
    NeonSurface(onClick = onClick, modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(value, fontSize = 13.sp, color = Neon.TextDim)
            }
            if (action != null) Pill(action, Neon.Magenta)
        }
    }
}

@Composable
private fun AddSourceDialog(app: FitzApp, type: SourceType, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(if (type == SourceType.XTREAM) "My IPTV" else "Playlist") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(620.dp)
                .background(Neon.Bg2, RoundedCornerShape(20.dp))
                .border(2.dp, Neon.Purple, RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (type == SourceType.XTREAM) "Add Xtream Codes login" else "Add M3U playlist", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Field("Name", name) { name = it }
            Field(if (type == SourceType.XTREAM) "Server URL (http://host:port)" else "Playlist URL", url, KeyboardType.Uri) { url = it }
            if (type == SourceType.XTREAM) {
                Field("Username", user) { user = it }
                Field("Password", pass, password = true) { pass = it }
            }
            Field("EPG / XMLTV URL (optional)", epg, KeyboardType.Uri) { epg = it }
            error?.let { Text(it, color = Neon.Live, fontSize = 14.sp) }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(if (busy) "Checking…" else "Save", selected = true, onClick = {
                    if (busy) return@NeonButton
                    if (url.isBlank() || (type == SourceType.XTREAM && (user.isBlank() || pass.isBlank()))) {
                        error = "Please fill in all required fields"; return@NeonButton
                    }
                    val src = Source(UUID.randomUUID().toString(), name.ifBlank { "Source" }, type, url.trim(), user.trim(), pass.trim(), epg.trim())
                    busy = true
                    scope.launch {
                        val err = if (type == SourceType.XTREAM) withContext(Dispatchers.IO) {
                            XtreamClient.verify(src, app.settings.value.userAgent)
                        } else null
                        busy = false
                        if (err != null) { error = err; return@launch }
                        app.settings.update { it.copy(sources = it.sources + src) }
                        onDone()
                        app.scope.launch { app.repo.refreshChannels() }
                    }
                })
                NeonButton("Cancel", onClick = onDone)
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { M3Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Neon.Text,
            unfocusedTextColor = Neon.Text,
            focusedBorderColor = Neon.Purple,
            unfocusedBorderColor = Neon.PurpleSoft,
            focusedLabelColor = Neon.Purple,
            unfocusedLabelColor = Neon.TextDim,
            cursorColor = Neon.Magenta,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
