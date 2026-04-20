# Fix Summary: RPE missing from previous session values in active workout

## Root Cause

`WorkoutViewModel.startWorkoutFromRoutine()` built `ActiveSet` objects from ghost (previous session) `WorkoutSet` data but omitted `ghostRpe`. The fix is a single missing line: `ghostRpe = ghost?.rpe?.toString()`.

The ghost data was already fully populated by `getPreviousSessionSets` DAO query (which returns `WorkoutSet` including `rpe: Int?`). The `ActiveSet.ghostRpe` field and `formatGhostLabel()` in `ActiveWorkoutScreen.kt` already handled RPE rendering correctly. The "add exercise mid-workout" code path (Path B) was already assigning `ghostRpe`. Only the routine-start path (Path A) was missing the assignment.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added `ghostRpe = ghost?.rpe?.toString()` to `ActiveSet` construction in `startWorkoutFromRoutine()` |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added 2 unit tests verifying `ghostRpe` is populated (with RPE) and null (without RPE) when starting from a routine |

## Surfaces Fixed

- Active workout screen → PREV column → now shows `weight×reps@rpe` (e.g. `80×10@8`) when the previous session had RPE logged, for all workouts started from a routine

## How to QA

1. Complete a workout from a routine and record RPE values on at least one set (e.g. set RPE to 8 on set 1)
2. Start a new workout using the same routine
3. Look at the set rows for that exercise
4. Verify: the PREV column shows `<weight>×<reps>@<rpe>` (e.g. `80×10@8`) instead of just `80×10`
5. Also verify: for sets where no RPE was recorded in the previous session, PREV column shows `<weight>×<reps>` with no `@` suffix
