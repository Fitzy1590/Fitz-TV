package com.fitztv.sports

import java.text.Normalizer

object Text {
    private val accents = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val nonAlnum = "[^a-z0-9+]+".toRegex()

    private val normCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Lowercase, accent-free, punctuation collapsed to single spaces, padded with spaces.
     * Memoised: the same team / channel names are normalised constantly while the UI redraws.
     */
    fun norm(s: String): String {
        normCache[s]?.let { return it }
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(accents, "")
        val out = " " + n.replace(nonAlnum, " ").trim() + " "
        if (normCache.size > 50_000) normCache.clear()
        normCache[s] = out
        return out
    }

    /** Whole-word / phrase containment on normalised strings. */
    fun has(normHaystack: String, phrase: String): Boolean {
        val p = norm(phrase)
        return p.isNotBlank() && p != "  " && normHaystack.contains(p)
    }

    private val qualityTokens = listOf(
        "8k" to 4, "4k" to 4, "4320p" to 4, "3840p" to 4, "3840" to 4, "uhd" to 4, "2160p" to 4, "2160" to 4, "fhd" to 3, "full hd" to 3, "fullhd" to 3,
        "1080p" to 3, "1080i" to 3, "1080" to 3, "1440p" to 3, "2560p" to 3, "1920p" to 3, "1920" to 3, "hevc" to 3, "h265" to 3,
        "hd" to 2, "720p" to 2, "720" to 2, "1280p" to 2, "1280" to 2, "hq" to 2,
        "sd" to 1, "576p" to 1, "540p" to 1, "480p" to 1, "360p" to 1, "lq" to 1, "low" to 1, "mobile" to 1, "3g" to 1,
    )

    private val nonAlnumAscii = Regex("[^a-z0-9]+")

    /**
     * Quality tag in a channel name, or null when it has none. Compatibility-normalised first so
     * superscript tags providers use ("³⁸⁴⁰ᴾ", "ᶠᴴᴰ", "⁸ᴷ") read as 3840p / FHD / 8K.
     */
    fun qualityTag(name: String): Int? {
        val n = " " + Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase().replace(nonAlnumAscii, " ").trim() + " "
        return qualityTokens.firstOrNull { n.contains(" ${it.first} ") }?.second
    }

    fun quality(name: String): Int = qualityTag(name) ?: 2

    /** Channel name with quality markers removed, for grouping variants of one feed. */
    fun variantKey(name: String): String {
        var n = norm(name)
        qualityTokens.forEach { n = n.replace(" ${it.first} ", " ") }
        listOf(" raw ", " backup ", " alt ", " multi audio ", " 50fps ", " 60fps ").forEach { n = n.replace(it, " ") }
        return n.replace("\\s+".toRegex(), " ").trim()
    }

    fun classify(name: String, group: String): Sport? {
        val n = norm(name)
        val g = norm(group)
        // Specific sport in the channel name wins, then the group title, then generic sports.
        for (hay in listOf(n, g)) {
            Sport.entries.filter { it != Sport.GENERAL }.forEach { sport ->
                if (sport.keywords.any { has(hay, it) }) return resolveFootball(sport, n + g)
            }
        }
        if (Sport.GENERAL.keywords.any { has(n, it) || has(g, it) }) return Sport.GENERAL
        return null
    }

    /** "Football" means soccer unless the US/Aussie context says otherwise. */
    private fun resolveFootball(sport: Sport, hay: String): Sport {
        if (sport != Sport.SOCCER) return sport
        if (has(hay, "nfl") || has(hay, "college football") || has(hay, "ncaaf")) return Sport.NFL
        if (has(hay, "afl")) return Sport.AFL
        return sport
    }

    // ---- display-name cleanup ---------------------------------------------------------

    private val nameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val countryPrefix = Regex("""^\s*[\[(|]?\s*[A-Z]{2,3}\s*[\])|:]\s*[|:\-]?\s*""")
    private val eventPrefix = Regex(
        """^\s*(NBA|NFL|NHL|MLB|AFL|NBL|BBL|EPL|UFC|NCAAF|NCAAB|MLS|NRL|ESPN\+?|PPV|EVENTS?|LIVE|DAZN|FOX|SPORTS?|GAME|MATCH)""" +
            """\s*(PASS|LEAGUE PASS|GAME ?PASS|PLUS)?\s*\d{0,3}\s*[:|\-–]\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val times = Regex(
        """[@|\-–]?\s*[(\[]?\s*(\d{1,2}[:.]\d{2}\s*(am|pm)?|\d{1,2}\s*(am|pm))\s*(ET|EST|EDT|PT|PST|CT|CST|UK|GMT|BST|AEST|AEDT|ACDT|ACST|UTC)?\s*[)\]]?""",
        RegexOption.IGNORE_CASE,
    )
    private val dates = Regex("""[(\[]?\b\d{1,2}[/.\-]\d{1,2}([/.\-]\d{2,4})?\b[)\]]?""")
    private val qualityWords = Regex(
        """[(\[]?\b(FHD|UHD|HD|SD|4K|8K|HEVC|H\.?265|H\.?264|1080[pi]?|720p|2160p|50 ?fps|60 ?fps|RAW|HDR|LQ|VIP|BACKUP|ALT)\b[)\]]?""",
        RegexOption.IGNORE_CASE,
    )
    private val versus = Regex("""\s+(@|v|vs\.?|versus)\s+""", RegexOption.IGNORE_CASE)
    private val junk = Regex("""[★☆◉●▶►✦⚡]+|ᴴᴰ|ᶠᴴᴰ|ᵁᴴᴰ|ˢᴰ""")
    private val emptyBrackets = Regex("""[(\[]\s*[)\]]""")

    /** "US| NBA 01: Houston Rockets @ LA Lakers 7:30PM ET [FHD]" -> "Houston Rockets vs LA Lakers". */
    fun cleanName(raw: String): String = nameCache.getOrPut(raw) {
        var n = raw.replace(junk, " ")
        repeat(2) { n = n.replace(countryPrefix, "") }
        n = n.replace(qualityWords, " ").replace(emptyBrackets, " ")
        val withoutEvent = n.replace(eventPrefix, "")
        val isEvent = versus.containsMatchIn(withoutEvent)
        if (isEvent || withoutEvent.isNotBlank() && withoutEvent.length < n.length && withoutEvent.length > 3) {
            n = withoutEvent
        }
        if (isEvent) {
            n = n.replace(times, " ").replace(dates, " ")
            n = n.replace(versus, " vs ")
        }
        n = n.replace(emptyBrackets, " ").replace(Regex("\\s+"), " ").trim().trim('-', ':', '|', '–', '@', ',', '.').trim()
        if (n.isBlank()) raw.trim() else titleCase(n)
    }

    /** SHOUTY CHANNEL NAMES -> Title Case, keeping short acronyms (ESPN, FOX, NBA) as-is. */
    private fun titleCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return s
        return s.split(' ').joinToString(" ") { w ->
            if (w.length <= 4 || w.any { it.isDigit() }) w else w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
