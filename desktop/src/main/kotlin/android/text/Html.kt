package android.text

/** Enough of Html.fromHtml for YouTube titles: strips tags and decodes the common entities. */
object Html {
    const val FROM_HTML_MODE_LEGACY = 0
    fun fromHtml(s: String, flags: Int): CharSequence =
        s.replace(Regex("<[^>]*>"), "")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
}
