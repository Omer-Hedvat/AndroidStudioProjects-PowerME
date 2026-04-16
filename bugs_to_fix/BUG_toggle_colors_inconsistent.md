# BUG: Switch/Toggle colors inconsistent across screens

## Status
[x] Fixed

## Description
Switch composables across the app used three different color approaches, causing visual inconsistency and broken appearance depending on ThemeMode:

- `SettingsScreen.kt` (4 switches): `checkedThumbColor = colorScheme.surface` — wrong token; `surface` in dark mode is near-black (`#1C1C1C`), making the thumb invisible on the violet track. Missing `uncheckedThumbColor` and `uncheckedTrackColor`.
- `ActiveWorkoutScreen.kt` (1 switch): hardcoded `Color.White` / `Color.Black` via `isSystemInDarkTheme()` — does not respond to app's `ThemeMode` setting (only to system dark mode). Missing track colors entirely.
- `ToolsScreen.kt` (1 switch): correct — already used theme tokens with all four parameters.

## Steps to Reproduce
1. Open Settings → Rest Timers card → observe Audio/Haptics toggles in dark mode: thumb is near-invisible
2. Set ThemeMode to Dark in app but leave system in Light → open the "Rest after last set" dialog in active workout → toggle shows wrong colors

## Fix Notes
Standardized all 5 broken switches to the canonical pattern (already used in ToolsScreen):
```kotlin
colors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onSurface,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
)
```
- Removed hardcoded `Color.White`/`Color.Black` and the `isSystemInDarkTheme()` check from `ActiveWorkoutScreen.kt`
- Removed now-unused `import androidx.compose.foundation.isSystemInDarkTheme` from `ActiveWorkoutScreen.kt`
