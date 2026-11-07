package com.fitztv.mobile

import com.fitztv.FitzApp
import kotlinx.coroutines.launch
import com.fitztv.mobile.player.MobilePlayerActivity

class MobileApp : FitzApp() {
    override val playerActivity = MobilePlayerActivity::class.java
    override val updateAsset = "Mobile"

    override fun onCreate() {
        super.onCreate()
        // Theme follows Settings › Theme instantly.
        scope.launch { settings.state.collect { com.fitztv.mobile.ui.Neon.palette = com.fitztv.ui.components.Palettes.byId(it.theme) } }
    }

    init { com.fitztv.player.Multiview.activity = com.fitztv.mobile.player.MobileMultiviewActivity::class.java }
}
