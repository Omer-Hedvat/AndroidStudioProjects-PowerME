package com.omerhedvat.powerme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.omerhedvat.powerme.navigation.PowerMeApp
import com.omerhedvat.powerme.ui.theme.PowerMETheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PowerMETheme {
                PowerMeApp()
            }
        }
    }
}