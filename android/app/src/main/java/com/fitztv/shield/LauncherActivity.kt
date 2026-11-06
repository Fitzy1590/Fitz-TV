package com.fitztv.shield

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Pick the default launcher; optionally turn the stock launcher + recommendations off (reversible). */
class LauncherActivity : Screen() {
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val local by lazy { Engine.isLocal(host()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(text("Launcher manager", 26f, bold = true))
        root.addView(text(
            "Make your launcher the real default, so Home always goes straight there. Turning the stock launcher off " +
                "also stops its recommendation service, which frees RAM. You lose the stock home screen's content rows " +
                "(Projectivy has its own). Settings stays as a fallback home, so you can never be left without one.",
            13f, color = COLOR_MUTED,
        ))
        status = text("Loading…", 13f, color = COLOR_MUTED)
        root.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply { addView(root) })
        load()
    }

    private fun load() {
        list.removeAllViews(); actionViews.clear()
        background(work = { Engine.lines(host(), "launchers") }) { r ->
            r.onFailure { status.text = error(it) }
            r.onSuccess { lines ->
                status.text = ""
                for (l in Engine.table(lines, "ROW")) {
                    if (l.size < 3) continue
                    val (component, pkg, isDefault) = l
                    if (pkg == "com.android.tv.settings") continue
                    val card = panel()
                    card.addView(text(Names.of(this, pkg, local), 18f, bold = true))
                    card.addView(text(if (isDefault == "yes") "★ Default launcher" else component, 12f,
                        color = if (isDefault == "yes") COLOR_SAFE else COLOR_MUTED))
                    if (isDefault != "yes") card.addView(row().apply {
                        addView(small("Make default") { act("sethome $component") })
                    })
                    list.addView(card)
                }
                val stock = Engine.table(lines, "STOCK").associate { it[0] to it.getOrElse(1) { "" } }
                val off = stock["com.google.android.tvlauncher"] == "disabled"
                val card = panel()
                card.addView(text("Stock Android TV home + recommendations", 18f, bold = true))
                card.addView(text(if (off) "Turned OFF (saving RAM)" else "Running", 13f, color = if (off) COLOR_SAFE else COLOR_MUTED))
                card.addView(row().apply {
                    if (off) addView(small("Turn stock launcher back on") { act("stocklauncher on") })
                    else addView(small("Turn stock launcher off") {
                        confirm("Turn off the stock launcher?",
                            "Your other launcher becomes Home and the stock recommendation service stops. " +
                                "Undo here any time, or with Roll back everything.", "Turn off") { act("stocklauncher off") }
                    })
                })
                list.addView(card)
            }
        }
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(COLOR_PANEL)
        setPadding(16.px(), 12.px(), 16.px(), 12.px())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 10.px() }
    }

    private fun act(args: String) {
        background(work = { Engine.lines(host(), args) }) { r ->
            r.onFailure { toast(error(it)) }
            r.onSuccess { toast(it.lastOrNull() ?: "Done"); load() }
        }
    }
}
