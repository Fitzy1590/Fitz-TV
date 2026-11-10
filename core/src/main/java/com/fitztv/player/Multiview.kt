package com.fitztv.player

import android.content.Context
import android.content.Intent
import com.fitztv.data.Channel
import com.fitztv.data.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One multiview window: a game (with its ranked streams) or a plain channel. */
data class MultiviewSlot(val id: String, val title: String, val game: Game?, val channels: List<Channel>)

/** The games shown in multiview (up to 4), in screen order; slot 0 is the big one in 3-up. */
object Multiview {
    const val MAX = 4
    private val _slots = MutableStateFlow<List<MultiviewSlot>>(emptyList())
    val slots: StateFlow<List<MultiviewSlot>> = _slots

    /** Multiview screen of the running app (set by the TV / phone app). */
    var activity: Class<out android.app.Activity>? = null

    fun add(slot: MultiviewSlot): Boolean {
        val cur = _slots.value
        if (cur.any { it.id == slot.id }) return true
        if (cur.size >= MAX) return false
        _slots.value = cur + slot
        return true
    }

    fun replace(index: Int, slot: MultiviewSlot) {
        _slots.value = _slots.value.toMutableList().also { if (index in it.indices) it[index] = slot else it += slot }.take(MAX)
    }

    fun remove(index: Int) { _slots.value = _slots.value.filterIndexed { i, _ -> i != index } }

    /** Swap two windows (move a game around the screen). */
    fun swap(a: Int, b: Int) {
        val l = _slots.value.toMutableList()
        if (a !in l.indices || b !in l.indices) return
        val t = l[a]; l[a] = l[b]; l[b] = t
        _slots.value = l
    }

    fun clear() { _slots.value = emptyList() }

    fun open(context: Context) {
        val a = activity ?: return
        context.startActivity(Intent(context, a).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or (if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)))
    }

    fun slotFor(game: Game?, channels: List<Channel>, index: Int = 0): MultiviewSlot? {
        if (channels.isEmpty()) return null
        val ordered = channels.drop(index) + channels.take(index)
        return MultiviewSlot(game?.id ?: ordered.first().id, game?.matchup ?: ordered.first().label, game, ordered)
    }
}
