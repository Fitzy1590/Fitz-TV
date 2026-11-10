package com.fitztv.data

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns which channel categories ("US| NBA", "UK| SKY SPORTS") you actually watch for a league,
 * from watch time. Time decays with a 30-day half-life, so a provider category that stopped
 * working fades out and a new one takes over after a few games.
 */
class CategoryLearner(initial: Map<String, Map<String, Stat>> = emptyMap()) {
    /** Watched seconds (decayed to [at]) for one category. */
    @Serializable
    data class Stat(val seconds: Double = 0.0, val at: Long = 0)

    /** scope ("lg:<league>" / "sp:<sport>") -> category key -> watch time. */
    private val data = ConcurrentHashMap<String, MutableMap<String, Stat>>().also { m -> initial.forEach { (k, v) -> m[k] = ConcurrentHashMap(v) } }

    fun snapshot(): Map<String, Map<String, Stat>> = data.mapValues { it.value.toMap() }

    private fun decayed(s: Stat, now: Long): Double =
        s.seconds * Math.pow(0.5, ((now - s.at).coerceAtLeast(0)) / (30.0 * 86_400_000.0))

    fun add(scopes: List<String>, category: String, seconds: Double, now: Long = System.currentTimeMillis()) {
        for (sc in scopes) {
            val m = data.getOrPut(sc) { ConcurrentHashMap() }
            val old = m[category]
            m[category] = Stat((if (old == null) 0.0 else decayed(old, now)) + seconds, now)
            // Keep each scope small: drop the weakest once there are more than 30 categories.
            if (m.size > 30) m.entries.minByOrNull { decayed(it.value, now) }?.let { m.remove(it.key) }
        }
    }

    /** 0..4 from decayed watch minutes: <4, 4, 15, 45, 120+. The league's own history first, else the sport's. */
    fun level(scopes: List<String>, category: String, now: Long = System.currentTimeMillis()): Int {
        for (sc in scopes) {
            val m = data[sc] ?: continue
            if (m.isEmpty()) continue
            val mins = m[category]?.let { decayed(it, now) / 60.0 } ?: 0.0
            // A league with learned history decides; an empty league falls through to its sport.
            return when { mins >= 120 -> 4; mins >= 45 -> 3; mins >= 15 -> 2; mins >= 4 -> 1; else -> 0 }
        }
        return 0
    }
}
