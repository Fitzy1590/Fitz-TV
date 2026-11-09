package com.fitztv.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.focusRequester
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
    var editYt by remember { mutableStateOf(false) }
    var editPriority by remember { mutableStateOf(false) }
    var editToken by remember { mutableStateOf(false) }
    val upd by app.updates.state.collectAsState()
    fun update(block: (AppSettings) -> AppSettings) = app.settings.update(block)
    var choice by remember { mutableStateOf<Choice?>(null) }
    fun engineLabel(e: Engine) = when (e) { Engine.AUTO -> "Auto (ExoPlayer, VLC if a stream won't play)"; Engine.EXOPLAYER -> "ExoPlayer only"; Engine.VLC -> "VLC only" }
    fun formatLabel(f: XtreamFormat) = when (f) {
        XtreamFormat.AUTO -> "Auto — HLS when your provider allows it, otherwise MPEG-TS"
        XtreamFormat.HLS -> "HLS (.m3u8) — segment based, recovers best from network blips"
        XtreamFormat.TS -> "MPEG-TS (.ts) — lowest delay, single continuous connection"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Sync with your phone") }
        item {
            SettingRow(
                "Sync Shield ↔ phone",
                "Scan the QR with your phone: paste the phone app's sync code to bring everything across, or open \"Copy this TV's settings\" to send the TV's settings to the phone",
                action = "Open",
            ) { showQr = true }
        }

        item { SectionTitle("App updates") }
        item {
            SettingRow("Check for updates", updateStatusText(app, upd), action = if (upd is com.fitztv.update.UpdateState.Available) "Update" else "Check") {
                val st = upd
                scope.launch {
                    if (st is com.fitztv.update.UpdateState.Available) app.updates.downloadAndInstall(st.release) else app.updates.check()
                }
            }
        }
        item {
            SettingRow("Check automatically on startup", if (s.autoUpdateCheck) "On" else "Off") { update { it.copy(autoUpdateCheck = !it.autoUpdateCheck) } }
            SettingRow("Download and install updates automatically", if (s.autoUpdateInstall) "On" else "Off") { update { it.copy(autoUpdateInstall = !it.autoUpdateInstall) } }
        }
        item {
            SettingRow(
                "GitHub token",
                if (s.githubToken.isBlank()) "Not set — needed while the Fitz-TV repo is private" else "Set (stored only on this device)",
                action = "Edit",
            ) { editToken = true }
        }

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
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Set up from phone", selected = true, icon = com.fitztv.ui.components.Glyph.Phone, onClick = { showQr = true })
                NeonButton("Add Xtream login", onClick = { adding = SourceType.XTREAM })
                NeonButton("Add M3U playlist", onClick = { adding = SourceType.M3U })
                NeonButton("Refresh everything", icon = com.fitztv.ui.components.Glyph.Refresh, onClick = { scope.launch { app.repo.refreshIfStale(force = true) } })
            }
        }

        item { SectionTitle("Theme") }
        item {
            val pal = com.fitztv.ui.components.Palettes.byId(s.theme)
            SettingRow("Colour theme", "${pal.name} — ${pal.description}") {
                choice = Choice("Colour theme", com.fitztv.ui.components.Palettes.all.map { p -> "${p.name} — ${p.description}" to { update { it.copy(theme = p.id) } } },
                    com.fitztv.ui.components.Palettes.all.indexOf(pal))
            }
        }

        item { SectionTitle("Playback") }
        item {
            SettingRow("Player engine", engineLabel(s.engine)) {
                choice = Choice("Player engine", Engine.entries.map { e -> engineLabel(e) to { update { it.copy(engine = e) } } }, s.engine.ordinal)
            }
        }
        item {
            SettingRow("Xtream stream format", formatLabel(s.xtreamFormat) + "  •  falls back to the other format automatically if a stream fails") {
                choice = Choice("Xtream stream format", XtreamFormat.entries.map { f -> formatLabel(f) to {
                    update { it.copy(xtreamFormat = f) }; scope.launch { app.repo.refreshChannels() }; Unit
                } }, s.xtreamFormat.ordinal)
            }
        }
        item {
            SettingRow(
                "Priority categories",
                if (s.priorityGroups.isEmpty()) "None — streams rank by match, resolution and language"
                else "${s.priorityGroups.size} starred · their streams are tried first: " + s.priorityGroups.take(3).joinToString(", ") + if (s.priorityGroups.size > 3) "…" else "",
                action = "Edit",
            ) { editPriority = true }
        }
        item {
            SettingRow("Buffer ahead", "${s.bufferSeconds}s  (more = fewer stalls, slightly slower switching)") {
                val opts = listOf(15, 30, 45, 60)
                choice = Choice("Buffer ahead", opts.map { b -> "$b seconds" + (if (b == 30) " (recommended)" else "") to { update { it.copy(bufferSeconds = b) } } }, opts.indexOf(s.bufferSeconds))
            }
        }
        item {
            SettingRow("Audio boost", if (s.audioBoostDb == 0) "Off" else "+${s.audioBoostDb} dB  (for quiet streams; not applied when bitstreaming to an AV receiver)") {
                val opts = listOf(0, 3, 6, 9, 12)
                choice = Choice("Audio boost", opts.map { d -> (if (d == 0) "Off" else "+$d dB") to { update { it.copy(audioBoostDb = d) } } }, opts.indexOf(s.audioBoostDb))
            }
        }
        item {
            SettingRow("Match frame rate", if (s.matchFrameRate) "On — switch the TV to the stream's refresh rate (smooth motion)" else "Off") {
                update { it.copy(matchFrameRate = !it.matchFrameRate) }
            }
        }
        item {
            SettingRow(
                "Tunnelled playback (experimental)",
                if (s.tunneling) "On — video and audio hardware sync directly (smoother, less CPU). Turn off if you see audio drop-outs, stutter or a black picture"
                else "Off — turn on to compare smoothness on your Shield. Applies from the next stream you open",
            ) { update { it.copy(tunneling = !it.tunneling) } }
        }
        item {
            SettingRow("User agent", s.userAgent.take(60), action = "Change") {
                choice = Choice("User agent", userAgents.map { ua -> ua.take(70) to { update { it.copy(userAgent = ua) } } }, userAgents.indexOf(s.userAgent))
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
        item { SettingRow("Time zone", "Adelaide, Australia (ACST/ACDT) — all guide and game times use this") {} }
        item {
            SettingRow("Clock", if (s.use24h) "24-hour" else "12-hour") {
                choice = Choice("Clock", listOf("12-hour (7:30 pm)" to { update { it.copy(use24h = false) } }, "24-hour (19:30)" to { update { it.copy(use24h = true) } }), if (s.use24h) 1 else 0)
            }
        }
        item {
            val report by app.repo.guideReport.collectAsState()
            SettingRow("Guide status", report ?: "Not refreshed since the app opened", action = "Refresh") { scope.launch { app.repo.refreshSchedule(force = true) } }
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

        item { SectionTitle("Alerts & reminders") }
        item { SettingRow("My team is starting", if (s.teamStartAlerts) "On — pop-up when a favourite team's game begins" else "Off") { update { it.copy(teamStartAlerts = !it.teamStartAlerts) } } }
        item { SettingRow("Auto-switch to my team", if (s.autoSwitchToTeam) "On — jump straight into your team's game at kick-off" else "Off — just show the pop-up") { update { it.copy(autoSwitchToTeam = !it.autoSwitchToTeam) } } }
        item { SettingRow("Score & close-game alerts", if (s.scoreAlerts) "On — banner when your team scores or a game goes down to the wire" else "Off") { update { it.copy(scoreAlerts = !it.scoreAlerts) } } }
        item {
            val recs by com.fitztv.record.Recorder.recordings.collectAsState()
            val used = recs.sumOf { it.sizeBytes } / 1e9
            val free = (com.fitztv.record.Recorder.dir(LocalContext.current).usableSpace / 1e9)
            SettingRow("Recordings", "${recs.size} recordings • %.1f GB used • %.0f GB free on this device (about 3–4 GB per hour of HD)".format(used, free)) {}
        }

        item { SectionTitle("Highlights") }
        item {
            SettingRow(
                "YouTube API key (optional)",
                if (s.youtubeApiKey.isBlank()) "Not set — highlights open a YouTube search for the game. Add a free key to open the exact video."
                else "Set — highlights open the matching video directly",
                action = "Edit",
            ) { editYt = true }
        }

        item { SectionTitle("Sports shown") }
        item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Sport.tabs) { sp ->
                    val shown = sp.name !in s.hiddenSports
                    NeonButton(sp.label, selected = shown, icon = com.fitztv.ui.components.Glyph.sport(sp), onClick = {
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
    if (editYt) YoutubeKeyDialog(app, onDone = { editYt = false })
    if (editPriority) PriorityGroupsDialog(app, onDone = { editPriority = false })
    choice?.let { c -> ChoiceDialog(c, onDone = { choice = null }) }
    if (editToken) TokenDialog(app, onDone = { editToken = false })
}

@Composable
private fun SettingRow(title: String, value: String, action: String? = null, onClick: () -> Unit) {
    NeonSurface(onClick = onClick, modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 13.sp, color = Neon.TextDim)
            }
            if (action != null) Text(action, fontSize = 13.sp, color = Neon.TextDim)
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
                .background(Neon.Bg2, RoundedCornerShape(16.dp))
                .border(1.dp, Neon.Line, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (type == SourceType.XTREAM) "Add Xtream Codes login" else "Add M3U playlist", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
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
            unfocusedBorderColor = Neon.Line,
            focusedLabelColor = Neon.Purple,
            unfocusedLabelColor = Neon.TextDim,
            cursorColor = Neon.Purple,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}


@Composable
private fun YoutubeKeyDialog(app: FitzApp, onDone: () -> Unit) {
    var key by remember { mutableStateOf(app.settings.value.youtubeApiKey) }
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(620.dp).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("YouTube API key", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Free from console.cloud.google.com → APIs & Services → enable “YouTube Data API v3” → Credentials → API key. " +
                    "Stored only on this device; used only to find highlight videos.",
                color = Neon.TextDim, fontSize = 13.sp,
            )
            Field("API key", key) { key = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("Save", selected = true, onClick = { app.settings.update { it.copy(youtubeApiKey = key.trim()) }; onDone() })
                NeonButton("Clear", onClick = { app.settings.update { it.copy(youtubeApiKey = "") }; onDone() })
                NeonButton("Cancel", onClick = onDone)
            }
        }
    }
}


@Composable
private fun TokenDialog(app: FitzApp, onDone: () -> Unit) {
    var token by remember { mutableStateOf(app.settings.value.githubToken) }
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(640.dp).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GitHub token for updates", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "github.com → Settings → Developer settings → Fine-grained tokens → Generate. Repository access: only Fitz-TV. " +
                    "Permissions: Contents → Read-only. Paste it here. Not needed if the repo is public.",
                color = Neon.TextDim, fontSize = 13.sp,
            )
            Field("Token", token, password = true) { token = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("Save", selected = true, onClick = { app.settings.update { it.copy(githubToken = token.trim()) }; onDone() })
                NeonButton("Clear", onClick = { app.settings.update { it.copy(githubToken = "") }; onDone() })
                NeonButton("Cancel", onClick = onDone)
            }
        }
    }
}


/** A setting with a fixed list of options: pick one directly instead of cycling. */
private data class Choice(val title: String, val options: List<Pair<String, () -> Unit>>, val selected: Int)

@Composable
private fun ChoiceDialog(c: Choice, onDone: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(640.dp).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(c.title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            c.options.forEachIndexed { i, (label, action) ->
                NeonSurface(
                    onClick = { action(); onDone() },
                    modifier = Modifier.fillMaxWidth().then(if (i == c.selected.coerceAtLeast(0)) Modifier.focusRequester(focus) else Modifier),
                    radius = 10.dp, selected = i == c.selected,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.tv.material3.Icon(if (i == c.selected) com.fitztv.ui.components.Glyph.RadioOn else com.fitztv.ui.components.Glyph.RadioOff, null, tint = if (i == c.selected) Neon.Purple else Neon.TextFaint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 15.sp, color = Neon.Text)
                    }
                }
            }
        }
    }
}
