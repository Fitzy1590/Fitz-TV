package android.net

import java.net.URLDecoder
import java.net.URLEncoder

class Uri private constructor(private val s: String) {
    override fun toString() = s
    companion object {
        fun parse(s: String) = Uri(s)
        fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")
    }
}
