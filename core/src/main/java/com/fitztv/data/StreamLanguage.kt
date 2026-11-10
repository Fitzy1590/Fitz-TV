package com.fitztv.data

import com.fitztv.sports.Text
import java.util.concurrent.ConcurrentHashMap

/**
 * Commentary language guessed from the channel name and group, used to put English
 * streams first. IPTV lists mark language with a country prefix ("US: ESPN", "UK| Sky Sports",
 * "[AR] beIN 1") or a word ("ESPN Deportes", "Arabic"). Returns a tier:
 * 0 = English, 1 = unknown, 2 = another language.
 */
object StreamLanguage {
    const val ENGLISH = 0
    const val UNKNOWN = 1
    const val OTHER = 2

    private val cache = ConcurrentHashMap<String, Int>()

    // Country / language codes as list prefixes or bracketed tags.
    private val englishCodes = setOf("us", "usa", "uk", "gb", "en", "eng", "au", "aus", "ca", "can", "nz", "ie", "irl", "za")
    private val otherCodes = setOf(
        "ar", "ara", "fr", "fra", "es", "esp", "spa", "mx", "lat", "latam", "latino", "de", "ger", "deu", "at", "ch",
        "it", "ita", "pt", "por", "br", "bra", "tr", "tur", "pl", "pol", "nl", "ned", "be", "ru", "rus", "ua", "gr",
        "ro", "hu", "cz", "sk", "rs", "srb", "hr", "ba", "mk", "si", "al", "alb", "bg", "se", "swe", "no", "nor",
        "dk", "fi", "fin", "il", "ir", "pk", "af", "kr", "jp", "cn", "vn", "th", "id", "my", "ph", "exyu", "ex yu",
        "arg", "co", "cl", "pe", "ve",
    )
    private val tag = Regex("""^\s*[\[(|]?\s*([a-z]{2,6}(?: [a-z]{2})?)\s*[\])|:\-]""")
    private val bracketed = Regex("""[\[(|]\s*([a-z]{2,6})\s*[\])|]""")

    // Whole words anywhere in the name / group.
    private val otherWords = listOf(
        "arabic", "french", "francais", "spanish", "espanol", "castellano", "deportes", "latino", "german", "deutsch",
        "italian", "italiano", "italia", "portuguese", "portugues", "brasil", "turkish", "turk", "polish", "polska",
        "dutch", "russian", "greek", "romanian", "hungarian", "czech", "serbian", "croatian", "albanian", "bulgarian",
        "swedish", "norwegian", "danish", "finnish", "hebrew", "persian", "farsi", "hindi", "tamil", "telugu", "urdu",
        "bangla", "kurdish", "chinese", "korean", "japanese", "vietnamese", "thai", "malay", "indonesia",
        "tudn", "univision", "telemundo", "movistar", "tyc sports", "ssc", "ad sports", "alkass", "al kass",
        "match tv", "arena sport", "sport tv", "sky italia", "sky de", "rmc sport", "canal", "eleven sports pt",
        "bein sports ar", "bein ar", "mexico", "argentina", "france", "espana", "spain", "germany", "portugal", "turkiye", "arabia", "ziggo", "viaplay", "polsat", "nova sport", "supertennis", "teledeporte",
    )
    private val englishWords = listOf(
        "english", "eng", "sky sports", "tnt sports", "bt sport", "premier sports", "espn", "fox sports", "fox footy",
        "fox cricket", "foxtel", "kayo", "optus sport", "stan sport", "nba tv", "nfl network", "redzone", "red zone",
        "willow", "tsn", "sportsnet", "nbc", "cbs", "abc", "peacock", "supersport", "sky sport nz", "bein sports au",
        "bein sports usa", "bein sports english", "seven", "channel 9", "nine", "ten", "sbs",
    ).map { Text.norm(it) }
    private val otherNorm = otherWords.map { Text.norm(it) }

    fun tier(c: Channel): Int = cache.getOrPut(c.id) { compute(c.name, c.group) }

    fun isEnglish(c: Channel) = tier(c) == ENGLISH

    internal fun compute(name: String, group: String): Int {
        for (raw in listOf(name, group)) {
            val s = raw.lowercase()
            val code = tag.find(s)?.groupValues?.get(1) ?: bracketed.find(s)?.groupValues?.get(1)
            if (code != null) {
                if (code in englishCodes) return ENGLISH
                if (code in otherCodes) return OTHER
            }
        }
        val n = Text.norm("$name $group")
        // A foreign word wins over an English broadcaster name ("ESPN Deportes", "Fox Sports Mexico").
        if (otherNorm.any { n.contains(it) }) return OTHER
        if (englishWords.any { n.contains(it) }) return ENGLISH
        return UNKNOWN
    }
}
