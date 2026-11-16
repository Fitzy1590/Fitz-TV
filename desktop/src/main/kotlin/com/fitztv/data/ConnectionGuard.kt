package com.fitztv.data

import com.fitztv.sources.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Warns when more multiview windows are open than the provider's plan allows streams at once. */
object ConnectionGuard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkedAt = 0L

    /** Smallest connection limit across your Xtream sources, when the provider reports one. */
    @Volatile var limit: Int? = null
        private set

    private val _warning = MutableStateFlow<String?>(null)
    /** Shown by the multiview screen. */
    val warning: StateFlow<String?> = _warning

    private fun refresh(settings: AppSettings) {
        if (System.currentTimeMillis() - checkedAt < 5 * 60_000L) return
        checkedAt = System.currentTimeMillis()
        val limits = settings.sources.filter { it.type == SourceType.XTREAM }
            .mapNotNull { runCatching { XtreamClient.connections(it, settings.userAgent) }.getOrNull()?.first?.takeIf { n -> n > 0 } }
        limit = limits.minOrNull()
    }

    fun windowsChanged(settings: AppSettings, windows: Int) {
        if (windows < 2) { _warning.value = null; return }
        scope.launch {
            refresh(settings)
            val lim = limit ?: return@launch
            _warning.value = if (windows > lim)
                "Your plan allows $lim streams at once but $windows windows are open: some will keep dropping. Close a window, or stop other devices."
            else null
        }
    }
}
