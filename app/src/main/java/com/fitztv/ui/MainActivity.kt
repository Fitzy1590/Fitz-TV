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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FitzTheme { MainScreen(FitzApp.instance) } }
    }

    override fun onResume() {
        super.onResume()
        // Cheap no-op unless the guide or channel list is out of date (e.g. a new day).
        lifecycleScope.launch { FitzApp.instance.repo.refreshIfStale() }
    }
}
