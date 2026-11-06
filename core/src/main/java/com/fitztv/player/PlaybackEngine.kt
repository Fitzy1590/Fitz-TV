package com.fitztv.player

import android.view.View

enum class ErrorKind { NETWORK, BEHIND_LIVE, UNSUPPORTED, ENDED }

interface EngineCallback {
    fun onBuffering(buffering: Boolean)
    fun onPlaying()
    fun onError(kind: ErrorKind, message: String)
    fun onVideoInfo(width: Int, height: Int, fps: Float, decoder: String?)
}

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
}
