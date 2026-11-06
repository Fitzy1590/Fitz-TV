package com.fitztv.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.fitztv.FitzApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FitzMobileTheme { MobileMain(FitzApp.instance) } }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { FitzApp.instance.repo.refreshIfStale() }
    }
}
