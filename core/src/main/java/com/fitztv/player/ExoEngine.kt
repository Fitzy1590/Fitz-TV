package com.fitztv.player

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.annotation.OptIn
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fitztv.data.AppSettings
import com.fitztv.data.Http

@OptIn(UnstableApi::class)
class ExoEngine(context: Context, settings: AppSettings, private val cb: EngineCallback) : PlaybackEngine {
    override val name = "ExoPlayer"

    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setExceedRendererCapabilitiesIfNecessary(true)
            .setPreferredAudioLanguage("en")
            .build()
    }

    // Start by assuming a fast connection so adaptive streams open at their top rung.
    private val bandwidth = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(50_000_000L)
        .build()

    private val bufferMs = settings.bufferSeconds.coerceIn(10, 120) * 1000
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ (bufferMs * 2 / 3).coerceAtLeast(8_000),
            /* maxBufferMs = */ bufferMs + 20_000,
            /* bufferForPlaybackMs = */ 1_500,
            /* bufferForPlaybackAfterRebufferMs = */ 4_000,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        // Keep the last minute in memory so quick 3-second rewinds are instant.
        .setBackBuffer(60_000, true)
        .build()

    private val retryPolicy = object : DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 8) {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            minOf(250L * info.errorCount, 2_000L)
    }

    private val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        .setConstantBitrateSeekingEnabled(true)

    private val http = OkHttpDataSource.Factory(Http.client).setUserAgent(settings.userAgent)

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            // Platform (hardware) decoders first; FFmpeg only fills audio-codec gaps.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
            // Decoder runs on its own thread: fewer dropped frames on 50/60 fps sport.
            // (Default only on Android 12+; the Shield is on Android 11.)
            .forceEnableMediaCodecAsynchronousQueueing(),
    )
        .setTrackSelector(trackSelector)
        .setBandwidthMeter(bandwidth)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors)
                .setLoadErrorHandlingPolicy(retryPolicy)
        )
        .setVideoChangeFrameRateStrategy(
            if (settings.matchFrameRate) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        )
        .setWakeMode(C.WAKE_MODE_NETWORK)
        // Proper media audio focus: pauses for phone calls, ducks for notifications.
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    // SurfaceView (PlayerView's default) keeps frames on the hardware overlay path,
    // which is what the Shield's AI upscaler processes.
    override val view: View = PlayerView(context).apply {
        useController = false
        setShutterBackgroundColor(Color.BLACK)
        setKeepContentOnPlayerReset(true)
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        keepScreenOn = true
        player = this@ExoEngine.player
    }

    private var url: String? = null
    private var decoder: String? = null

    /** Optional loudness boost for quiet streams (no effect when bitstreaming to an AV receiver). */
    private val boost: LoudnessEnhancer? = settings.audioBoostDb.takeIf { it > 0 }?.let { db ->
        runCatching {
            val session = Util.generateAudioSessionIdV21(context)
            player.audioSessionId = session
            LoudnessEnhancer(session).apply { setTargetGain(db * 100); enabled = true }
        }.getOrNull()
    }
    private var maxBitrateCap = Int.MAX_VALUE

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> cb.onBuffering(true)
                    Player.STATE_READY -> { cb.onBuffering(false); cb.onPlaying() }
                    Player.STATE_ENDED -> cb.onError(ErrorKind.ENDED, "Stream ended")
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val kind = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> ErrorKind.BEHIND_LIVE
                    in 2000..2999 -> ErrorKind.NETWORK
                    in 3000..4999 -> ErrorKind.UNSUPPORTED
                    else -> ErrorKind.NETWORK
                }
                cb.onError(kind, error.errorCodeName)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) = reportVideo()
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long,
            ) { decoder = decoderName; reportVideo() }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) = reportVideo()
        })
    }

    private fun reportVideo() {
        val f = player.videoFormat ?: return
        cb.onVideoInfo(f.width, f.height, f.frameRate, decoder)
    }

    override fun play(url: String) {
        this.url = url
        maxBitrateCap = Int.MAX_VALUE
        restoreQuality()
        val item = MediaItem.Builder().setUri(url).apply {
            val u = url.substringBefore('?').lowercase()
            when {
                u.endsWith(".m3u8") || u.contains("/hls/") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                u.endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
                u.endsWith(".ts") -> setMimeType(MimeTypes.VIDEO_MP2T)
            }
            setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun reconnect() {
        val u = url ?: return
        if (player.isCurrentMediaItemLive && player.playerError == null) {
            player.seekToDefaultPosition()
            player.prepare()
        } else {
            // Replays resume where they were; live TS restarts at the live edge.
            val resumeAt = if (!player.isCurrentMediaItemLive && player.isCurrentMediaItemSeekable) player.currentPosition else 0L
            val wasPlaying = player.playWhenReady
            val cap = maxBitrateCap
            play(u)
            if (resumeAt > 0) player.seekTo(resumeAt)
            player.playWhenReady = wasPlaying
            if (cap != Int.MAX_VALUE) applyCap(cap)
        }
    }

    override fun pause() { player.playWhenReady = false }
    override fun resume() { player.playWhenReady = true }

    override fun seekTo(positionMs: Long): Boolean {
        if (!player.isCurrentMediaItemSeekable) return false
        val d = player.duration
        player.seekTo(if (d > 0) positionMs.coerceIn(0, d) else positionMs.coerceAtLeast(0))
        return true
    }

    override fun seekToLive() { player.seekToDefaultPosition(); player.playWhenReady = true }

    override fun timeline(): Timeline {
        val d = player.duration
        return Timeline(
            positionMs = player.currentPosition,
            durationMs = if (d > 0) d else 0,
            seekable = player.isCurrentMediaItemSeekable,
            live = player.isCurrentMediaItemLive,
        )
    }

    override fun positionMs(): Long = player.currentPosition

    override fun lowerQuality(): Boolean {
        // Only meaningful for adaptive (HLS/DASH) streams with several video renditions.
        val bitrates = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).bitrate } }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        if (bitrates.size < 2) return false
        val current = player.videoFormat?.bitrate?.takeIf { it > 0 } ?: bitrates.first()
        val next = bitrates.firstOrNull { it < current && it < maxBitrateCap } ?: return false
        applyCap(next)
        return true
    }

    private fun applyCap(bitrate: Int) {
        maxBitrateCap = bitrate
        trackSelector.parameters = trackSelector.buildUponParameters().setMaxVideoBitrate(bitrate).build()
    }

    override fun restoreQuality() {
        maxBitrateCap = Int.MAX_VALUE
        trackSelector.parameters = trackSelector.buildUponParameters().setMaxVideoBitrate(Int.MAX_VALUE).build()
    }

    override fun release() {
        runCatching { boost?.release() }
        (view as PlayerView).player = null
        player.release()
    }
}
