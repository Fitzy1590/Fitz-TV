package com.fitztv.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.fitztv.sources.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Knows how many streams the provider account allows at once (Xtream max_connections) and warns
 * when multiview opens more windows than that: the extra windows are the ones that keep dropping.
 */
object ConnectionGuard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var checkedAt = 0L
    /** Smallest connection limit across your Xtream sources, when the provider reports one. */
    @Volatile var limit: Int? = null
        private set

    private fun refresh(settings: AppSettings) {
        if (System.currentTimeMillis() - checkedAt < 5 * 60_000L) return
        checkedAt = System.currentTimeMillis()
        val limits = settings.sources.filter { it.type == SourceType.XTREAM }
            .mapNotNull { runCatching { XtreamClient.connections(it, settings.userAgent) }.getOrNull()?.first?.takeIf { n -> n > 0 } }
        limit = limits.minOrNull()
    }

    /** Call after the number of multiview windows changes. */
    fun windowsChanged(context: Context, settings: AppSettings, windows: Int) {
        if (windows < 2) return
        scope.launch {
            refresh(settings)
            val lim = limit ?: return@launch
            if (windows > lim) Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext,
                    "Your plan allows $lim streams at once but $windows windows are open: some will keep dropping. Close a window, or stop other devices.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}
