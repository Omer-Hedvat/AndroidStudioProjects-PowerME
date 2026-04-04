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
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.ThemeMode
import com.powerme.app.navigation.PowerMeApp
import com.powerme.app.ui.theme.PowerMETheme
import com.powerme.app.ui.workout.WorkoutViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appSettingsDataStore: AppSettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by appSettingsDataStore.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            val keepScreenOn by appSettingsDataStore.keepScreenOn.collectAsState(initial = true)
            val workoutViewModel: WorkoutViewModel = hiltViewModel()
            val workoutState by workoutViewModel.workoutState.collectAsState()
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
                if (keepScreenOn && workoutState.isActive) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            PowerMETheme(themeMode = themeMode) {
                PowerMeApp()
            }
        }
    }
}
