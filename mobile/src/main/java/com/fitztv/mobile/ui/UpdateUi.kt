package com.fitztv.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitztv.FitzApp
import com.fitztv.update.UpdateState
import kotlinx.coroutines.launch

/** Prompt shown when a newer build is on GitHub. */
@Composable
fun UpdatePrompt(app: FitzApp) {
    val state by app.updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        else -> return
    }
    AlertDialog(
        onDismissRequest = { app.updates.dismiss() }, containerColor = Neon.Bg2,
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Build ${release.build} (you have build ${app.updates.currentBuild})", color = Neon.Cyan, fontSize = 14.sp)
                if (release.notes.isNotBlank()) Text(release.notes, color = Neon.TextDim, fontSize = 13.sp, maxLines = 8)
                (state as? UpdateState.Downloading)?.let { Text("Downloading… ${it.percent}%", color = Neon.Magenta, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (state !is UpdateState.Downloading) scope.launch { app.updates.downloadAndInstall(release) } }) {
                Text(if (state is UpdateState.Downloading) "Downloading…" else "Update now", color = Neon.Purple)
            }
        },
        dismissButton = { TextButton(onClick = { app.updates.dismiss() }) { Text("Later") } },
    )
}

fun updateStatusText(app: FitzApp, state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Installed: build ${app.updates.currentBuild}"
    UpdateState.Checking -> "Checking GitHub…"
    is UpdateState.UpToDate -> "Up to date (build ${state.build})"
    is UpdateState.Available -> "Build ${state.release.build} is available"
    is UpdateState.Downloading -> "Downloading build ${state.release.build}… ${state.percent}%"
    is UpdateState.Error -> state.message
}
