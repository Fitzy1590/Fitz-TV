package com.fitztv.shield

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Live health monitor (mode=health) or playback inspector (mode=playback); refreshes while open. */
class MonitorActivity : Screen() {
    private lateinit var table: LinearLayout
    private lateinit var status: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var running = false
    private var inFlight = false
    private val mode by lazy { intent.getStringExtra("mode") ?: "health" }
    private val interval get() = if (mode == "health") 3000L else 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(text(if (mode == "health") "Health monitor" else "Playback inspector", 26f, bold = true))
        root.addView(text(
            if (mode == "health") "Live CPU, temperature, RAM, storage and network. High temperature with low CPU speed means " +
                "the Shield is throttling: give it more airflow."
            else "Start playing something on the Shield. This shows what it's actually sending to your TV: resolution, " +
                "refresh rate (should match the video, e.g. 23.976 Hz for films, if frame-rate matching is on) and audio format " +
                "(E_AC3 / AC3 / DTS / TRUEHD = passthrough to your TV or receiver; PCM = decoded on the Shield).",
            13f, color = COLOR_MUTED,
        ))
        status = text("Connecting…", 12f, color = COLOR_MUTED)
        root.addView(status)
        table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(table)
        root.addView(button("Refresh now") { tick() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); running = true; tick() }
    override fun onPause() { super.onPause(); running = false; ui.removeCallbacksAndMessages(null) }

    private fun tick() {
        if (inFlight) return
        inFlight = true
        background(fast = true, lock = false, work = { Engine.lines(host(), mode) }) { r ->
            inFlight = false
            r.onFailure { status.text = error(it) }
            r.onSuccess { lines ->
                status.text = "Updated ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())} · refreshes every ${interval / 1000}s"
                show(Engine.table(lines, "KV"))
            }
            if (running) ui.postDelayed({ tick() }, interval)
        }
    }

    private fun show(rows: List<List<String>>) {
        table.removeAllViews()
        for (kv in rows) {
            if (kv.size < 2) continue
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundColor(COLOR_PANEL)
                setPadding(16.px(), 10.px(), 16.px(), 10.px())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 6.px() }
            }
            r.addView(text(kv[0], 12f, color = COLOR_MUTED).apply { setPadding(0, 0, 0, 0) })
            r.addView(text(kv[1], 17f, bold = true, color = colorFor(kv[0], kv[1])).apply { setPadding(0, 0, 0, 0) })
            table.addView(r)
        }
        if (rows.isEmpty()) table.addView(text("No data returned.", color = COLOR_MUTED))
    }

    private fun colorFor(k: String, v: String): Int {
        if (k.startsWith("Temp")) {
            val t = v.substringBefore(' ').toIntOrNull() ?: return COLOR_TEXT
            return if (t >= 80) 0xFFFF5C5C.toInt() else if (t >= 65) COLOR_CAUTION else COLOR_SAFE
        }
        if (k == "RAM free") {
            val pct = Regex("\\((\\d+)%\\)").find(v)?.groupValues?.get(1)?.toIntOrNull() ?: return COLOR_TEXT
            return if (pct < 15) 0xFFFF5C5C.toInt() else if (pct < 30) COLOR_CAUTION else COLOR_SAFE
        }
        return COLOR_TEXT
    }
}
