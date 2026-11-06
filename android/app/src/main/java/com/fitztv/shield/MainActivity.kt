package com.fitztv.shield

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var hostEdit: EditText
    private lateinit var dnsBox: CheckBox
    private lateinit var dnsEdit: EditText
    private lateinit var optionalBox: CheckBox
    private lateinit var optimizeBox: CheckBox
    private val buttons = mutableListOf<Button>()
    private var busy = false

    private val dp get() = resources.displayMetrics.density
    private fun Int.px() = (this * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)
        val tv = Engine.isTv(this)
        val wide = resources.configuration.screenWidthDp >= 720

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.px(), 20.px(), 24.px(), 20.px())
        }
        controls.addView(text("Fitz-TV Optimizer", 26f, bold = true))
        controls.addView(text("Debloat, speed up and protect your Shield. Everything can be rolled back.", 14f, muted = true))

        controls.addView(label("Shield address  (127.0.0.1 = this device)"))
        hostEdit = edit(Engine.host(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { Engine.save(this, "host", it) }
        controls.addView(hostEdit)

        dnsBox = check("Block ads, telemetry & trackers (Private DNS)", Engine.pref(this, "dns_on", true)) { Engine.save(this, "dns_on", it) }
        controls.addView(dnsBox)
        dnsEdit = edit(Engine.pref(this, "dns", "dns.adguard-dns.com"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { Engine.save(this, "dns", it) }
        controls.addView(dnsEdit)
        optimizeBox = check("Pre-compile apps for faster launches (5–15 min)", Engine.pref(this, "optimize", true)) { Engine.save(this, "optimize", it) }
        controls.addView(optimizeBox)
        optionalBox = check("Also disable optional extras (stock-home recommendation rows, Ambient screensaver)", Engine.pref(this, "optional", false)) { Engine.save(this, "optional", it) }
        controls.addView(optionalBox)

        controls.addView(button("Connect & check status") { task("Status") { h, o -> Engine.run(h, "status", out = o) } })
        controls.addView(button("Preview changes (no changes made)") {
            task("Preview") { h, o -> Engine.run(h, debloatArgs(false), tweakArgs(false), out = o) }
        })
        controls.addView(button("⚡  Optimise now") {
            confirm("Optimise the Shield?",
                "A rollback snapshot is taken first. Bloat is disabled (never uninstalled), animations are switched off and ad-blocking DNS is applied. Your own apps and launchers are never touched.") {
                task("Optimise") { h, o ->
                    Engine.run(h, debloatArgs(true), tweakArgs(true), out = o)
                    o("Done. Tip: a reboot makes everything take full effect.")
                    0
                }
            }
        })
        controls.addView(button("🧹  Clean cache & RAM") { task("Clean") { h, o -> Engine.run(h, "clean", out = o) } })
        controls.addView(button("↩  Roll back everything") {
            task("Rollback preview") { h, o -> Engine.run(h, "rollback", out = o) }
            confirm("Roll back?", "Re-enable every disabled app and restore every setting this app changed, back to the first snapshot.") {
                task("Rollback") { h, o -> Engine.run(h, "rollback apply", out = o) }
            }
        })
        controls.addView(button("Install SD Maid SE (on-device cleaner)") {
            task("SD Maid SE") { h, o ->
                o(Engine.shell(h, "am start -a android.intent.action.VIEW -d market://details?id=eu.darken.sdmse"))
                o("Play Store opened on the Shield — press Install with the remote. If it says 'not compatible', the built-in Clean button does the same job.")
                0
            }
        })
        if (!tv) controls.addView(button("📺  Install this app on the Shield") {
            task("Install on Shield") { h, o ->
                o("Sending app to the Shield…")
                Engine.install(h, File(applicationInfo.sourceDir))
                o("Installed. On the Shield look for 'Fitz-TV Optimizer' and the one-click 'Fitz Clean' tile.")
                0
            }
        })
        controls.addView(button("Wallpaper & picture-quality tips") { showTips() })

        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f; setTextIsSelectable(!tv)
            setPadding(16.px(), 16.px(), 16.px(), 16.px())
            text = "Ready. Target: ${Engine.host(this@MainActivity)}\n"
        }
        logScroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0B0E11.toInt()); isFocusable = false; addView(log)
        }

        val controlsScroll = ScrollView(this).apply { addView(controls) }
        val root = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {
                addView(controlsScroll, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
                addView(logScroll, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.2f))
            } else {
                addView(controlsScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.4f))
                addView(logScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }
        }
        setContentView(root)
        buttons.first().requestFocus()
    }

    private fun debloatArgs(apply: Boolean): String {
        val tiers = if (optionalBox.isChecked) "core,bloat,optional" else "core,bloat"
        return "debloat ${if (apply) "apply " else ""}tiers=$tiers"
    }

    private fun tweakArgs(apply: Boolean): String {
        val dns = if (dnsBox.isChecked) dnsEdit.text.toString().filter { it.isLetterOrDigit() || it == '.' || it == '-' } else ""
        return buildString {
            append("tweaks "); if (apply) append("apply ")
            append("dns=$dns")
            if (optimizeBox.isChecked) append(" optimize")
        }
    }

    /** Runs work off the UI thread with buttons locked and output streamed into the log. */
    private fun task(name: String, work: (host: String, out: (String) -> Unit) -> Int) {
        if (busy) return
        val host = hostEdit.text.toString().trim().ifEmpty { Engine.host(this) }
        setBusy(true)
        append("\n── $name → $host ──")
        if (host == "127.0.0.1" || host == "localhost")
            append("If an 'Allow network debugging?' prompt appears, tick 'Always allow' and press OK.")
        else append("First time: accept 'Allow network debugging?' on the TV (tick 'Always allow').")
        Engine.worker.execute {
            try { work(host) { line -> runOnUiThread { append(line) } } }
            catch (e: Throwable) { runOnUiThread { append(Engine.explain(e, host)) } }
            finally { runOnUiThread { setBusy(false) } }
        }
    }

    private fun append(line: String) {
        log.append(line + "\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setBusy(b: Boolean) { busy = b; buttons.forEach { it.isEnabled = !b } }

    private fun confirm(title: String, msg: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("Yes") { _, _ -> yes() }.setNegativeButton("Cancel", null).show()
    }

    private fun showTips() {
        AlertDialog.Builder(this).setTitle("Wallpaper & picture tips").setMessage(
            """
            ROTATING WALLPAPER (Projectivy)
            • Projectivy > Settings > General > 'Override current launcher' (grant the accessibility permission).
            • Settings > Wallpaper > choose a source, set change interval to 10 minutes (or the closest offered).
            • Add your Favourites/App rows and Channel rows to match your old layout.

            PICTURE (Settings > Device Preferences > Display & Sound)
            • Resolution: your TV's native mode (e.g. 4K 59.940 Hz).
            • Advanced > Match content frame rate: 'Seamless only' — smooth, no black-screen pause.
            • Match content dynamic range: on if you watch HDR.
            • AI upscaling: AI-Enhanced, detail Medium (2019 Shield).

            PRIVACY (manual)
            • Device Preferences > Usage & diagnostics: off.
            • Ads: opt out of personalisation, delete advertising ID.
            """.trimIndent()
        ).setPositiveButton("OK", null).show()
    }

    // ---- small view helpers ----
    private fun text(s: String, size: Float, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; if (bold) setTypeface(typeface, Typeface.BOLD)
        if (muted) setTextColor(0xFF9AA0A6.toInt())
        setPadding(0, 0, 0, 8.px())
    }
    private fun label(s: String) = text(s, 13f, muted = true).apply { setPadding(0, 12.px(), 0, 2.px()) }

    private fun edit(v: String, type: Int, onChange: (String) -> Unit) = EditText(this).apply {
        setText(v); inputType = type; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_DONE
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) = onChange(e.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun check(s: String, v: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = s; isChecked = v; textSize = 15f; setPadding(8.px(), 10.px(), 0, 10.px())
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 17f; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.btn); setTextColor(0xFFE8EAED.toInt())
        setPadding(20.px(), 14.px(), 20.px(), 14.px())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 10.px() }
        setOnClickListener { onClick() }
        buttons += this
    }
}
