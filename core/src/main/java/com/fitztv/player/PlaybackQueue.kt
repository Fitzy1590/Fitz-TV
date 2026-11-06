package com.fitztv.player

import android.content.Context
import android.content.Intent
import com.fitztv.data.Channel

/** In-memory hand-off of the zap list to the player (too big for an Intent). */
object PlaybackQueue {
    var channels: List<Channel> = emptyList()
        private set
    var index: Int = 0
    var gameId: String? = null
        private set

    fun launch(context: Context, channels: List<Channel>, index: Int = 0, gameId: String? = null) {
        if (channels.isEmpty()) return
        this.channels = channels
        this.index = index.coerceIn(0, channels.lastIndex)
        this.gameId = gameId
        context.startActivity(
            Intent(context, com.fitztv.FitzApp.instance.playerActivity).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or (if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
        )
    }

    fun replace(channels: List<Channel>, index: Int, gameId: String?) {
        this.channels = channels
        this.index = index.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
        this.gameId = gameId
    }
}
