package com.omerhedvat.powerme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.omerhedvat.powerme.data.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary          = StremioViolet,
    secondary        = StremioMagenta,
    tertiary         = StremioViolet,
    background       = Slate50,
    surface          = Slate100,
    surfaceVariant   = StremioCloudGrey,
    onPrimary        = LightBackground,
    onSecondary      = StremioViolet,
    onTertiary       = LightBackground,
    onBackground     = Slate900,
    onSurface        = Slate900,
    onSurfaceVariant = Slate900,
    error            = StremioError,
    onError          = LightBackground
)

private val DarkColorScheme = darkColorScheme(
    primary             = StremioViolet,           // #A061FF — accent only
    onPrimary           = StremioBackground,       // #000000 — black on purple
    primaryContainer    = Color(0xFF3D1A8F),        // deep purple (FABs, filled chips)
    onPrimaryContainer  = Color(0xFFEDD9FF),        // light lavender
    secondary           = StremioMagenta,
    onSecondary         = StremioBackground,
    tertiary            = StremioViolet,
    onTertiary          = StremioBackground,
    background          = StremioBackground,       // #000000
    surface             = StremioSurface,          // #121212
    surfaceVariant      = StremioSurfaceVar,       // #1E1E1E
    surfaceTint         = StremioBackground,       // neutralizes M3 purple tint on elevated surfaces
    onBackground        = StremioCloudGrey,        // #FFFFFF
    onSurface           = StremioCloudGrey,        // #FFFFFF
    onSurfaceVariant    = StremioSubGrey,          // #A0A0A0 — medium emphasis
    outline             = ProOutline,              // #3D3D3D neutral borders
    error               = StremioError,
    onError             = StremioBackground
)

@Composable
fun PowerMETheme(themeMode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
