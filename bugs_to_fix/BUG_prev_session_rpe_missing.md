# BUG: RPE missing from previous session values in active workout

## Status
[x] Fixed

## Description
In the active workout screen, the PREV column shows weight and reps from the last session for each set, but RPE values are not displayed. When a user recorded RPE in their previous workout session, those RPE values should appear in the PREV column alongside weight/reps so the user can reference their previous effort level. Likely root cause is in `WorkoutViewModel` (previous session data loading) or `ActiveWorkoutScreen.kt` (PREV column rendering) — RPE may not be included in the previous session query or not rendered in the PREV display.

Affected screen: `ActiveWorkoutScreen.kt` — set row PREV column.

## Severity
P1

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `WorkoutViewModelTest.kt`

## Steps to Reproduce
1. Complete a workout with RPE values recorded on sets
2. Start a new workout using the same routine
3. Look at the PREV column on set rows
4. Observe: weight and reps from previous session are shown, but RPE is missing

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `startWorkoutFromRoutine()` in `WorkoutViewModel.kt` built `ActiveSet` objects from the ghost (previous session) `WorkoutSet` data but omitted `ghostRpe`. The ghost data already contained RPE (from the `getPreviousSessionSets` DAO query), and `ActiveSet.ghostRpe` and `formatGhostLabel()` in `ActiveWorkoutScreen.kt` already supported rendering it — only the assignment was missing.

Fix: Added `ghostRpe = ghost?.rpe?.toString()` to the `ActiveSet` construction block in `startWorkoutFromRoutine()` (line 488). This mirrors the identical assignment already present in the "add exercise mid-workout" code path.

Two unit tests added to `WorkoutViewModelTest.kt`:
- `startWorkoutFromRoutine populates ghostRpe from previous session data` — verifies RPE is propagated to `ghostRpe` on each set
- `startWorkoutFromRoutine ghostRpe is null when previous session has no RPE` — verifies null RPE is handled correctly
