package com.fitztv.player

import android.view.View

enum class ErrorKind { NETWORK, BEHIND_LIVE, UNSUPPORTED, ENDED }

interface EngineCallback {
    fun onBuffering(buffering: Boolean)
    fun onPlaying()
    fun onError(kind: ErrorKind, message: String)
    fun onVideoInfo(width: Int, height: Int, fps: Float, decoder: String?)
    /** A picture actually reached the screen (not just a format announced by the playlist). */
    fun onFirstFrame() {}
}

/** Where playback is in the stream: used for the progress bar and seeking. */
data class Timeline(
    val positionMs: Long = 0,
    /** Length of the seekable window (live DVR window, archive or VOD); 0 if unknown. */
    val durationMs: Long = 0,
    val seekable: Boolean = false,
    val live: Boolean = true,
)

/** Common surface over ExoPlayer and libVLC. Both render to a SurfaceView with hardware decoding. */
interface PlaybackEngine {
    val name: String
    val view: View
    fun play(url: String)
    /** Re-open the current stream at the live edge. */
    fun reconnect()
    fun positionMs(): Long
    /** Step down one adaptive rung. Returns false when the stream has nothing lower. */
    fun lowerQuality(): Boolean
    fun restoreQuality()
    fun release()

    fun pause()
    fun resume()
    /** Seek within the stream; false if this stream can't seek. */
    fun seekTo(positionMs: Long): Boolean
    /** Jump to the live edge of a live stream. */
    fun seekToLive()
    fun timeline(): Timeline
    /** 0 = muted, 1 = full (multiview: only the selected tile has sound). */
    fun setVolume(volume: Float)
    /** Whether this engine pauses when another app takes audio focus (off in picture-in-picture so both keep playing). */
    fun setHandleAudioFocus(handle: Boolean) {}
    /** Bitrate of the video being played, if the stream says (HLS renditions do). */
    fun bitrateKbps(): Int? = null
    /** Audio/video sync: positive = audio later, negative = audio earlier (milliseconds). */
    fun setAudioOffsetMs(ms: Int) {}
}
