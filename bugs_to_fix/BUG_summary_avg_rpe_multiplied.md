# BUG: Workout summary avg RPE shown as 10× actual value

## Status
[x] Fixed

## Severity
P2 normal
- Avg RPE per exercise on the post-workout summary screen shows the raw stored value (×10 scale) instead of the display value. E.g. sets @8, 9.5, 7, 10 → avg displays as 86.3 instead of 8.6.

## Description
RPE is stored internally as an integer on a ×10 scale (8.0 → 80, 9.5 → 95). The avg RPE computation in WorkoutSummaryViewModel (or WorkoutSummaryScreen) is averaging the raw stored integers and displaying the result without dividing by 10. The same root cause was fixed for the PREV column (BUG_prev_rpe_multiplied) but the summary avg was missed.

## Steps to Reproduce
1. Complete a workout with RPE logged on multiple sets (e.g. 8, 9.5, 7, 10)
2. View the post-workout WorkoutSummaryScreen
3. Observe: avg RPE shows ~86.3 instead of ~8.6

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutSummaryViewModel.kt`, `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `buildExerciseCard()` in `WorkoutSummaryViewModel.kt` averaged the raw storage-scale RPE values (`rpeValues.average()`) without dividing by 10. RPE is stored on a ×10 scale (80 = RPE 8.0, 95 = RPE 9.5), so the displayed avg was 10× the correct value (e.g. 86.25 instead of 8.625).

Fix: changed `rpeValues.average()` → `rpeValues.average() / 10.0`. The `isGoldenZone` check (`>= 8.0 && <= 9.0`) was already correct for display-scale values and needed no change.

Individual set RPE display in `SetDetailRow` (screen-side) was already correct — it manually divides by 10 via `val whole = set.rpe / 10; val fraction = set.rpe % 10`.

Also fixed existing unit tests that were using display-scale RPE values (8, 9, 7) as test inputs instead of storage-scale (80, 90, 70). Added a new regression test `avgRpe divides stored x10 values by 10 for display` covering the exact bug scenario (4 sets with storage RPE 80, 95, 70, 100 → expected display avg 8.625, not 86.25).
