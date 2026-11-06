package com.fitztv.mobile

import com.fitztv.FitzApp
import com.fitztv.mobile.player.MobilePlayerActivity

class MobileApp : FitzApp() {
    override val playerActivity = MobilePlayerActivity::class.java
    override val updateAsset = "Mobile"

    init { com.fitztv.player.Multiview.activity = com.fitztv.mobile.player.MobileMultiviewActivity::class.java }
}
