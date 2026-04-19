# SUMMARY: BUG_timed_exercise_prev_rpe_missing

## What Was Fixed
PREV column for timed exercises now shows RPE (e.g. `60s@8`) when the previous session recorded an RPE value.

## Root Cause
`formatGhostTimedLabel(weight, timeSeconds)` in `ActiveWorkoutScreen.kt` (line 1134) took only two parameters and never appended RPE. Both IDLE-state (line 1972) and COMPLETED-state (line 2202) call sites passed only `ghostWeight` and `ghostTimeSeconds`.

## Fix
- Updated `formatGhostTimedLabel` to accept `rpe: String?` as a third parameter
- Appends `@$rpe` to the base label when `rpe != null` (matching the `formatGhostLabel` strength pattern)
- Both call sites now pass `set.ghostRpe` as the third argument

## Files Changed
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt`
- `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` (new test)

## Tests Added
- `addExercise populates ghostRpe for timed exercise sets from previous session`
