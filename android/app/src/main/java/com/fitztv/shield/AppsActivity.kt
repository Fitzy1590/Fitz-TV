package com.fitztv.shield

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * RAM-hog finder + full debloat wizard + background-running blocker, on one screen.
 * Tabs: apps using RAM now / all built-in apps / your apps.
 */
class AppsActivity : Screen() {
    private data class App(
        val pkg: String, val mb: Int, val kind: String, val state: String, val verdict: String, val bg: String,
    )

    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private val tabs = mutableListOf<Button>()
    private var mode = "running"
    private val local by lazy { Engine.isLocal(host()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(text("Apps & RAM", 26f, bold = true))
        summary = text("Loading…", 14f, color = COLOR_MUTED)
        root.addView(summary)

        val tabRow = row()
        for ((m, label) in listOf("running" to "Using RAM now", "all" to "All built-in apps", "user" to "Your apps")) {
            tabRow.addView(small(label) { mode = m; load() }.also { tabs += it })
        }
        root.addView(tabRow)
        root.addView(text(
            "● Safe to disable   ● Caution: only if you don't use it   ● Core: protected\n" +
                "Disable = turn the app off completely. Block background = it still opens normally, but can't run " +
                "or use RAM when you're not using it. Everything is undone by 'Roll back everything'.",
            12f, color = COLOR_MUTED,
        ).apply { setPadding(0, 10.px(), 0, 6.px()) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply { addView(root) })
        load()
        tabs.first().requestFocus()
    }

    private fun load() {
        tabs.forEachIndexed { i, b -> b.alpha = if (listOf("running", "all", "user")[i] == mode) 1f else 0.6f }
        summary.text = "Loading app list from the Shield (a few seconds)…"
        list.removeAllViews(); actionViews.retainAll(tabs.toSet())
        background(work = { Engine.lines(host(), "apps $mode") }) { r ->
            r.onFailure { summary.text = error(it) }
            r.onSuccess { lines ->
                val mem = Engine.table(lines, "MEM").firstOrNull()
                val apps = Engine.table(lines, "ROW").mapNotNull {
                    if (it.size < 6) null else App(it[0], it[1].toIntOrNull() ?: 0, it[2], it[3], it[4], it[5])
                }
                val used = apps.filter { it.state == "enabled" }.sumOf { it.mb }
                summary.text = buildString {
                    if (mem != null && mem.size >= 2) append("Free RAM: ${mem[0]} MB of ${mem[1]} MB.  ")
                    when (mode) {
                        "running" -> append("${apps.size} apps in memory using $used MB. Biggest first.")
                        "all" -> append("${apps.size} built-in apps (${apps.count { it.state == "disabled" }} disabled).")
                        else -> append("${apps.size} apps you installed. These are never disabled automatically.")
                    }
                }
                apps.forEach { list.addView(card(it)) }
                if (apps.isEmpty()) list.addView(text("Nothing to show.", color = COLOR_MUTED))
            }
        }
    }

    private fun card(a: App): LinearLayout {
        val (vLabel, vColor) = when (a.verdict) {
            "safe" -> "Safe to disable" to COLOR_SAFE
            "caution" -> "Caution" to COLOR_CAUTION
            "yours" -> "Your app" to COLOR_YOURS
            "launcher" -> "Launcher (protected)" to COLOR_YOURS
            else -> "Core (protected)" to COLOR_KEEP
        }
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_PANEL)
            setPadding(16.px(), 12.px(), 16.px(), 12.px())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 8.px() }
        }
        c.addView(text(Names.of(this, a.pkg, local), 17f, bold = true).apply { setPadding(0, 0, 0, 2.px()) })
        val info = buildString {
            if (a.mb > 0) append("${a.mb} MB RAM · ")
            append(if (a.state == "disabled") "DISABLED" else "enabled")
            if (a.bg == "blocked") append(" · background blocked")
            append(" · ${a.pkg}")
        }
        c.addView(text(info, 12f, color = COLOR_MUTED).apply { setPadding(0, 0, 0, 2.px()) })
        c.addView(text(vLabel, 13f, bold = true, color = vColor).apply { setPadding(0, 0, 0, 0) })

        val actions = row()
        val protected = a.verdict == "keep" || a.verdict == "launcher"
        if (a.kind == "system" && !protected) {
            if (a.state == "disabled") actions.addView(small("Enable") { act("enable ${a.pkg}") })
            else actions.addView(small("Disable") {
                if (a.verdict == "caution") confirm("Disable ${Names.of(this, a.pkg, local)}?",
                    "This isn't on the known-safe list. Only disable it if you don't use it. You can re-enable it here or with Roll back.",
                    "Disable") { act("disable ${a.pkg}") }
                else act("disable ${a.pkg}")
            })
        }
        if (!protected && a.state != "disabled") {
            if (a.bg == "blocked") actions.addView(small("Allow background") { act("bg ${a.pkg} off") })
            else actions.addView(small("Block background") {
                confirm("Block background running?",
                    "${Names.of(this, a.pkg, local)} will still open and work normally, but can't run while you're not using it. " +
                        "Don't use this on apps that need to work in the background (music players, Plex server, downloaders).",
                    "Block") { act("bg ${a.pkg} on") }
            })
        }
        if (a.state == "enabled" && a.mb > 0 && a.pkg != "android" && a.pkg != "system" && !a.pkg.startsWith("com.fitztv.shield"))
            actions.addView(small("Stop now") { act("stop ${a.pkg}") })
        if (actions.childCount > 0) c.addView(actions)
        return c
    }

    private fun act(args: String) {
        background(work = { Engine.lines(host(), args) }) { r ->
            r.onFailure { toast(error(it)) }
            r.onSuccess { toast(it.lastOrNull() ?: "Done"); load() }
        }
    }
}
