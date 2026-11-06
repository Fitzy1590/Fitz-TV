package com.fitztv.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.Engine
import com.fitztv.data.Source
import com.fitztv.data.SourceType
import com.fitztv.data.XtreamFormat
import com.fitztv.sources.XtreamClient
import com.fitztv.sports.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val zones = listOf(
    "", "Australia/Adelaide", "Australia/Darwin", "Australia/Sydney", "Australia/Melbourne", "Australia/Brisbane",
    "Australia/Perth", "Pacific/Auckland", "Europe/London", "America/New_York", "America/Chicago", "America/Los_Angeles", "UTC",
)
private val userAgents = listOf(
    AppSettings.DEFAULT_UA,
    "Mozilla/5.0 (Linux; Android 14; SM-F956B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
    "IPTVSmartersPro",
    "okhttp/4.12.0",
)
private fun <T> List<T>.after(v: T): T = this[(indexOf(v) + 1).mod(size)]

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(app: FitzApp, onEditFavs: () -> Unit) {
    val s by app.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf<SourceType?>(null) }
    var confirmRemove by remember { mutableStateOf<Source?>(null) }
    var editYt by remember { mutableStateOf(false) }
    var editToken by remember { mutableStateOf(false) }
    val upd by app.updates.state.collectAsState()
    fun update(block: (AppSettings) -> AppSettings) = app.settings.update(block)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionHeader("App updates") }
        item {
            Row2("Check for updates", updateStatusText(app, upd), if (upd is com.fitztv.update.UpdateState.Available) "Update" else "Check") {
                val st = upd
                scope.launch { if (st is com.fitztv.update.UpdateState.Available) app.updates.downloadAndInstall(st.release) else app.updates.check() }
            }
        }
        item { Row2("Check automatically on startup", if (s.autoUpdateCheck) "On" else "Off") { update { it.copy(autoUpdateCheck = !it.autoUpdateCheck) } } }
        item {
            Row2("GitHub token", if (s.githubToken.isBlank()) "Not set — needed while the Fitz-TV repo is private" else "Set (stored only on this phone)", "Edit") { editToken = true }
        }

        item { SectionHeader("Sources") }
        if (s.sources.isEmpty()) item { Text("No sources yet.", color = Neon.TextDim) }
        items(s.sources, key = { it.id }) { src ->
            Row2(src.name, (if (src.type == SourceType.XTREAM) "Xtream • " else "M3U • ") + src.url, "Remove") { confirmRemove = src }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = SourceType.XTREAM }, colors = ButtonDefaults.buttonColors(containerColor = Neon.Purple)) { Text("+ Xtream login") }
                Button(onClick = { adding = SourceType.M3U }, colors = ButtonDefaults.buttonColors(containerColor = Neon.Purple)) { Text("+ M3U playlist") }
                OutlinedButton(onClick = { scope.launch { app.repo.refreshIfStale(force = true) } }) { Text("⟳ Refresh", color = Neon.Text) }
            }
        }

        item { SectionHeader("Favourite teams") }
        item { Row2("My teams", s.favoriteTeams.sorted().joinToString(", ").ifBlank { "None" }, "Edit", onEditFavs) }
        item {
            Row2("Team theme", if (s.teamTheme) "On — background rotates hourly between your teams" else "Off — plain neon background") {
                update { it.copy(teamTheme = !it.teamTheme) }
            }
        }

        item { SectionHeader("Kayo") }
        item {
            val installed = com.fitztv.external.Kayo.isInstalled(androidx.compose.ui.platform.LocalContext.current)
            Row2(
                "Kayo",
                when (s.kayoMode) {
                    com.fitztv.data.KayoMode.PRIORITY -> "Priority — games Kayo carries open in the Kayo app first"
                    com.fitztv.data.KayoMode.WHEN_BETTER -> "When better — Kayo only if it beats your best IPTV stream (1080p 50fps)"
                    com.fitztv.data.KayoMode.OFF -> "Off — always use IPTV streams"
                } + if (installed) "" else "  •  Kayo app not installed",
            ) { update { it.copy(kayoMode = com.fitztv.data.KayoMode.entries.after(it.kayoMode)) } }
        }

        item { SectionHeader("Playback") }
        item {
            Row2("Player engine", when (s.engine) {
                Engine.AUTO -> "Auto (ExoPlayer, VLC if a stream won't play)"; Engine.EXOPLAYER -> "ExoPlayer only"; Engine.VLC -> "VLC only"
            }) { update { it.copy(engine = Engine.entries.after(it.engine)) } }
        }
        item {
            Row2("Xtream stream format", when (s.xtreamFormat) {
                XtreamFormat.AUTO -> "Auto — HLS when allowed, otherwise MPEG-TS"
                XtreamFormat.HLS -> "HLS (.m3u8) — best on mobile data / Wi-Fi blips"
                XtreamFormat.TS -> "MPEG-TS (.ts) — lowest delay"
            }) {
                update { it.copy(xtreamFormat = XtreamFormat.entries.after(it.xtreamFormat)) }
                scope.launch { app.repo.refreshChannels() }
            }
        }
        item {
            Row2("Buffer ahead", "${s.bufferSeconds}s (more = fewer stalls, slower switching)") {
                val opts = listOf(15, 30, 45, 60)
                update { it.copy(bufferSeconds = opts.after(it.bufferSeconds.takeIf { b -> b in opts } ?: 30)) }
            }
        }
        item {
            Row2("Audio boost", if (s.audioBoostDb == 0) "Off" else "+${s.audioBoostDb} dB (for quiet streams)") {
                val opts = listOf(0, 3, 6, 9)
                update { it.copy(audioBoostDb = opts.after(it.audioBoostDb.takeIf { d -> d in opts } ?: 0)) }
            }
        }
        item {
            Row2("User agent", s.userAgent.take(50), "Change") {
                update { it.copy(userAgent = userAgents.after(it.userAgent.takeIf { ua -> ua in userAgents } ?: userAgents.last())) }
            }
        }

        item { SectionHeader("Guide & time") }
        item { Row2("Time zone", "Adelaide, Australia (ACST/ACDT) — all guide and game times use this") {} }
        item { Row2("Clock", if (s.use24h) "24-hour" else "12-hour") { update { it.copy(use24h = !it.use24h) } } }
        item {
            val report by app.repo.guideReport.collectAsState()
            Row2("Guide status (tap to refresh)", report ?: "Not refreshed since the app opened") { scope.launch { app.repo.refreshSchedule() } }
        }

        item { SectionHeader("Highlights") }
        item {
            Row2(
                "YouTube API key (optional)",
                if (s.youtubeApiKey.isBlank()) "Not set — highlights open a YouTube search. Add a free key to open the exact video." else "Set — highlights open the video directly",
                "Edit",
            ) { editYt = true }
        }

        item { SectionHeader("Sports shown") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sport.tabs.forEach { sp ->
                    val shown = sp.name !in s.hiddenSports
                    FilterChip(
                        selected = shown, label = { Text("${sp.emoji} ${sp.label}") },
                        onClick = {
                            update { it.copy(hiddenSports = if (shown) it.hiddenSports + sp.name else it.hiddenSports - sp.name) }
                            scope.launch { app.repo.refreshSchedule() }
                        },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Neon.Surface, labelColor = Neon.TextDim, selectedContainerColor = Neon.Purple, selectedLabelColor = Color.White),
                    )
                }
            }
        }
        item { SectionHeader("Privacy") }
        item {
            Text(
                "No analytics, no crash reporting, no logging. The only traffic is your IPTV provider and the public schedule feeds " +
                    "(ESPN, TheSportsDB) used for the guide and live stats.",
                color = Neon.TextDim, fontSize = 13.sp,
            )
        }
    }

    adding?.let { AddSourceDialog(app, it) { adding = null } }
    if (editToken) {
        var token by remember { mutableStateOf(s.githubToken) }
        AlertDialog(
            onDismissRequest = { editToken = false }, containerColor = Neon.Bg2,
            title = { Text("GitHub token for updates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "github.com → Settings → Developer settings → Fine-grained tokens → Generate. Repository access: only Fitz-TV. " +
                            "Permissions: Contents → Read-only. Not needed if the repo is public.",
                        color = Neon.TextDim, fontSize = 13.sp,
                    )
                    Field("Token", token, password = true) { token = it }
                }
            },
            confirmButton = { TextButton(onClick = { update { it.copy(githubToken = token.trim()) }; editToken = false }) { Text("Save", color = Neon.Purple) } },
            dismissButton = { TextButton(onClick = { update { it.copy(githubToken = "") }; editToken = false }) { Text("Clear") } },
        )
    }
    if (editYt) {
        var key by remember { mutableStateOf(s.youtubeApiKey) }
        AlertDialog(
            onDismissRequest = { editYt = false }, containerColor = Neon.Bg2,
            title = { Text("YouTube API key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Free from console.cloud.google.com → enable “YouTube Data API v3” → Credentials → API key. Stored only on this phone.", color = Neon.TextDim, fontSize = 13.sp)
                    Field("API key", key) { key = it }
                }
            },
            confirmButton = { TextButton(onClick = { update { it.copy(youtubeApiKey = key.trim()) }; editYt = false }) { Text("Save", color = Neon.Purple) } },
            dismissButton = { TextButton(onClick = { update { it.copy(youtubeApiKey = "") }; editYt = false }) { Text("Clear") } },
        )
    }
    confirmRemove?.let { src ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${src.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    update { st -> st.copy(sources = st.sources.filter { it.id != src.id }) }
                    confirmRemove = null
                    scope.launch { app.repo.refreshChannels() }
                }) { Text("Remove", color = Neon.Live) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
            containerColor = Neon.Bg2,
        )
    }
}

@Composable
private fun Row2(title: String, value: String, action: String? = null, onClick: () -> Unit) {
    NeonCard(onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
                Text(value, fontSize = 12.sp, color = Neon.TextDim)
            }
            if (action != null) Pill(action, Neon.Magenta)
        }
    }
}

@Composable
fun AddSourceDialog(app: FitzApp, type: SourceType, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(if (type == SourceType.XTREAM) "My IPTV" else "Playlist") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Neon.Bg2,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text(if (type == SourceType.XTREAM) "Add Xtream Codes login" else "Add M3U playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Name", name) { name = it }
                Field(if (type == SourceType.XTREAM) "Server URL (http://host:port)" else "Playlist URL", url, KeyboardType.Uri) { url = it }
                if (type == SourceType.XTREAM) {
                    Field("Username", user) { user = it }
                    Field("Password", pass, password = true) { pass = it }
                }
                Field("EPG / XMLTV URL (optional)", epg, KeyboardType.Uri) { epg = it }
                error?.let { Text(it, color = Neon.Live, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (url.isBlank() || (type == SourceType.XTREAM && (user.isBlank() || pass.isBlank()))) {
                    error = "Please fill in all required fields"; return@TextButton
                }
                val src = Source(UUID.randomUUID().toString(), name.ifBlank { "Source" }, type, url.trim(), user.trim(), pass.trim(), epg.trim())
                busy = true
                scope.launch {
                    val err = if (type == SourceType.XTREAM) withContext(Dispatchers.IO) { XtreamClient.verify(src, app.settings.value.userAgent) } else null
                    busy = false
                    if (err != null) { error = err; return@launch }
                    app.settings.update { it.copy(sources = it.sources + src) }
                    onDone()
                    app.scope.launch { app.repo.refreshChannels() }
                }
            }) { Text(if (busy) "Checking…" else "Save", color = Neon.Purple) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple,
            unfocusedBorderColor = Neon.PurpleSoft, focusedLabelColor = Neon.Purple, unfocusedLabelColor = Neon.TextDim, cursorColor = Neon.Magenta,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
