package com.powerme.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.powerme.app.data.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary          = LightPrimary,       // #6B3FA0 — WCAG AA on white
    secondary        = LightSecondary,     // #7D3B65
    tertiary         = LightPrimary,
    background       = Slate50,
    surface          = Slate100,
    surfaceVariant   = Color(0xFFEDE7F6),  // Light violet tint for chips/rows
    onPrimary        = LightBackground,
    onSecondary      = LightBackground,
    onTertiary       = LightBackground,
    onBackground     = Slate900,
    onSurface        = Slate900,
    onSurfaceVariant = Slate900,
    error            = LightError,         // #B3261E — M3 standard
    onError          = LightBackground
)

private val DarkColorScheme = darkColorScheme(
    primary             = ProViolet,            // #9B7DDB — desaturated lavender-violet
    onPrimary           = ProBackground,        // #0F0D13
    primaryContainer    = Color(0xFF2D2052),    // muted deep purple
    onPrimaryContainer  = Color(0xFFE0D4F0),   // muted lavender
    secondary           = ProMagenta,           // #9E6B8A — dusty rose
    onSecondary         = ProBackground,
    tertiary            = ProViolet,
    onTertiary          = ProBackground,
    background          = ProBackground,        // #0F0D13
    surface             = ProSurface,           // #1C1A24
    surfaceVariant      = ProSurfaceVar,        // #28253A
    surfaceTint         = ProBackground,        // suppress M3 purple elevation tint
    onBackground        = ProCloudGrey,         // #E8E4F0 — lavender off-white
    onSurface           = ProCloudGrey,         // #E8E4F0
    onSurfaceVariant    = ProSubGrey,           // #9E99AB — medium emphasis
    outline             = ProOutline,           // #3A3650 — purple-tinted borders
    error               = ProError,             // #E05555 — desaturated red
    onError             = ProBackground
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
