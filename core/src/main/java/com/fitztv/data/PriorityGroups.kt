package com.fitztv.data

import java.text.Normalizer

/**
 * Provider categories (Xtream category / M3U group-title) whose streams rank first.
 * Matching is loose: case, "|" and ":" separators, "+" and superscript tags (ᵛᴵᴾ, ⁸ᴷ) are ignored,
 * and a category matches if its words appear in the channel's group as a whole run.
 */
object PriorityGroups {
    @Volatile private var source: List<String> = emptyList()
    @Volatile private var keys: List<String> = emptyList()

    fun key(s: String): String =
        " " + Normalizer.normalize(s, Normalizer.Form.NFKD).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() + " "

    private fun keysFor(list: List<String>): List<String> {
        if (list !== source) { keys = list.map(::key).filter { it.isNotBlank() && it != "  " }; source = list }
        return keys
    }

    fun matches(c: Channel, list: List<String>): Boolean {
        val k = keysFor(list)
        if (k.isEmpty()) return false
        val g = key(c.group)
        return k.any { g.contains(it) }
    }

    fun isOn(group: String, list: List<String>): Boolean { val g = key(group); return list.any { g.contains(key(it)) } }

    /** Turn a provider category on or off in the priority list. */
    fun toggle(group: String, list: List<String>): List<String> {
        val g = key(group)
        return if (isOn(group, list)) list.filterNot { g.contains(key(it)) } else list + group
    }

    /** Every sports category in your sources (plus saved ones not loaded yet); priority ones first. */
    fun options(channels: List<Channel>, list: List<String>): List<String> {
        val groups = channels.asSequence().filter { !it.vod && it.group.isNotBlank() }.map { it.group }.distinct().toMutableList()
        list.filter { saved -> groups.none { key(it).contains(key(saved)) } }.forEach { groups += it }
        return groups.sortedWith(compareBy<String> { if (isOn(it, list)) 0 else 1 }.thenBy { it.lowercase() })
    }
}
