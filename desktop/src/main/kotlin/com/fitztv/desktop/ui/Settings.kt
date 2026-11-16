package com.fitztv.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.fitztv.ui.components.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val userAgents = listOf(
    AppSettings.DEFAULT_UA,
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    "IPTVSmartersPro",
    "okhttp/4.12.0",
)

private class Choice(val title: String, val options: List<Pair<String, () -> Unit>>, val selected: Int)

@Composable
fun SettingsScreen(app: FitzApp) {
    val s by app.settings.state.collectAsState()
    val upd by app.updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf<SourceType?>(null) }
    var showFavs by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var editYt by remember { mutableStateOf(false) }
    var editToken by remember { mutableStateOf(false) }
    var editPriority by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    fun update(block: (AppSettings) -> AppSettings) = app.settings.update(block)

    fun engineLabel(e: Engine) = when (e) { Engine.AUTO -> "Auto (libVLC)"; Engine.EXOPLAYER -> "Auto (libVLC)"; Engine.VLC -> "libVLC" }
    fun formatLabel(f: XtreamFormat) = when (f) {
        XtreamFormat.AUTO -> "Auto — HLS when your provider allows it, otherwise MPEG-TS"
        XtreamFormat.HLS -> "HLS (.m3u8) — segment based, recovers best from network blips"
        XtreamFormat.TS -> "MPEG-TS (.ts) — lowest delay, single continuous connection"
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Sync with your phone or TV") }
        item { SettingRow("Sync code", "Copy this PC's settings to the TV or phone app, or paste a sync code from them to bring your sources and teams across", action = "Open") { showSync = true } }

        item { SectionTitle("App updates") }
        item {
            SettingRow("Check for updates", updateStatusText(app, upd), action = if (upd is com.fitztv.update.UpdateState.Available) "Update" else "Check") {
                val st = upd
                scope.launch { if (st is com.fitztv.update.UpdateState.Available) app.updates.downloadAndInstall(st.release) else app.updates.check() }
            }
        }
        item { SettingRow("Check automatically on startup", if (s.autoUpdateCheck) "On" else "Off") { update { it.copy(autoUpdateCheck = !it.autoUpdateCheck) } } }
        item { SettingRow("GitHub token", if (s.githubToken.isBlank()) "Not set — needed while the Fitz-TV repo is private" else "Set (stored only on this PC)", action = "Edit") { editToken = true } }

        item { SectionTitle("Sources") }
        if (s.sources.isEmpty()) item { Text("No sources yet. Add an Xtream Codes login or an M3U playlist URL.", color = Neon.TextDim, modifier = Modifier.padding(start = PAD)) }
        items(s.sources, key = { it.id }) { src ->
            SettingRow(src.name, if (src.type == SourceType.XTREAM) "Xtream • ${src.url}" else "M3U • ${src.url.take(60)}", action = if (confirmRemove == src.id) "Click again to remove" else "Remove") {
                if (confirmRemove != src.id) { confirmRemove = src.id; return@SettingRow }
                confirmRemove = null
                update { st -> st.copy(sources = st.sources.filter { it.id != src.id }) }
                scope.launch { app.repo.refreshChannels() }
            }
        }
        item {
            Row(Modifier.padding(start = PAD).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Add Xtream login", selected = true, onClick = { adding = SourceType.XTREAM })
                NeonButton("Add M3U playlist", onClick = { adding = SourceType.M3U })
                NeonButton("Refresh everything", icon = Glyph.Refresh, onClick = { scope.launch { app.repo.refreshIfStale(force = true) } })
            }
        }

        item { SectionTitle("Theme") }
        item {
            val pal = Palettes.byId(s.theme)
            SettingRow("Colour theme", "${pal.name} — ${pal.description}") {
                choice = Choice("Colour theme", Palettes.all.map { p -> "${p.name} — ${p.description}" to { update { it.copy(theme = p.id) } } }, Palettes.all.indexOf(pal))
            }
        }

        item { SectionTitle("Playback") }
        item { SettingRow("Player engine", engineLabel(s.engine) + " — hardware decoding (D3D11 / DXVA) with automatic failover between your streams") {} }
        item {
            SettingRow("Xtream stream format", formatLabel(s.xtreamFormat) + "  •  falls back to the other format automatically if a stream fails") {
                choice = Choice("Xtream stream format", XtreamFormat.entries.map { f -> formatLabel(f) to { update { it.copy(xtreamFormat = f) }; scope.launch { app.repo.refreshChannels() }; Unit } }, s.xtreamFormat.ordinal)
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
            SettingRow("Audio boost", if (s.audioBoostDb == 0) "Off" else "+${s.audioBoostDb} dB  (for quiet streams)") {
                val opts = listOf(0, 3, 6, 9, 12)
                choice = Choice("Audio boost", opts.map { d -> (if (d == 0) "Off" else "+$d dB") to { update { it.copy(audioBoostDb = d) } } }, opts.indexOf(s.audioBoostDb))
            }
        }
        item {
            var result by remember { mutableStateOf<String?>(null) }
            var running by remember { mutableStateOf(false) }
            SettingRow("Test connection to provider", result ?: if (running) "Testing for about 15 seconds…" else "Measures speed and suggests a buffer size and how many streams you can run at once", action = if (running) null else "Run") {
                if (!running) { running = true; scope.launch { result = com.fitztv.data.ConnectionTest.run(app.repo, app.settings.value); running = false } }
            }
        }
        item {
            SettingRow("Score cards (who scored, with stats)", if (s.scoreAnimations) "On — a short card with the scorer's stats. NBA, NFL and soccer" else "Off") { update { it.copy(scoreAnimations = !it.scoreAnimations) } }
        }
        if (s.scoreAnimations) item {
            SettingRow("Score card delay", "${s.scoreDelaySeconds} seconds — hold cards back to match how far behind real time your stream is", action = "Change") {
                update { it.copy(scoreDelaySeconds = listOf(0, 10, 20, 30, 45, 60, 90, 120).let { l -> l.firstOrNull { v -> v > it.scoreDelaySeconds } ?: l.first() }) }
            }
        }
        item {
            SettingRow("Fix unstable streams automatically",
                if (s.selfHeal) "On — a stream that keeps reconnecting is retried with another player identity, a bigger buffer and a longer wait after stalls; a fix that works is remembered for that channel"
                else "Off — streams reconnect the normal way") { update { it.copy(selfHeal = !it.selfHeal) } }
        }
        item {
            SettingRow("User agent", s.userAgent.take(60), action = "Change") {
                choice = Choice("User agent", userAgents.map { ua -> ua.take(70) to { update { it.copy(userAgent = ua) } } }, userAgents.indexOf(s.userAgent))
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
        item { SettingRow("My teams", s.favoriteTeams.sorted().joinToString(", ").ifBlank { "None" }, action = "Edit") { showFavs = true } }
        item { SettingRow("Team theme", if (s.teamTheme) "On — background rotates hourly between your teams" else "Off — plain background") { update { it.copy(teamTheme = !it.teamTheme) } } }

        item { SectionTitle("Alerts & reminders") }
        item { SettingRow("My team is starting", if (s.teamStartAlerts) "On — pop-up when a favourite team's game begins" else "Off") { update { it.copy(teamStartAlerts = !it.teamStartAlerts) } } }
        item { SettingRow("Auto-switch to my team", if (s.autoSwitchToTeam) "On — jump straight into your team's game at kick-off" else "Off — just show the pop-up") { update { it.copy(autoSwitchToTeam = !it.autoSwitchToTeam) } } }
        item { SettingRow("Score & close-game alerts", if (s.scoreAlerts) "On — banner when your team scores or a game goes down to the wire" else "Off") { update { it.copy(scoreAlerts = !it.scoreAlerts) } } }
        item {
            val recs by com.fitztv.record.Recorder.recordings.collectAsState()
            val used = recs.sumOf { it.sizeBytes } / 1e9
            val free = com.fitztv.record.Recorder.dir(app).usableSpace / 1e9
            SettingRow("Recordings", "${recs.size} recordings • %.1f GB used • %.0f GB free on this PC (about 3–4 GB per hour of HD) • saved in ${com.fitztv.record.Recorder.dir(app)}".format(used, free)) {}
        }

        item { SectionTitle("Highlights") }
        item {
            SettingRow("YouTube API key (optional)",
                if (s.youtubeApiKey.isBlank()) "Not set — highlights open a YouTube search for the game. Add a free key to open the exact video."
                else "Set — highlights open the matching video directly", action = "Edit") { editYt = true }
        }

        item { SectionTitle("Sports shown") }
        item {
            Row(Modifier.padding(horizontal = PAD).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sport.tabs.forEach { sp ->
                    val shown = sp.name !in s.hiddenSports
                    NeonButton(sp.label, selected = shown, icon = Glyph.sport(sp), onClick = {
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
                    "schedule feeds (ESPN, TheSportsDB) used to build the TV guide. Your sources and tokens stay on this PC in ${app.filesDir}.",
                color = Neon.TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = PAD),
            )
        }
    }

    adding?.let { type -> AddSourceDialog(app, type, onDone = { adding = null }) }
    if (showFavs) FavouritesDialog(app, onDone = { showFavs = false })
    if (showSync) SyncDialog(app, onDone = { showSync = false })
    if (editYt) TextValueDialog("YouTube API key", "Paste a YouTube Data API v3 key", s.youtubeApiKey, onSave = { v -> update { it.copy(youtubeApiKey = v.trim()) } }, onDone = { editYt = false })
    if (editToken) TextValueDialog("GitHub token", "A read-only token with access to the Fitz-TV repository (stored only on this PC)", s.githubToken, secret = true, onSave = { v -> update { it.copy(githubToken = v.trim()) } }, onDone = { editToken = false })
    if (editPriority) PriorityGroupsDialog(app, onDone = { editPriority = false })
    choice?.let { c -> ChoiceDialog(c, onDone = { choice = null }) }
}

@Composable
private fun SettingRow(title: String, value: String, action: String? = null, onClick: () -> Unit) {
    NeonSurface(onClick = onClick, modifier = Modifier.padding(horizontal = PAD).fillMaxWidth(), radius = 12.dp) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Neon.Text)
                Text(value, fontSize = 13.sp, color = Neon.TextDim)
            }
            if (action != null) Text(action, fontSize = 13.sp, color = Neon.TextDim)
        }
    }
}

@Composable
private fun ChoiceDialog(c: Choice, onDone: () -> Unit) {
    Modal(onDone, width = 640.dp) {
        Text(c.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            c.options.forEachIndexed { i, (label, act) ->
                NeonSurface(onClick = { act(); onDone() }, modifier = Modifier.fillMaxWidth(), radius = 10.dp, selected = i == c.selected) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(if (i == c.selected) Glyph.RadioOn else Glyph.RadioOff, null, tint = if (i == c.selected) Neon.Purple else Neon.TextFaint, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                        Text(label, fontSize = 15.sp, color = Neon.Text)
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@Composable
private fun TextValueDialog(title: String, hint: String, initial: String, secret: Boolean = false, onSave: (String) -> Unit, onDone: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    Modal(onDone, width = 620.dp) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
        Text(hint, color = Neon.TextDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        Field(title, v, password = secret) { v = it }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton("Save", selected = true, onClick = { onSave(v); onDone() })
            NeonButton("Clear", onClick = { onSave(""); onDone() })
            NeonButton("Cancel", onClick = onDone)
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
    Modal(onDone, width = 620.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (type == SourceType.XTREAM) "Add Xtream Codes login" else "Add M3U playlist", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Field("Name", name) { name = it }
            Field(if (type == SourceType.XTREAM) "Server URL (http://host:port)" else "Playlist URL", url) { url = it }
            if (type == SourceType.XTREAM) { Field("Username", user) { user = it }; Field("Password", pass, password = true) { pass = it } }
            Field("EPG / XMLTV URL (optional)", epg) { epg = it }
            error?.let { Text(it, color = Neon.Live, fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(if (busy) "Checking…" else "Save", selected = true, onClick = {
                    if (busy) return@NeonButton
                    if (url.isBlank() || (type == SourceType.XTREAM && (user.isBlank() || pass.isBlank()))) { error = "Please fill in all required fields"; return@NeonButton }
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
                })
                NeonButton("Cancel", onClick = onDone)
            }
        }
    }
}

/** Sync code: the same code the TV and phone apps use (sources, teams, preferences). */
@Composable
private fun SyncDialog(app: FitzApp, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    Modal(onDone, width = 700.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sync code", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Text("Paste a code from the TV or phone app to bring its sources and teams here, or copy this PC's code into them. It contains your IPTV login — only share it with your own devices.", color = Neon.TextDim, fontSize = 13.sp)
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("FITZTV1:…") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5)
            note?.let { Text(it, color = Neon.Purple, fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("Import code", selected = true, onClick = {
                    val r = com.fitztv.data.SyncCode.import(code, app.settings)
                    note = if (r == null) "That isn't a valid sync code" else { scope.launch { app.repo.refreshIfStale(force = true) }; "Imported — refreshing channels and guide" }
                })
                NeonButton("Copy this PC's code", onClick = {
                    val c = com.fitztv.data.SyncCode.export(app.settings.value)
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(c), null)
                    note = "Copied to the clipboard"
                })
                NeonButton("Close", onClick = onDone)
            }
        }
    }
}
