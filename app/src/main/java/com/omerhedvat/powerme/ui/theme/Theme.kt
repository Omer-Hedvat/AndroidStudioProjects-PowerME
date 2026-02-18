package com.omerhedvat.powerme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = ElectricBlue,
    tertiary = NeonBlue,
    background = DeepNavy,
    surface = NavySurface,
    onPrimary = DeepNavy,
    onSecondary = Color.White,
    onTertiary = DeepNavy,
    onBackground = NeonBlue,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    secondary = NeonBlue,
    tertiary = ElectricBlue,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = ElectricBlue,
    onTertiary = Color.White,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface
)

@Composable
fun PowerMETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
