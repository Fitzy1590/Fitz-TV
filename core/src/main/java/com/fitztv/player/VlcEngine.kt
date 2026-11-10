package com.fitztv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import com.fitztv.data.AppSettings
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** libVLC fallback: copes with odd MPEG-TS muxing and codecs ExoPlayer rejects. */
class VlcEngine(context: Context, private val settings: AppSettings, private val cb: EngineCallback) : PlaybackEngine {
    override val name = "VLC"

    private val main = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(
        context,
        arrayListOf(
            "--no-stats",
            "--no-sub-autodetect-file",
            "--http-reconnect",
            "--avcodec-skiploopfilter=0",
            "--audio-time-stretch",
            "--clock-jitter=0",
            "--clock-synchro=0",
        )
    )
    private val mp = MediaPlayer(libVlc)
    private val layout = VLCVideoLayout(context).apply { keepScreenOn = true }
    override val view: View = layout

    private var attached = false
    private var pending: String? = null
    private var url: String? = null

    init {
        layout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!attached) {
                    // useTextureView = false -> SurfaceView, hardware overlay, AI upscaling friendly.
                    mp.attachViews(layout, null, false, false)
                    attached = true
                }
                pending?.let { pending = null; start(it) }
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        mp.setEventListener { e ->
            main.post {
                when (e.type) {
                    MediaPlayer.Event.Buffering -> cb.onBuffering(e.buffering < 100f)
                    MediaPlayer.Event.Playing -> cb.onPlaying()
                    MediaPlayer.Event.Vout -> { reportVideo(); if (e.voutCount > 0) cb.onFirstFrame() }
                    MediaPlayer.Event.EncounteredError -> cb.onError(ErrorKind.NETWORK, "VLC playback error")
                    MediaPlayer.Event.EndReached -> cb.onError(ErrorKind.ENDED, "Stream ended")
                    else -> Unit
                }
            }
        }
    }

    private fun reportVideo() {
        val t = mp.currentVideoTrack ?: return
        val fps = if (t.frameRateDen > 0) t.frameRateNum.toFloat() / t.frameRateDen else -1f
        cb.onVideoInfo(t.width, t.height, fps, "MediaCodec (VLC)")
    }

    override fun play(url: String) {
        this.url = url
        if (!attached) { pending = url; return }
        start(url)
    }

    private fun start(url: String) {
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=${(settings.bufferSeconds.coerceIn(3, 60) * 1000) / 3}")
            addOption(":live-caching=3000")
            addOption(":http-user-agent=${settings.userAgent}")
            addOption(":http-reconnect")
        }
        mp.media = media
        media.release()
        if (settings.audioBoostDb > 0) {
            // VLC volume goes to 200%; convert the dB boost to a percentage.
            mp.volume = (100 * Math.pow(10.0, settings.audioBoostDb / 20.0)).toInt().coerceAtMost(200)
        }
        mp.play()
    }

    override fun reconnect() { url?.let { mp.stop(); start(it) } }
    override fun positionMs(): Long = mp.time
    override fun lowerQuality(): Boolean = false
    override fun restoreQuality() = Unit

    override fun pause() { if (mp.isPlaying) mp.pause() }
    override fun resume() { if (!mp.isPlaying) mp.play() }
    override fun seekTo(positionMs: Long): Boolean {
        if (!mp.isSeekable) return false
        mp.time = positionMs.coerceAtLeast(0)
        return true
    }
    override fun seekToLive() = reconnect()
    override fun timeline() = Timeline(
        positionMs = mp.time.coerceAtLeast(0), durationMs = mp.length.coerceAtLeast(0),
        seekable = mp.isSeekable, live = mp.length <= 0,
    )

    override fun setVolume(volume: Float) { mp.volume = (volume * 100).toInt() }

    // libVLC: positive delay = audio later, in microseconds.
    override fun setAudioOffsetMs(ms: Int) { runCatching { mp.setAudioDelay(ms * 1000L) } }

    override fun release() {
        mp.setEventListener(null)
        mp.stop()
        if (attached) mp.detachViews()
        mp.release()
        libVlc.release()
    }
}
