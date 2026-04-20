# BUG: PREV RPE shown as 10x the actual value (9 shows as 90)

## Status
[x] Fixed

## Severity
P2 normal
- PREV column shows RPE as 90, 80 etc. instead of 9, 8. Confusing but doesn't block the workout.

## Description
In the active workout screen PREV column, RPE values are displayed multiplied by 10. A stored RPE of 9 appears as "90", RPE 8 appears as "80". This is visible in the `weight×reps@RPE` format — e.g. "32×8@90" instead of "32×8@9".

Likely root cause: RPE is stored as a float (e.g. 9.0) and when converted to a display string, the decimal point is dropped and/or the value is multiplied by 10 somewhere in the ghost data formatting path. Alternatively, RPE may be stored on a 0–100 scale internally and not divided by 10 before display in the PREV formatter.

## Steps to Reproduce
1. Complete a workout with RPE values logged (e.g. RPE 9)
2. Start a new workout with the same routine
3. Observe PREV column — RPE shows as 90 instead of 9

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: RPE is stored in `workout_sets.rpe` on a ×10 scale (60=6.0, 80=8.0, 90=9.0, 100=10.0) matching the `RpeInfo.value` from `RPE_SCALE`. Ghost RPE was converted with `.toString()` directly, yielding "90" instead of "9".

Fix: added `formatGhostRpe(rpe: Int): String` helper (matches the live display logic at `ActiveWorkoutScreen.kt:1694-1697`) and used it in both `startWorkoutFromRoutine` and `addExercise`. Also corrected two existing tests that used wrong `rpe = 8`/`9` test data (should be `80`/`90`).
