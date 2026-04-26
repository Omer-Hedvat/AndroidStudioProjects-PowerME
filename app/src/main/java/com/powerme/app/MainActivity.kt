package com.powerme.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.KeepScreenOnMode
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.navigation.PowerMeApp
import com.powerme.app.ui.theme.PowerMETheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appSettingsDataStore: AppSettingsDataStore
    @Inject lateinit var firestoreSyncManager: FirestoreSyncManager

    private var lastSyncMs = 0L

    override fun onStart() {
        super.onStart()
        val now = System.currentTimeMillis()
        if (now - lastSyncMs > 5 * 60 * 1000L) {
            lastSyncMs = now
            firestoreSyncManager.launchBackgroundSync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Collect keepScreenOnMode imperatively so the window flag is applied immediately
        // and on every change — independent of Compose recomposition timing.
        // ALWAYS  → add FLAG_KEEP_SCREEN_ON (screen always on, app-wide)
        // anything else → clear flag (DURING_WORKOUT is handled view-level in ActiveWorkoutScreen)
        lifecycleScope.launch {
            appSettingsDataStore.keepScreenOnMode.collect { mode ->
                if (mode == KeepScreenOnMode.ALWAYS) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            val themeMode by appSettingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> systemDark
            }
            SideEffect {
                val barStyle = if (isDark)
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            PowerMETheme(themeMode = themeMode) {
                PowerMeApp()
            }
        }
    }
}
