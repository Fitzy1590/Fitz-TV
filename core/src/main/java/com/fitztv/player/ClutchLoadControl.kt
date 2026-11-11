package com.fitztv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.fitztv.data.adapters.Situation

enum class BufferMode { NORMAL, CLUTCH, BREAK }

/** Buffer thresholds for one mode, in microseconds (what Media3 compares against). */
data class BufferProfile(val minUs: Long, val maxUs: Long, val startUs: Long, val rebufferUs: Long)

/** Which mode a game situation calls for, and the thresholds for each. Pure, so it is unit-tested. */
object ClutchPolicy {
    /** Close-game window: 4th quarter / overtime with the margin at most this many points. */
    const val CLUTCH_DELTA = 8
    /** Leaves clutch only when the margin passes this, so a 8↔9 point swing doesn't flap the buffer. */
    const val CLUTCH_EXIT_DELTA = 10

    fun next(current: BufferMode, s: Situation): BufferMode = when {
        !s.live -> BufferMode.NORMAL
        s.inBreak -> BufferMode.BREAK
        s.periodic && s.period >= 4 && s.scoreDelta <= (if (current == BufferMode.CLUTCH) CLUTCH_EXIT_DELTA else CLUTCH_DELTA) -> BufferMode.CLUTCH
        else -> BufferMode.NORMAL
    }

    /**
     * [baseMinMs]/[baseMaxMs]/[startMs]/[rebufferMs] are the normal settings. [safety] is the self-heal
     * buffer factor: a stream that keeps stalling never goes below a proportionally higher floor.
     */
    fun profile(mode: BufferMode, baseMinMs: Int, baseMaxMs: Int, startMs: Int, rebufferMs: Int, safety: Double = 1.0): BufferProfile {
        fun us(ms: Long) = ms * 1_000
        return when (mode) {
            BufferMode.NORMAL -> BufferProfile(us(baseMinMs.toLong()), us(baseMaxMs.toLong()), us(startMs.toLong()), us(rebufferMs.toLong()))
            // Minimum safe buffer: a few seconds, never above the normal settings.
            BufferMode.CLUTCH -> BufferProfile(
                us(minOf((3_000 * safety).toLong(), baseMinMs.toLong())),
                us(minOf((8_000 * safety).toLong(), baseMaxMs.toLong())),
                us(minOf((1_000 * safety).toLong(), startMs.toLong())),
                us(minOf((2_500 * safety).toLong(), rebufferMs.toLong())),
            )
            // Break: fill up to the normal ceiling and beyond, so the next stretch of play has headroom.
            BufferMode.BREAK -> BufferProfile(
                us(baseMaxMs.toLong()),
                us((baseMaxMs * 3L / 2 + 10_000).coerceAtMost(120_000)),
                us(startMs.toLong()), us(rebufferMs.toLong()),
            )
        }
    }
}

/**
 * Media3 load control that follows the game: close finish -> minimum safe buffer (nearest to live),
 * halftime / timeout / end of period -> larger buffer. Outside a live ESPN game it behaves exactly
 * like [DefaultLoadControl] with the normal thresholds.
 */
@OptIn(UnstableApi::class)
class ClutchLoadControl(
    private val baseMinMs: Int,
    private val baseMaxMs: Int,
    private val startMs: Int,
    private val rebufferMs: Int,
    private val safety: Double = 1.0,
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    baseMinMs, baseMaxMs, startMs, rebufferMs,
    C.LENGTH_UNSET, /* prioritizeTimeOverSizeThresholds = */ true,
    /* backBufferDurationMs = */ 60_000, /* retainBackBufferFromKeyframe = */ true,
) {
    @Volatile var mode: BufferMode = BufferMode.NORMAL
        private set
    private var loading = false

    /** Feed the latest game situation (any thread). */
    fun update(s: Situation) { mode = ClutchPolicy.next(mode, s) }

    private fun profile(m: BufferMode) = ClutchPolicy.profile(m, baseMinMs, baseMaxMs, startMs, rebufferMs, safety)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val m = mode
        if (m == BufferMode.NORMAL) return super.shouldContinueLoading(parameters)
        val p = profile(m)
        val buffered = parameters.bufferedDurationUs
        loading = when {
            buffered < p.minUs -> true
            buffered >= p.maxUs || allocator.totalBytesAllocated >= SIZE_CAP_BYTES -> false
            else -> loading
        }
        return loading
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val m = mode
        if (m == BufferMode.NORMAL) return super.shouldStartPlayback(parameters)
        val p = profile(m)
        var need = if (parameters.rebuffering) p.rebufferUs else p.startUs
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) need = minOf(need, parameters.targetLiveOffsetUs / 2)
        return need <= 0 || parameters.bufferedDurationUs >= need
    }

    private companion object { const val SIZE_CAP_BYTES = 96 * 1024 * 1024 }
}
