# Fix Summary: Switch/Toggle colors inconsistent across screens

## Root Cause
Three different color approaches used across 6 switches:
1. `SettingsScreen.kt` used `colorScheme.surface` for `checkedThumbColor` — in dark mode this resolves to near-black (`#1C1C1C`), making the thumb invisible on the violet track.
2. `ActiveWorkoutScreen.kt` hardcoded `Color.White`/`Color.Black` via `isSystemInDarkTheme()` — bypasses app `ThemeMode` setting, responds only to system dark mode.
3. Neither file specified unchecked state colors, leaving them as M3 defaults which don't match the app's palette.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` | 4 switches: corrected `checkedThumbColor` from `surface` → `onSurface`; added `uncheckedThumbColor = onSurface` and `uncheckedTrackColor = surfaceVariant` |
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | 1 switch: removed hardcoded `Color.White`/`Color.Black` and `isSystemInDarkTheme()` check; replaced with standard token pattern; removed unused import |

## Surfaces Fixed
- Settings → Rest Timers card (Audio, Haptics toggles)
- Settings → Display & Workout card (Keep Screen On, Use RPE toggles)
- Active Workout → Set Rest Timers dialog (Rest after last set toggle)

## How to QA
1. Open Settings in **dark mode** → Rest Timers card → Audio and Haptics toggles: thumb should be near-white, checked track violet, unchecked track dark grey
2. Open Settings in **light mode** → same toggles: thumb should be near-black, checked track deep violet, unchecked track pale lavender
3. Open Settings → Display & Workout → repeat checks for Keep Screen On and Use RPE
4. Start any workout → long-press a set → "Set Rest Timers" dialog → check the "Rest after last set" toggle in both modes
5. Set app ThemeMode to Dark while system is in Light → confirm ActiveWorkoutScreen toggle still shows correct dark-mode colors (was broken before — was using system theme, not app theme)
