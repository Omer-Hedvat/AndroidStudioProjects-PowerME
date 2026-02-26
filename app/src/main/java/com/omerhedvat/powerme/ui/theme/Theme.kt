package com.omerhedvat.powerme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = StremioViolet,
    secondary        = StremioMagenta,
    tertiary         = StremioViolet,
    background       = StremioBackground,
    surface          = StremioSurface,
    surfaceVariant   = StremioSurfaceVar,
    onPrimary        = StremioBackground,
    onSecondary      = StremioCloudGrey,
    onTertiary       = StremioBackground,
    onBackground     = StremioCloudGrey,
    onSurface        = StremioCloudGrey,
    onSurfaceVariant = StremioCloudGrey,
    error            = StremioError,
    onError          = StremioBackground
)

private val LightColorScheme = lightColorScheme(
    primary          = StremioViolet,
    secondary        = StremioMagenta,
    tertiary         = StremioViolet,
    background       = Slate50,
    surface          = Slate100,
    surfaceVariant   = Slate200,
    onPrimary        = LightBackground,
    onSecondary      = StremioViolet,
    onTertiary       = LightBackground,
    onBackground     = Slate900,
    onSurface        = Slate900,
    onSurfaceVariant = Slate900,
    error            = StremioError,
    onError          = LightBackground
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
