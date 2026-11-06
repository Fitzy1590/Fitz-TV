package com.fitztv.shield

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Phone remote: D-pad, media and volume keys, and typing/pasting text into the TV. */
class RemoteActivity : Screen() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(text("Remote", 26f, bold = true))
        status = text("Target: ${host()}", 12f, color = COLOR_MUTED)
        root.addView(status)

        root.addView(keys(null to 0, "▲" to 19, null to 0))
        root.addView(keys("◀" to 21, "OK" to 23, "▶" to 22))
        root.addView(keys(null to 0, "▼" to 20, null to 0))
        root.addView(keys("Back" to 4, "Home" to 3, "Menu" to 82))
        root.addView(keys("⏪" to 89, "⏯" to 85, "⏩" to 90))
        root.addView(keys("Vol −" to 25, "Mute" to 164, "Vol +" to 24))
        root.addView(keys("Sleep" to 223, "Wake" to 224, "Settings" to 176))

        root.addView(section("Type into the TV"))
        val input = EditText(this).apply {
            hint = "Text, password or URL"; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        root.addView(input)
        root.addView(row().apply {
            addView(small("Send text") { sendText(input.text.toString()); input.setText("") })
            addView(small("⌫ Delete") { key(67) })
            addView(small("Enter") { key(66) })
        })
        input.setOnEditorActionListener { _, _, _ -> sendText(input.text.toString()); input.setText(""); true }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun keys(vararg k: Pair<String?, Int>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        for ((label, code) in k) {
            val b = Button(this@RemoteActivity).apply {
                text = label ?: ""; isAllCaps = false; textSize = 20f
                setTextColor(COLOR_TEXT)
                if (label != null) { setBackgroundResource(R.drawable.btn); setOnClickListener { key(code) } }
                else { background = null; isEnabled = false; isFocusable = false }
            }
            addView(b, LinearLayout.LayoutParams(0, 64.px(), 1f).apply { setMargins(5.px(), 5.px(), 5.px(), 5.px()) })
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun key(code: Int) = send("input keyevent $code")

    private fun sendText(t: String) {
        if (t.isEmpty()) return
        // `input text` treats %s as a space; single-quote everything else for the device shell.
        val arg = "'" + t.replace("'", "'\\''").replace(" ", "%s") + "'"
        send("input text $arg")
    }

    private fun send(cmd: String) {
        background(fast = true, lock = false, work = { Engine.shell(host(), cmd) }) { r ->
            r.onFailure { status.text = error(it) }
            r.onSuccess { status.text = "Target: ${host()} · connected" }
        }
    }
}
