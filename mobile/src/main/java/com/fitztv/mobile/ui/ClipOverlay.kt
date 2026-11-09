package com.fitztv.mobile.ui

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.fitztv.sports.Clip

/** Highlight played over the live game (phone). Ends or ✕ → [onDone]. TextureView so it sits above the game. */
@OptIn(UnstableApi::class)
@Composable
fun MobileClipOverlay(clip: Clip, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(clip.url) {
        ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(clip.url)); prepare(); playWhenReady = true }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onDone() }
            override fun onPlayerError(error: PlaybackException) { onDone() }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    androidx.activity.compose.BackHandler { onDone() }
    Box(modifier.background(Color.Black)) {
        Column(Modifier.align(Alignment.Center)) {
            AndroidView(factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text(clip.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
        }
        IconButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Glyph.Remove, "Back to the game", tint = Color.White) }
    }
}
