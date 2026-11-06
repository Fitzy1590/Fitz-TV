package com.fitztv.shield

import android.content.Context
import android.content.pm.PackageManager
import dadb.AdbKeyPair
import dadb.AdbShellPacket
import dadb.Dadb
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Talks ADB (over the network) to the Shield and runs the device-side engine
 * (assets/fitz.sh). Works from a phone (Shield's IP) or on the Shield itself
 * (127.0.0.1), because the engine runs as the ADB shell user, which is allowed
 * to disable packages and change system settings.
 */
object Engine {
    const val DIR = "/data/local/tmp/fitztv"
    const val PORT = 5555
    const val DEFAULT_REMOTE_HOST = "192.168.50.239"

    val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var app: Context
    private var dadb: Dadb? = null
    private var dadbHost: String? = null
    private var enginePushed = false

    fun init(ctx: Context) { if (!::app.isInitialized) app = ctx.applicationContext }

    fun isTv(ctx: Context) = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("fitz", Context.MODE_PRIVATE)
    fun pref(ctx: Context, key: String, def: String): String = prefs(ctx).getString(key, def) ?: def
    fun pref(ctx: Context, key: String, def: Boolean): Boolean = prefs(ctx).getBoolean(key, def)
    fun save(ctx: Context, key: String, v: String) = prefs(ctx).edit().putString(key, v).apply()
    fun save(ctx: Context, key: String, v: Boolean) = prefs(ctx).edit().putBoolean(key, v).apply()
    fun host(ctx: Context) = pref(ctx, "host", if (isTv(ctx)) "127.0.0.1" else DEFAULT_REMOTE_HOST).trim()

    private fun keyPair(): AdbKeyPair {
        val priv = File(app.filesDir, "adbkey")
        val pub = File(app.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    @Synchronized
    fun connection(host: String): Dadb {
        dadb?.let { if (dadbHost == host) return it }
        reset()
        // socketTimeout 0: wait as long as needed for "Allow debugging?" on the TV.
        val d = Dadb.create(host, PORT, keyPair(), 10_000, 0)
        try { d.shell("echo ok") } catch (e: Exception) { runCatching { d.close() }; throw e }
        dadb = d; dadbHost = host; enginePushed = false
        return d
    }

    @Synchronized
    fun reset() {
        runCatching { dadb?.close() }
        dadb = null; dadbHost = null; enginePushed = false
    }

    private fun pushEngine(d: Dadb) {
        if (enginePushed) return
        d.shell("mkdir -p $DIR")
        for (name in listOf("fitz.sh", "packages.txt")) {
            val f = File(app.cacheDir, name)
            app.assets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } }
            d.push(f, "$DIR/$name", 420 /* 0644 */, System.currentTimeMillis())
        }
        enginePushed = true
    }

    /** Runs `sh fitz.sh <args>` for each entry, streaming output lines. Returns last exit code. */
    fun run(host: String, vararg engineArgs: String, out: (String) -> Unit): Int {
        // Retry only the connect/setup. Never re-run engine steps that may already have started.
        val d = withRetry { connection(host).also { pushEngine(it) } }
        val cmd = engineArgs.joinToString("; ") { "FITZ_DIR=$DIR sh $DIR/fitz.sh $it" }
        d.openShell(cmd).use { s ->
            val buf = StringBuilder()
            while (true) {
                when (val p = s.read()) {
                    is AdbShellPacket.Exit -> {
                        if (buf.isNotEmpty()) out(buf.toString())
                        return p.payload.firstOrNull()?.toInt() ?: 0
                    }
                    else -> {
                        buf.append(String(p.payload, Charsets.UTF_8))
                        var nl = buf.indexOf("\n")
                        while (nl >= 0) {
                            out(buf.substring(0, nl)); buf.delete(0, nl + 1); nl = buf.indexOf("\n")
                        }
                    }
                }
            }
        }
    }

    fun shell(host: String, command: String): String =
        withRetry { connection(host).shell(command).allOutput.trim() }

    fun install(host: String, apk: File) = withRetry { connection(host).install(apk) }

    /** A cached connection can go stale (Shield slept, Wi-Fi blip): reconnect once. */
    private fun <T> withRetry(block: () -> T): T = try { block() } catch (e: IOException) { reset(); block() }

    fun explain(e: Throwable, host: String): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("ECONNREFUSED") || m.contains("refused", true) ->
                "Couldn't connect to $host:$PORT. On the Shield: Settings > Device Preferences > Developer options > turn ON 'Network debugging'."
            m.contains("ECONNABORTED") || m.contains("failed to connect", true) ->
                "Lost the connection to $host. The Shield may have gone to sleep or Wi-Fi dropped: wake the Shield, then tap 'Connect & check status'. Anything already applied stays applied."
            m.contains("timed out", true) || m.contains("EHOSTUNREACH") || m.contains("ENETUNREACH") ->
                "No answer from $host. Check the IP and that both devices are on the same network."
            else -> "Error: $m"
        }
    }
}
