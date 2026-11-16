// Desktop stand-ins for the few Android APIs the shared :core files touch, so they compile
// unchanged on the JVM. Only what :core actually uses is here; none of this talks to Android.
package android.content

import java.awt.Desktop
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class Intent(val action: String? = null, val data: android.net.Uri? = null) {
    private var pkg: String? = null
    fun setPackage(p: String) = apply { pkg = p }
    val packagePinned get() = pkg != null
    fun putExtra(k: String, v: String) = this
    fun addFlags(f: Int) = this
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEARCH = "android.intent.action.SEARCH"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    }
}

class AssetManager {
    fun open(name: String): InputStream =
        AssetManager::class.java.classLoader.getResourceAsStream(name) ?: error("missing asset $name")
}

interface SharedPreferences {
    fun getString(key: String, def: String?): String?
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun apply()
    }
}

private class FilePrefs(private val file: File) : SharedPreferences {
    private val map = ConcurrentHashMap<String, String>().also { m ->
        runCatching {
            if (file.exists()) {
                val o = kotlinx.serialization.json.Json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
                o.forEach { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.let { m[k] = it.content } }
            }
        }
    }
    override fun getString(key: String, def: String?) = map[key] ?: def
    override fun edit() = object : SharedPreferences.Editor {
        val pending = HashMap<String, String?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        @Synchronized override fun apply() {
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(kotlinx.serialization.json.JsonObject(map.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }).toString())
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

/** App storage root: %APPDATA%\FitzTV on Windows, ~/.fitztv elsewhere. Credentials never leave it. */
open class Context {
    val filesDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { File(it, "FitzTV") }
            ?: File(System.getProperty("user.home"), ".fitztv")
        base.apply { mkdirs() }
    }
    val cacheDir: File by lazy { File(filesDir, "tmp").apply { mkdirs() } }
    val applicationContext: Context get() = this
    val assets = AssetManager()
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = FilePrefs(File(filesDir, "$name.json"))

    /** Only web links can be "started" on the desktop (opened in the browser); anything else throws, like a missing app. */
    fun startActivity(intent: Intent) {
        val uri = intent.data?.toString()
        if (intent.action == Intent.ACTION_VIEW && uri != null && !intent.packagePinned && (uri.startsWith("https://") || uri.startsWith("http://"))) {
            Desktop.getDesktop().browse(URI(uri))
        } else error("No app for $intent")
    }

    companion object { const val MODE_PRIVATE = 0 }
}
