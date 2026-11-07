package com.fitztv

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.fitztv.data.Repository
import com.fitztv.data.SettingsStore
import com.fitztv.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shared application base; the TV and phone apps each supply their own player screen. */
abstract class FitzApp : Application(), Configuration.Provider {
    /** Activity that plays [com.fitztv.player.PlaybackQueue]. */
    abstract val playerActivity: Class<out android.app.Activity>
    /** Which APK in a GitHub release is this app ("TV" or "Mobile"). */
    abstract val updateAsset: String
    lateinit var updates: com.fitztv.update.Updater
        private set
    lateinit var alerts: com.fitztv.alerts.Alerts
        private set
    /** Background stream-quality measuring (only while the app is open and idle). */
    lateinit var scout: com.fitztv.data.QualityScout
        private set

    lateinit var settings: SettingsStore
        private set
    lateinit var repo: Repository
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        repo = Repository(this, settings)
        updates = com.fitztv.update.Updater(this, settings, updateAsset)
        com.fitztv.record.Recorder.init(this)
        alerts = com.fitztv.alerts.Alerts(this, repo, settings, scope)
        scout = com.fitztv.data.QualityScout(repo, settings)
        scope.launch {
            repo.loadFromDisk()
            repo.refreshIfStale()
        }
        RefreshWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.ASSERT).build()

    companion object {
        lateinit var instance: FitzApp
            private set
    }
}
