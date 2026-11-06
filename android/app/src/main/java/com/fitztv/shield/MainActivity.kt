package com.fitztv.shield

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.zip.ZipInputStream

class MainActivity : Screen() {
    private lateinit var log: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var hostEdit: EditText
    private lateinit var dnsBox: CheckBox
    private lateinit var dnsEdit: EditText
    private lateinit var optionalBox: CheckBox
    private lateinit var optimizeBox: CheckBox
    private lateinit var maintBox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wide = resources.configuration.screenWidthDp >= 720
        val c = column()

        c.addView(text("Fitz-TV Optimizer", 26f, bold = true))
        c.addView(text("Debloat, speed up and protect your Shield. Everything can be rolled back.", 14f, color = COLOR_MUTED))

        c.addView(text("Shield address  (127.0.0.1 = this device)", 13f, color = COLOR_MUTED).apply { setPadding(0, 12.px(), 0, 2.px()) })
        hostEdit = edit(Engine.host(this)) { Engine.save(this, "host", it.trim()) }
        c.addView(hostEdit)

        dnsBox = check("Block ads, telemetry & trackers (Private DNS)", "dns_on", true)
        c.addView(dnsBox)
        dnsEdit = edit(Engine.pref(this, "dns", "dns.adguard-dns.com")) { Engine.save(this, "dns", it) }
        c.addView(dnsEdit)
        optimizeBox = check("Pre-compile apps for faster launches (runs in background on the Shield)", "optimize", true)
        c.addView(optimizeBox)
        optionalBox = check("Also disable optional extras (stock-home recommendation rows, Ambient screensaver)", "optional", false)
        c.addView(optionalBox)

        c.addView(section("Optimise"))
        c.addView(button("Connect & check status") { task("Status") { h, o -> Engine.run(h, "status", out = o) } })
        c.addView(button("Preview changes (no changes made)") {
            task("Preview") { h, o -> Engine.run(h, debloatArgs(false), tweakArgs(false), out = o) }
        })
        c.addView(button("⚡  Optimise now") {
            confirm("Optimise the Shield?",
                "A rollback snapshot is taken first. Bloat is disabled (never uninstalled), animations are switched off and " +
                    "ad-blocking DNS is applied. Your own apps and launchers are never touched.") {
                task("Optimise") { h, o ->
                    Engine.run(h, debloatArgs(true), tweakArgs(true), out = o)
                    o("Done. Tip: a reboot makes everything take full effect."); 0
                }
            }
        })
        c.addView(button("🧹  Clean cache & RAM") { task("Clean") { h, o -> Engine.run(h, "clean", out = o) } })
        c.addView(button("↩  Roll back everything") {
            task("Rollback preview") { h, o -> Engine.run(h, "rollback", out = o) }
            confirm("Roll back?", "Re-enable every app this app disabled, allow background running again, restore your " +
                "default launcher and every setting this app changed, back to the first snapshot.") {
                task("Rollback") { h, o -> Engine.run(h, "rollback apply", out = o) }
            }
        })

        c.addView(section("Apps & RAM"))
        c.addView(button("📊  Apps & RAM manager") { open(AppsActivity::class.java) })

        c.addView(section("Monitor"))
        c.addView(button("🌡  Health monitor") { open(MonitorActivity::class.java, "health") })
        c.addView(button("🎬  Playback inspector") { open(MonitorActivity::class.java, "playback") })

        c.addView(section("Home screen"))
        c.addView(button("🏠  Launcher manager") { open(LauncherActivity::class.java) })
        c.addView(button("Wallpaper & picture-quality tips") { showTips() })

        c.addView(section("Tools"))
        if (!isTv) {
            c.addView(button("🎮  Phone remote") { open(RemoteActivity::class.java) })
            c.addView(button("📦  Install APK on the Shield") { pick(REQ_APK) })
            c.addView(button("📁  Send a file to the Shield (Downloads)") { pick(REQ_FILE) })
            c.addView(button("📺  Install this app on the Shield") {
                task("Install on Shield") { h, o ->
                    o("Sending app to the Shield…")
                    Engine.install(h, File(applicationInfo.sourceDir))
                    o("Installed. On the Shield look for 'Fitz-TV Optimizer' and the one-click 'Fitz Clean' tile."); 0
                }
            })
        }
        c.addView(button("Install SD Maid SE (on-device cleaner)") {
            task("SD Maid SE") { h, o ->
                o(Engine.shell(h, "am start -a android.intent.action.VIEW -d market://details?id=eu.darken.sdmse"))
                o("Play Store opened on the Shield. Press Install with the remote."); 0
            }
        })

        c.addView(section("Automation & security"))
        maintBox = check("🌙  Nightly auto-maintenance" + if (isTv) "" else " (phone must be on home Wi-Fi overnight)", "maint", false)
        maintBox.setOnCheckedChangeListener { _, on ->
            Engine.save(this, "maint", on); MaintenanceJob.schedule(this, on)
            append(if (on) "Nightly maintenance on: clears caches, re-disables anything updates switched back on, " +
                "re-applies background blocks and pre-compiles updated apps while idle."
            else "Nightly maintenance off.")
        }
        c.addView(maintBox)
        Engine.pref(this, "maint_last", "").takeIf { it.isNotEmpty() }?.let {
            c.addView(text("Last run: $it", 12f, color = COLOR_MUTED))
        }
        c.addView(button("🔒  Turn off debugging (lock the Shield)") { lock() })

        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f; setTextIsSelectable(!isTv)
            setPadding(16.px(), 16.px(), 16.px(), 16.px())
            text = "Ready. Target: ${Engine.host(this@MainActivity)}\n"
        }
        logScroll = ScrollView(this).apply { setBackgroundColor(0xFF0B0E11.toInt()); isFocusable = false; addView(log) }
        val controls = ScrollView(this).apply { addView(c) }
        setContentView(LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {
                addView(controls, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
                addView(logScroll, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.2f))
            } else {
                addView(controls, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.6f))
                addView(logScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }
        })
        actionViews.first().requestFocus()
    }

    private fun open(cls: Class<*>, mode: String? = null) {
        Engine.save(this, "host", hostEdit.text.toString().trim())
        startActivity(Intent(this, cls).apply { mode?.let { putExtra("mode", it) } })
    }

    private fun debloatArgs(apply: Boolean): String {
        val tiers = if (optionalBox.isChecked) "core,bloat,optional" else "core,bloat"
        return "debloat ${if (apply) "apply " else ""}tiers=$tiers"
    }

    private fun tweakArgs(apply: Boolean): String {
        val dns = if (dnsBox.isChecked) dnsEdit.text.toString().filter { it.isLetterOrDigit() || it == '.' || it == '-' } else ""
        return "tweaks ${if (apply) "apply " else ""}dns=$dns${if (optimizeBox.isChecked) " optimize" else ""}"
    }

    /** Runs work off the UI thread with buttons locked and output streamed into the log. */
    private fun task(name: String, work: (host: String, out: (String) -> Unit) -> Int) {
        if (working) return
        val host = hostEdit.text.toString().trim().ifEmpty { Engine.host(this) }
        Engine.save(this, "host", host)
        append("\n── $name → $host ──")
        append(if (Engine.isLocal(host)) "If an 'Allow network debugging?' prompt appears, tick 'Always allow' and press OK."
        else "First time: accept 'Allow network debugging?' on the TV (tick 'Always allow').")
        background(work = { work(host) { line -> runOnUiThread { append(line) } } }) { r ->
            r.onFailure { append(Engine.explain(it, host)) }
        }
    }

    private fun append(line: String) {
        log.append(line + "\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun lock() {
        val warn = buildString {
            append("This turns off USB and Network debugging on the Shield, so nothing on your network can control it.\n\n")
            append("While it's off, this app, the Fitz Clean tile")
            if (maintBox.isChecked) append(" and nightly maintenance")
            append(" can't reach the Shield.\n\nTo turn it back on: Settings › Device Preferences › Developer options › ")
            append("USB debugging, then Network debugging.")
        }
        confirm("Turn off debugging?", warn, "Turn off") {
            task("Lock") { h, o -> Engine.run(h, "lock", out = o).also { Engine.reset() } }
        }
    }

    // ---------- APK / file sender ----------
    private fun pick(req: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val name = displayName(uri)
        if (requestCode == REQ_APK) installApk(uri, name) else sendFile(uri, name)
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.replace(Regex("[^A-Za-z0-9._ ()-]"), "_") ?: "file"

    private fun installApk(uri: Uri, name: String) {
        val lower = name.lowercase()
        task("Install $name") { h, o ->
            val dir = File(cacheDir, "send").apply { deleteRecursively(); mkdirs() }
            if (lower.endsWith(".apks") || lower.endsWith(".xapk") || lower.endsWith(".apkm")) {
                o("Unpacking app bundle…")
                val parts = mutableListOf<File>()
                contentResolver.openInputStream(uri)!!.use { input ->
                    ZipInputStream(input).use { zip ->
                        var e = zip.nextEntry
                        while (e != null) {
                            val n = e.name.substringAfterLast('/')
                            if (!e.isDirectory && n.endsWith(".apk")) {
                                val f = File(dir, n); f.outputStream().use { zip.copyTo(it) }; parts += f
                            }
                            e = zip.nextEntry
                        }
                    }
                }
                if (parts.isEmpty()) error("No APKs found inside $name (encrypted .apkm bundles aren't supported).")
                o("Installing ${parts.size} parts…")
                Engine.installMultiple(h, parts)
            } else {
                val f = File(dir, if (lower.endsWith(".apk")) name else "$name.apk")
                contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
                o("Installing ${f.length() / 1024} KB…")
                Engine.install(h, f)
            }
            dir.deleteRecursively()
            o("Installed $name on the Shield."); 0
        }
    }

    private fun sendFile(uri: Uri, name: String) {
        task("Send $name") { h, o ->
            val f = File(cacheDir, name)
            contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            o("Sending ${f.length() / 1024} KB…")
            Engine.push(h, f, "/sdcard/Download/$name")
            f.delete()
            o("Saved to the Shield's Download folder: /sdcard/Download/$name"); 0
        }
    }

    private fun showTips() {
        AlertDialog.Builder(this).setTitle("Wallpaper & picture tips").setMessage(
            """
            ROTATING WALLPAPER (Projectivy)
            • Projectivy › Settings › General › 'Override current launcher' (grant the accessibility permission).
            • Settings › Wallpaper › choose a source, set change interval to 10 minutes (or the closest offered).

            PICTURE (Settings › Device Preferences › Display & Sound)
            • Resolution: your TV's native mode (e.g. 4K 59.940 Hz).
            • Advanced › Match content frame rate: 'Seamless only'. Smooth, with no black-screen pause.
            • Match content dynamic range: on if you watch HDR.
            • AI upscaling: AI-Enhanced, detail Medium (2019 Shield).
            • Check it's working with the Playback inspector.

            PRIVACY (manual)
            • Device Preferences › Usage & diagnostics: off.
            • Ads: opt out of personalisation, delete advertising ID.
            """.trimIndent(),
        ).setPositiveButton("OK", null).show()
    }

    // ---------- small helpers ----------
    private fun edit(v: String, onChange: (String) -> Unit) = EditText(this).apply {
        setText(v); isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_DONE
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) = onChange(e.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun check(s: String, key: String, def: Boolean) = CheckBox(this).apply {
        text = s; isChecked = Engine.pref(this@MainActivity, key, def); textSize = 15f
        setPadding(8.px(), 10.px(), 0, 10.px())
        setOnCheckedChangeListener { _, c -> Engine.save(this@MainActivity, key, c) }
    }

    companion object {
        private const val REQ_APK = 1
        private const val REQ_FILE = 2
    }
}
