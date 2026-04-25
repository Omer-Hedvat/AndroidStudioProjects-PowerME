# Keep Screen On — 3-Mode Selector

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `data/AppSettingsDataStore.kt`, `ui/workout/ActiveWorkoutScreen.kt`, `MainActivity.kt` |

> Read `SETTINGS_SPEC.md §2.5` before touching any file.

---

## Overview

The current "Keep screen on" setting is a Boolean toggle. Replace it with a 3-mode selector so users can choose: **Always on** (screen never sleeps in the app), **During active workout** (screen stays on only while a workout is running), or **Off** (OS default behaviour).

---

## Behaviour

### Modes

| Mode | Value | Screen behaviour |
|---|---|---|
| **Always on** | `ALWAYS` | `FLAG_KEEP_SCREEN_ON` applied on every screen in the app |
| **During workout** | `DURING_WORKOUT` | `FLAG_KEEP_SCREEN_ON` applied only while `ActiveWorkoutScreen` is visible (i.e. while `WorkoutViewModel.isWorkoutActive == true`) |
| **Off** | `OFF` | No flag set — OS default sleep behaviour |

### Flag application

- `ALWAYS` — set the flag in `MainActivity.onCreate()` (or via a Compose `LaunchedEffect` observing the mode at the top level).
- `DURING_WORKOUT` — `ActiveWorkoutScreen` sets the flag on `onResume`/composition and clears it on `onPause`/disposal using `DisposableEffect`.
- `OFF` — never set the flag; clear it if previously set.

Use `view.keepScreenOn = true/false` inside a Compose `DisposableEffect` wrapping `LocalView.current` — the established pattern in the project.

### Migration

`AppSettingsDataStore` currently stores `keepScreenOn: Boolean`. Replace with a new `String` key `keep_screen_on_mode` storing the enum name. On first read, migrate the old boolean: `true → ALWAYS`, `false → OFF`. Remove the old boolean key after migration.

Default value: `DURING_WORKOUT` (most users want screen-on only during workouts).

---

## Data Model

```kotlin
enum class KeepScreenOnMode { ALWAYS, DURING_WORKOUT, OFF }
```

- `AppSettingsDataStore`: replace `setKeepScreenOn(Boolean)` + `keepScreenOn: Flow<Boolean>` with `setKeepScreenOnMode(KeepScreenOnMode)` + `keepScreenOnMode: Flow<KeepScreenOnMode>`.
- `SettingsUiState`: replace `keepScreenOn: Boolean` with `keepScreenOnMode: KeepScreenOnMode`.
- `SettingsViewModel`: replace `toggleKeepScreenOn()` with `setKeepScreenOnMode(KeepScreenOnMode)`.

---

## UI Changes

**`SettingsScreen` — Display & Workout card:**

Replace the `Switch` row with a `SingleChoiceSegmentedButtonRow` (3 segments):
- Segment labels: `"Always on"` / `"During workout"` / `"Off"`
- Selected segment → `viewModel.setKeepScreenOnMode(...)`
- Use `MaterialTheme.colorScheme` tokens, same style as Appearance and Units segmented rows.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/AppSettingsDataStore.kt` — replace Boolean key with `KeepScreenOnMode` enum key; add migration
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — replace `toggleKeepScreenOn()` with `setKeepScreenOnMode()`; update `SettingsUiState`
- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — replace Switch with `SingleChoiceSegmentedButtonRow`
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — `DisposableEffect` to set/clear `FLAG_KEEP_SCREEN_ON` when mode is `DURING_WORKOUT`
- `app/src/main/java/com/powerme/app/MainActivity.kt` — apply flag when mode is `ALWAYS`

---

## How to QA

1. Open Settings → Display & Workout. Verify "Keep screen on" shows a 3-segment selector: Always on / During workout / Off.
2. Set **Always on** → navigate around the app → verify screen never sleeps.
3. Set **During workout** → start a workout → verify screen stays on → finish workout → verify screen can sleep again.
4. Set **Off** → start a workout → verify screen can sleep normally.
5. Upgrade path: fresh install gets default `DURING_WORKOUT`. Upgrade from old boolean `true` → `ALWAYS`; `false` → `OFF`.
