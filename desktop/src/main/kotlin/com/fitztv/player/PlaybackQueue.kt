package com.fitztv.player

import com.fitztv.data.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The zap list the player works through; [open] says whether the full-screen player is showing. */
object PlaybackQueue {
    var channels: List<Channel> = emptyList()
        private set
    var index: Int = 0
    var gameId: String? = null
        private set

    /** Set by "From start": the player rewinds to kick-off once the stream is playing. */
    @Volatile var fromStartPending = false

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    fun launch(channels: List<Channel>, index: Int = 0, gameId: String? = null) {
        if (channels.isEmpty()) return
        this.channels = channels
        this.index = index.coerceIn(0, channels.lastIndex)
        this.gameId = gameId
        _open.value = true
    }

    fun close() { _open.value = false }

    fun replace(channels: List<Channel>, index: Int, gameId: String?) {
        this.channels = channels
        this.index = index.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
        this.gameId = gameId
    }
}
