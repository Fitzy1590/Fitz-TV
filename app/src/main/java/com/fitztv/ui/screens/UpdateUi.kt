package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.theme.Neon
import com.fitztv.update.UpdateState
import kotlinx.coroutines.launch

/** Shown when a newer build is on GitHub (startup check or Settings › Check for updates). */
@Composable
fun UpdatePrompt(app: FitzApp) {
    val state by app.updates.state.collectAsState()
    val scope = rememberCoroutineScope()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        else -> return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = { app.updates.dismiss() }) {
        Column(
            Modifier.width(600.dp).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Update available", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Text("Build ${release.build} (you have build ${app.updates.currentBuild})", color = Neon.Cyan, fontSize = 14.sp)
            if (release.notes.isNotBlank()) Text(release.notes, color = Neon.TextDim, fontSize = 13.sp, maxLines = 8)
            (state as? UpdateState.Downloading)?.let { Text("Downloading… ${it.percent}%", color = Neon.Purple, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(
                    if (state is UpdateState.Downloading) "Downloading…" else "Update now", selected = true,
                    modifier = Modifier.focusRequester(focus),
                    onClick = { if (state !is UpdateState.Downloading) scope.launch { app.updates.downloadAndInstall(release) } },
                )
                NeonButton("Later", onClick = { app.updates.dismiss() })
            }
        }
    }
}

/** Human readable update status for Settings. */
fun updateStatusText(app: FitzApp, state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Installed: build ${app.updates.currentBuild}"
    UpdateState.Checking -> "Checking GitHub…"
    is UpdateState.UpToDate -> "Up to date (build ${state.build})"
    is UpdateState.Available -> "Build ${state.release.build} is available"
    is UpdateState.Downloading -> "Downloading build ${state.release.build}… ${state.percent}%"
    is UpdateState.Error -> state.message
}
