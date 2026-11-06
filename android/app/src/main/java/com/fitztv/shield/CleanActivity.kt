package com.fitztv.shield

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.TextView

/** One-click tile: clears every app's cache, closes background apps, shows the result, closes itself. */
class CleanActivity : Activity() {
    private lateinit var msg: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)
        msg = TextView(this).apply {
            textSize = 20f; setPadding(48, 40, 48, 40); minWidth = 700
            text = "🧹  Cleaning cache & RAM…"
        }
        setContentView(msg)
        val host = Engine.host(this)
        val lines = mutableListOf<String>()
        Engine.worker.execute {
            val result = try {
                Engine.run(host, "clean") { lines += it }
                "✅  Cleaned\n\n" + lines.filter { it.startsWith("Free") }.joinToString("\n")
            } catch (e: Throwable) {
                "⚠️  " + Engine.explain(e, host) + "\n\nOpen Fitz-TV Optimizer to check the address."
            }
            ui.post {
                done = true; msg.text = result
                ui.postDelayed({ if (!isFinishing) finish() }, 5000)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (done) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
