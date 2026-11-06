package com.fitztv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.fitztv.FitzApp
import com.fitztv.ui.screens.MainScreen
import com.fitztv.ui.theme.FitzTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object { var updateChecked = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent { FitzTheme { MainScreen(FitzApp.instance) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /** fitztv://game/<id> from a reminder, alert or the Android TV home row: start watching. */
    private fun handleDeepLink(intent: android.content.Intent?) {
        val id = com.fitztv.alerts.Alerts.gameIdFrom(intent) ?: return
        intent?.data = null
        val app = FitzApp.instance
        lifecycleScope.launch {
            app.repo.loadFromDisk()
            app.repo.game(id)?.let { com.fitztv.player.Launcher.play(this@MainActivity, it) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cheap no-op unless the guide or channel list is out of date (e.g. a new day).
        // Guide refresh runs on the app scope: it carries on in the background and never blocks the screen.
        val app = FitzApp.instance
        app.scope.launch { app.repo.refreshIfStale() }
        if (!updateChecked && app.settings.value.autoUpdateCheck) {
            updateChecked = true
            app.scope.launch { app.updates.check() }
        }
    }
}
