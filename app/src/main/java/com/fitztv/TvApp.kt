package com.fitztv

import com.fitztv.player.PlayerActivity

class TvApp : FitzApp() {
    override val playerActivity = PlayerActivity::class.java
    override val updateAsset = "TV"

    init { com.fitztv.player.Multiview.activity = com.fitztv.player.MultiviewActivity::class.java }
}
