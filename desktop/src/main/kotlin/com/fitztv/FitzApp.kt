package com.fitztv

import android.content.Context
import com.fitztv.data.Repository
import com.fitztv.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Windows app root: owns the same settings / repository / alerts / updater the TV and phone apps use. */
class FitzApp : Context() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsStore(this)
    val repo = Repository(this, settings)
    val updates = com.fitztv.update.Updater(this, settings, "Windows")
    val alerts = com.fitztv.alerts.Alerts(repo, settings, scope)
    /** Background stream-quality measuring (only while the app is open and idle). */
    val scout = com.fitztv.data.QualityScout(repo, settings)

    init {
        instance = this
        com.fitztv.data.ChannelLogos.init(this)
        com.fitztv.record.Recorder.init(this)
        scope.launch {
            repo.loadFromDisk()
            repo.refreshIfStale()
        }
        alerts.start()
    }

    companion object {
        lateinit var instance: FitzApp
            private set
    }
}
