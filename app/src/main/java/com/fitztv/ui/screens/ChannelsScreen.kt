package com.fitztv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitztv.FitzApp
import com.fitztv.data.Channel
import com.fitztv.player.PlaybackQueue
import com.fitztv.sports.Sport
import com.fitztv.ui.components.ChannelCard
import com.fitztv.ui.components.EmptyHint

@Composable
fun ChannelsScreen(channels: List<Channel>, sport: Sport?) {
    val context = LocalContext.current
    val epg by FitzApp.instance.repo.epg.collectAsState()
    val list = remember(channels, sport) {
        channels.filter { sport == null || it.sport == sport }
            .sortedWith(compareBy<Channel> { it.group }.thenBy { it.variantKey }.thenByDescending { it.quality })
    }
    if (list.isEmpty()) {
        EmptyHint("No channels", "Add a source in Settings, or pick a different sport.")
        return
    }
    val now = System.currentTimeMillis()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(list, key = { it.id }) { c ->
            val p = c.epgId?.let { id -> epg[id]?.firstOrNull { now in it.startMillis until it.stopMillis } }
            ChannelCard(c, p, onClick = { PlaybackQueue.launch(context, list, list.indexOf(c)) })
        }
    }
}
