package com.fitztv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.fitztv.FitzApp
import com.fitztv.data.AppSettings
import com.fitztv.data.PriorityGroups
import com.fitztv.ui.components.Glyph
import com.fitztv.ui.components.NeonButton
import com.fitztv.ui.components.NeonSurface
import com.fitztv.ui.theme.Neon
import androidx.compose.material3.Text as M3Text

/** Star the provider categories whose streams should be tried first. */
@Composable
fun PriorityGroupsDialog(app: FitzApp, onDone: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val channels by app.repo.channels.collectAsState()
    var query by remember { mutableStateOf("") }
    val all = remember(channels, settings.priorityGroups) { PriorityGroups.options(channels, settings.priorityGroups) }
    val list = remember(all, query) {
        val q = PriorityGroups.key(query)
        if (query.isBlank()) all else all.filter { PriorityGroups.key(it).contains(q.trim()) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.width(680.dp).background(Neon.Bg2, RoundedCornerShape(16.dp)).border(1.dp, Neon.Line, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Priority categories", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Neon.Text)
            Text(
                "Streams from starred categories are tried first for a game (SD streams still go last). " +
                    "${settings.priorityGroups.size} starred.",
                color = Neon.TextDim, fontSize = 13.sp,
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, label = { M3Text("Filter categories") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, focusedBorderColor = Neon.Purple,
                    unfocusedBorderColor = Neon.Line, focusedLabelColor = Neon.Purple, unfocusedLabelColor = Neon.TextDim, cursorColor = Neon.Purple,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (list.isEmpty()) Text("No categories loaded yet — add a source first.", color = Neon.TextDim, fontSize = 13.sp)
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it }) { g ->
                    val on = PriorityGroups.isOn(g, settings.priorityGroups)
                    NeonSurface(
                        onClick = { app.settings.update { it.copy(priorityGroups = PriorityGroups.toggle(g, it.priorityGroups)) } },
                        modifier = Modifier.fillMaxWidth().then(if (g == list.firstOrNull()) Modifier.focusRequester(focus) else Modifier),
                        radius = 8.dp, selected = on,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (on) Glyph.Star else Glyph.StarOff, if (on) "Priority" else "Not priority", tint = if (on) Neon.Purple else Neon.TextFaint, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(g, fontSize = 15.sp, color = Neon.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Done", selected = true, onClick = onDone)
                NeonButton("Reset to defaults", onClick = { app.settings.update { it.copy(priorityGroups = AppSettings.DEFAULT_PRIORITY_GROUPS) } })
                NeonButton("Clear all", onClick = { app.settings.update { it.copy(priorityGroups = emptyList()) } })
            }
        }
    }
}
