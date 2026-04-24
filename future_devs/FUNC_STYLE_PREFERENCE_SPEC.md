# Functional Training — WorkoutStyle Preference + Settings Card

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | func_block_entities_migration |
| **Touches** | `data/WorkoutStyle.kt` (new), `data/AppSettingsDataStore.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `data/sync/FirestoreSyncManager.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §7` — read before touching any file in this task.

---

## Overview

Add a new `WorkoutStyle` preference (`PURE_GYM` / `PURE_FUNCTIONAL` / `HYBRID`, default `HYBRID`) that gates the "Add" UX in the Template Builder. `PURE_GYM` preserves today's flow unchanged; `PURE_FUNCTIONAL` opens the Functional Block Wizard directly; `HYBRID` shows a chooser sheet.

This task is Tier 0 — it is a pure preference with no DB schema changes and no UI outside the Settings screen.

---

## Behaviour

- Preference stored in `AppSettingsDataStore` (Jetpack `preferencesDataStore`) using the existing `ThemeMode`/`UnitSystem` pattern.
- Synced to Firestore via `pushAppPreferences()` on every change — same as `timerSound`, `keepScreenOn`, etc.
- Default: `HYBRID`.
- Settings card inserted between the **Units** card and the **Health Connect** card using `SingleChoiceSegmentedButtonRow` (3 options: "Pure Gym", "Pure Functional", "Hybrid").

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/WorkoutStyle.kt` (NEW) — enum class
- `app/src/main/java/com/powerme/app/data/AppSettingsDataStore.kt` — add `WORKOUT_STYLE_KEY`, `workoutStyle: Flow<WorkoutStyle>`, `setWorkoutStyle()`
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — add `workoutStyle` to `SettingsUiState`, collect in `loadAppSettings()`, expose `setWorkoutStyle()`
- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — new `SettingsCard("Workout Style")` with `SingleChoiceSegmentedButtonRow`
- `app/src/main/java/com/powerme/app/data/sync/FirestoreSyncManager.kt` — include `workoutStyle` in `pushAppPreferences()` / pull

---

## How to QA

1. Open Settings. Verify a "Workout Style" card appears between Units and Health Connect.
2. Tap each of the three options; verify selection persists after backgrounding and reopening.
3. Sign in on a second device (or reinstall); verify the preference synced from Firestore.
