# RPE Auto-Pop — Workout Style Selector

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `data/AppSettingsDataStore.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `ui/workout/WorkoutViewModel.kt` |

> Read `SETTINGS_SPEC.md §2.5` and `WORKOUT_SPEC.md` (RPE picker section) before touching any file.

---

## Overview

The current "Use RPE" setting is a Boolean toggle: RPE auto-pops after every set, or never. Replace it with a 4-mode selector so users can choose which workout style(s) trigger the RPE picker — matching the `WorkoutStyle` enum already in the codebase (`PURE_GYM`, `PURE_FUNCTIONAL`, `HYBRID`) plus `OFF`.

---

## Behaviour

### Modes

| Mode | Label | When RPE auto-pops |
|---|---|---|
| `PURE_GYM` | "Strength only" | Only during `PURE_GYM` workouts |
| `PURE_FUNCTIONAL` | "Functional only" | Only during `PURE_FUNCTIONAL` workouts |
| `HYBRID` | "All workouts" | During any workout style (PURE_GYM, PURE_FUNCTIONAL, HYBRID) |
| `OFF` | "Off" | Never — RPE picker only opened manually |

> "All workouts" is used as the label for `HYBRID` because it's the most intuitive description for end users (not an internal enum name).

### Trigger logic in `WorkoutViewModel`

The existing `rpeAutoPopTarget` signal is emitted after each set completion. Add a check against the new mode:

```kotlin
val shouldAutoPop = when (rpeMode) {
    RpeMode.OFF -> false
    RpeMode.PURE_GYM -> currentWorkout.workoutStyle == WorkoutStyle.PURE_GYM
    RpeMode.PURE_FUNCTIONAL -> currentWorkout.workoutStyle == WorkoutStyle.PURE_FUNCTIONAL
    RpeMode.HYBRID -> true // fires for any style
}
```

Only emit `rpeAutoPopTarget` if `shouldAutoPop == true`.

### Migration

`AppSettingsDataStore` currently stores `useRpeAutoPop: Boolean`. Replace with a new `String` key `rpe_mode` storing the enum name (`RpeMode`). On first read, migrate the old boolean: `true → HYBRID` (was on for all workouts), `false → OFF`. Remove the old boolean key after migration.

Default value: `OFF` (opt-in; most users don't use RPE by default).

---

## Data Model

```kotlin
enum class RpeMode { PURE_GYM, PURE_FUNCTIONAL, HYBRID, OFF }
```

- `AppSettingsDataStore`: replace `setUseRpeAutoPop(Boolean)` + `useRpeAutoPop: Flow<Boolean>` with `setRpeMode(RpeMode)` + `rpeMode: Flow<RpeMode>`.
- `SettingsUiState`: replace `useRpeAutoPop: Boolean` with `rpeMode: RpeMode`.
- `SettingsViewModel`: replace `toggleUseRpeAutoPop()` with `setRpeMode(RpeMode)`.
- `WorkoutViewModel`: consume `rpeMode: RpeMode` from `AppSettingsDataStore` and apply the trigger logic above.

---

## UI Changes

**`SettingsScreen` — Display & Workout card:**

Replace the "Use RPE" `Switch` row with a labelled `SingleChoiceSegmentedButtonRow` (4 segments):
- Segments: `"Gym only"` / `"Functional"` / `"All workouts"` / `"Off"`
- Row label above the segmented row: `"RPE auto-pop"`
- Selected segment → `viewModel.setRpeMode(...)`
- Same visual style as Appearance and Units rows.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/AppSettingsDataStore.kt` — replace Boolean key with `RpeMode` enum key; add migration from old boolean
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — replace `toggleUseRpeAutoPop()` with `setRpeMode()`; update `SettingsUiState`
- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — replace Switch with `SingleChoiceSegmentedButtonRow`
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — update `rpeAutoPopTarget` emit guard to check `RpeMode` vs current `workoutStyle`

---

## How to QA

1. Open Settings → Display & Workout. Verify "RPE auto-pop" shows a 4-segment row: Gym only / Functional / All workouts / Off.
2. Set **Gym only** → start a PURE_GYM workout → complete a set → verify RPE picker auto-opens. Then start a PURE_FUNCTIONAL workout → complete a set → verify RPE picker does NOT auto-open.
3. Set **Functional** → start a PURE_FUNCTIONAL workout → verify RPE auto-pops. Start a PURE_GYM workout → verify it does NOT.
4. Set **All workouts** → verify RPE auto-pops in every workout style.
5. Set **Off** → verify RPE never auto-pops regardless of workout style.
6. Upgrade path: old `useRpeAutoPop = true` → migrates to `HYBRID` (All workouts). `false` → `OFF`.
