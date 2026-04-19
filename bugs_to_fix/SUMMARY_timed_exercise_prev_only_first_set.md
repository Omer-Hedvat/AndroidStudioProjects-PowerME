# SUMMARY: BUG_timed_exercise_prev_only_first_set

## What Was Fixed
`addExercise()` in `WorkoutViewModel.kt` never populated `ghostTimeSeconds` in the `ActiveSet` built from previous session data. All timed exercise set rows showed "--" in the PREV column regardless of set index.

## Root Cause
Lines 739–748 in `addExercise()` set `ghostWeight`, `ghostReps`, and `ghostRpe` from `prevSet`, but omitted `ghostTimeSeconds` entirely.

## Fix
- Extracted `prevTimeStr = prevSet.timeSeconds?.let { if (it > 0) it.toString() else null }`
- Assigned it to `ghostTimeSeconds` (PREV hint) and `timeSeconds` (active field pre-fill) on every set in `addExercise()`
- Combined with the `startWorkoutFromRoutine()` `timeSeconds` pre-fill from BUG_timed_exercise_time_not_persisted fix

## Files Changed
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt`
- `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` (new tests)

## Tests Added
- `addExercise populates ghostTimeSeconds for all set indices from previous session`
