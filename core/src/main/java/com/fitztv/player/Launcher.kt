package com.fitztv.player

import android.content.Context
import com.fitztv.FitzApp
import com.fitztv.data.Game
import com.fitztv.data.Repository

/** One place that knows how to start watching a game (ranked IPTV streams). */
object Launcher {
    /** Returns false when nothing could be found for the game. */
    suspend fun play(context: Context, game: Game): Boolean = when (val r = FitzApp.instance.repo.route(context, game)) {
        is Repository.Route.Streams -> { PlaybackQueue.launch(context, r.ranked, 0, game.id); true }
        Repository.Route.None -> false
    }

    /** Add a game to multiview and open it. */
    suspend fun addToMultiview(context: Context, game: Game): Boolean {
        val ranked = FitzApp.instance.repo.rankedStreams(game)
        val slot = Multiview.slotFor(game, ranked) ?: return false
        if (!Multiview.add(slot)) Multiview.replace(Multiview.MAX - 1, slot)
        Multiview.open(context)
        return true
    }
}
