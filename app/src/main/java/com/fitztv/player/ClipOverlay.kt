package com.fitztv.player

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.Text
import com.fitztv.sports.Clip
import com.fitztv.ui.theme.Neon

/**
 * A highlight clip played over the live game. The game keeps playing underneath (muted by the
 * caller); the clip ends or Back → [onDone], and the caller restores the game's sound.
 * Drawn on a TextureView so it sits above the game's SurfaceView.
 */
@OptIn(UnstableApi::class)
@Composable
fun ClipOverlay(clip: Clip, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(clip.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clip.url)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onDone() }
            override fun onPlayerError(error: PlaybackException) { onDone() }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    Column(
        modifier.background(Neon.Bg, RoundedCornerShape(12.dp)).border(2.dp, Neon.Purple, RoundedCornerShape(12.dp)).padding(8.dp),
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Text(clip.title, color = Neon.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("Back returns to the game", color = Neon.TextFaint, fontSize = 12.sp)
    }
}
