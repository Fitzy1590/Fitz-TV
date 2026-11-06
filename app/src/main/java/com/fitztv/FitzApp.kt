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

class FitzApp : Application(), Configuration.Provider {
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
