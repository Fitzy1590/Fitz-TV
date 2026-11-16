package com.fitztv.player

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.fitztv.data.AppSettings
import com.fitztv.desktop.VlcLocator
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

/** The picture: libVLC renders frames into memory and Compose draws them, so controls can overlay the video. */
class VideoView : View() {
    var frame by mutableStateOf<ImageBitmap?>(null)
}

/**
 * Windows playback engine on libVLC (via vlcj): hardware decoding (D3D11/DXVA), MPEG-TS and HLS,
 * the same engine the TV app falls back to. Also stands in for ExoPlayer, which is Android-only.
 */
class VlcEngine(@Suppress("unused") private val context: Context, private val settings: AppSettings, private val cb: EngineCallback) : PlaybackEngine {
    override val name = "VLC"
    private val video = VideoView()
    override val view: View = video

    private val factory: MediaPlayerFactory = VlcLocator.factory(
        "--no-stats", "--no-sub-autodetect-file", "--http-reconnect", "--avcodec-skiploopfilter=0",
        "--audio-time-stretch", "--clock-jitter=0", "--clock-synchro=0", "--no-video-title-show", "--no-osd",
        "--avcodec-hw=any",
    ) ?: error("VLC is not installed")
    private val mp: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    private var url: String? = null
    @Volatile private var released = false
    private var firstFrame = false
    private var buf: ByteArray = ByteArray(0)
    private var bitmap: Bitmap? = null

    private val listener = object : MediaPlayerEventAdapter() {
        override fun buffering(m: MediaPlayer, newCache: Float) = ui { cb.onBuffering(newCache < 100f) }
        override fun playing(m: MediaPlayer) = ui { cb.onPlaying() }
        override fun videoOutput(m: MediaPlayer, newCount: Int) = ui { reportVideo() }
        override fun error(m: MediaPlayer) = ui { cb.onError(ErrorKind.NETWORK, "VLC playback error") }
        override fun finished(m: MediaPlayer) = ui { cb.onError(ErrorKind.ENDED, "Stream ended") }
    }

    init {
        mp.videoSurface().set(factory.videoSurfaces().newVideoSurface(
            object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat = RV32BufferFormat(sourceWidth, sourceHeight)
                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
            },
            RenderCallback { _, buffers, fmt -> draw(buffers[0], fmt) },
            true,
        ))
        mp.events().addMediaPlayerEventListener(listener)
    }

    private fun draw(src: ByteBuffer, fmt: BufferFormat) {
        if (released) return
        val w = fmt.width; val h = fmt.height
        val size = w * h * 4
        if (buf.size != size) buf = ByteArray(size)
        src.rewind(); src.get(buf, 0, size)
        val bmp = Bitmap().apply {
            allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
            installPixels(buf.copyOf())
        }
        bitmap = bmp
        val img = bmp.asComposeImageBitmap()
        SwingUtilities.invokeLater {
            if (!released) {
                video.frame = img
                if (!firstFrame) { firstFrame = true; cb.onFirstFrame() }
            }
        }
    }

    private fun ui(block: () -> Unit) { if (!released) SwingUtilities.invokeLater { if (!released) block() } }

    private fun reportVideo() {
        val d = mp.video().videoDimension() ?: return
        val fps = mp.media().info()?.videoTracks()?.firstOrNull()?.let { t -> if (t.frameRateBase() > 0) t.frameRate().toFloat() / t.frameRateBase() else -1f } ?: -1f
        cb.onVideoInfo(d.width, d.height, fps, "libVLC (hardware)")
    }

    override fun play(url: String) { this.url = url; firstFrame = false; start(url) }

    private fun start(url: String) {
        val opts = arrayOf(
            ":network-caching=${(settings.bufferSeconds.coerceIn(3, 60) * 1000) / 3}",
            ":live-caching=3000",
            ":http-user-agent=${settings.userAgent}",
            ":http-reconnect",
        )
        mp.media().play(url, *opts)
        if (settings.audioBoostDb > 0) {
            // VLC volume goes to 200%; convert the dB boost to a percentage.
            mp.audio().setVolume((100 * Math.pow(10.0, settings.audioBoostDb / 20.0)).toInt().coerceAtMost(200))
        }
    }

    override fun reconnect() { url?.let { firstFrame = false; mp.controls().stop(); start(it) } }
    override fun positionMs(): Long = mp.status().time()
    override fun lowerQuality(): Boolean = false
    override fun restoreQuality() = Unit

    override fun pause() { if (mp.status().isPlaying) mp.controls().setPause(true) }
    override fun resume() { if (!mp.status().isPlaying) mp.controls().setPause(false) }
    override fun seekTo(positionMs: Long): Boolean {
        if (!mp.status().isSeekable) return false
        mp.controls().setTime(positionMs.coerceAtLeast(0))
        return true
    }
    override fun seekToLive() = reconnect()
    override fun timeline() = Timeline(
        positionMs = mp.status().time().coerceAtLeast(0), durationMs = mp.status().length().coerceAtLeast(0),
        seekable = mp.status().isSeekable, live = mp.status().length() <= 0,
    )

    // The controller sends 0..1; the boost set in play() scales the base level, so apply it on top.
    override fun setVolume(volume: Float) {
        val boost = if (settings.audioBoostDb > 0) Math.pow(10.0, settings.audioBoostDb / 20.0) else 1.0
        mp.audio().setVolume((volume * 100 * boost).toInt().coerceIn(0, 200))
    }

    // libVLC: positive delay = audio later, in microseconds.
    override fun setAudioOffsetMs(ms: Int) { runCatching { mp.audio().setDelay(ms * 1000L) } }

    override fun release() {
        released = true
        runCatching { mp.events().removeMediaPlayerEventListener(listener) }
        Thread { runCatching { mp.controls().stop(); mp.release(); factory.release() } }.start()
    }
}

/** ExoPlayer is Android-only: the shared controller asks for it by name, so it gets the same libVLC engine. */
class ExoEngine(
    context: Context,
    settings: AppSettings,
    cb: EngineCallback,
    @Suppress("UNUSED_PARAMETER") multiview: Boolean = false,
    @Suppress("UNUSED_PARAMETER") tuning: StreamTuning = StreamTuning(),
) : PlaybackEngine by VlcEngine(context, settings, cb)
