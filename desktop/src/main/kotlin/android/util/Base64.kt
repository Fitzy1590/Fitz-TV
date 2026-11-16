package android.util

object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    const val NO_PADDING = 1

    fun encodeToString(input: ByteArray, flags: Int): String {
        var enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        return enc.encodeToString(input)
    }
    fun decode(s: String, flags: Int): ByteArray =
        (if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder() else java.util.Base64.getMimeDecoder()).decode(s)
}
