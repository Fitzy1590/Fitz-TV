package com.fitztv.desktop

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File

/** Finds libVLC: the copy bundled with the installer first, then any VLC installed on the PC. */
object VlcLocator {
    private var checked = false
    private var ok = false

    /** Folder shipped with the installer (compose `appResources`), if present. */
    private fun bundled(): File? {
        val res = System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") }
        return res?.takeIf { File(it, "libvlc.dll").exists() }
    }

    @Synchronized fun available(): Boolean {
        if (checked) return ok
        checked = true
        ok = runCatching {
            val b = bundled()
            if (b != null) {
                System.setProperty("jna.library.path", b.absolutePath)
                NativeLibrary.addSearchPath("libvlc", b.absolutePath)
                NativeLibrary.addSearchPath("libvlccore", b.absolutePath)
                System.setProperty("VLC_PLUGIN_PATH", File(b, "plugins").absolutePath)
                true
            } else NativeDiscovery().discover()
        }.getOrDefault(false)
        return ok
    }

    fun factory(vararg args: String): MediaPlayerFactory? =
        if (available()) runCatching { MediaPlayerFactory(*args) }.getOrNull() else null
}
