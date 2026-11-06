package com.fitztv.mobile

import com.fitztv.FitzApp
import com.fitztv.mobile.player.MobilePlayerActivity

class MobileApp : FitzApp() {
    override val playerActivity = MobilePlayerActivity::class.java
}
