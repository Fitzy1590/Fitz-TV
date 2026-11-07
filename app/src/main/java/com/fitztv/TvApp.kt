package com.fitztv

import com.fitztv.player.PlayerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class TvApp : FitzApp() {
    override val playerActivity = PlayerActivity::class.java
    override val updateAsset = "TV"

    init { com.fitztv.player.Multiview.activity = com.fitztv.player.MultiviewActivity::class.java }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Theme follows Settings › Theme instantly.
        scope.launch { settings.state.collect { com.fitztv.ui.theme.Neon.palette = com.fitztv.ui.components.Palettes.byId(it.theme) } }
        // Keep the Android TV home-screen row in step with the guide (at most every 2 minutes).
        scope.launch {
            repo.games.debounce(5_000).collect {
                com.fitztv.tvhome.HomeRow.publish(this@TvApp)
                delay(120_000)
            }
        }
    }
}
