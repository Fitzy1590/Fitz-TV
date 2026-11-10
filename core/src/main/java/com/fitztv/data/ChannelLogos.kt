package com.fitztv.data

import android.content.Context
import java.text.Normalizer

/**
 * Clean channel logos from the public tv-logo/tv-logos collection, matched by channel name.
 * Provider logos are often squashed or mis-cropped; when a name matches here, this logo is used
 * and the provider's is only the fallback. The index ships with the app (names only); images
 * load from raw.githubusercontent.com on demand and are cached by the image loader.
 */
class LogoIndex(lines: List<String>) {
    private class Entry(val country: String, val stem: String)

    private val byKey: Map<String, List<Entry>>

    init {
        val m = HashMap<String, MutableList<Entry>>()
        for (line in lines) {
            val slash = line.indexOf('/')
            if (slash <= 0) continue
            val country = line.substring(0, slash); val stem = line.substring(slash + 1)
            val toks = tokens(stem.replace('-', ' ')).toMutableList()
            if (toks.size >= 2) toks.removeAt(toks.size - 1)          // trailing country code ("-au")
            if (toks.isEmpty()) continue
            m.getOrPut(toks.joinToString("-")) { mutableListOf() }.add(Entry(country, stem))
        }
        byKey = m
    }

    /** Logo URL for a channel name, or null when the collection has none that fits. */
    fun urlFor(name: String): String? {
        var toks = tokens(name)
        // Leading country tag from the provider ("AU", "US").
        if (toks.size > 1 && toks[0] in COUNTRY_TAGS) toks = toks.drop(1)
        // Full name first, then without the first word ("sky espn 2" -> "espn 2").
        val tries = buildList {
            if (toks.isNotEmpty()) add(toks)
            if (toks.size >= 3) add(toks.drop(1))
        }
        for (t in tries) {
            val hit = byKey[t.joinToString("-")] ?: continue
            val e = hit.minByOrNull { c -> PREFERRED.indexOf(c.country).let { if (it < 0) 99 else it } } ?: continue
            return BASE + e.country + "/" + e.stem + ".png"
        }
        return null
    }

    companion object {
        const val BASE = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/"
        private val PREFERRED = listOf("australia", "united-states", "united-kingdom", "new-zealand", "canada", "international", "ireland")
        private val COUNTRY_TAGS = setOf("au", "us", "uk", "nz", "ca", "ie", "usa", "gb")
        private val NOISE = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "raw", "hevc", "h265", "vip", "backup", "tv", "channel", "channels", "live")

        fun tokens(s: String): List<String> =
            Normalizer.normalize(s, Normalizer.Form.NFKD).lowercase()
                .replace(Regex("[^a-z0-9]+"), " ").trim().split(' ')
                .filter { it.isNotEmpty() && it !in NOISE }
    }
}

object ChannelLogos {
    @Volatile private var index: LogoIndex? = null
    @Volatile private var app: Context? = null

    /** Remember the app context and warm the bundled name index in the background. */
    fun init(context: Context) {
        app = context.applicationContext
        Thread { load() }.start()
    }

    @Synchronized private fun load(): LogoIndex? {
        index?.let { return it }
        val c = app ?: return null
        index = runCatching { LogoIndex(c.assets.open("channel_logos.txt").bufferedReader().readLines()) }.getOrNull()
        return index
    }

    fun urlFor(name: String): String? = (index ?: load())?.urlFor(name)
}
