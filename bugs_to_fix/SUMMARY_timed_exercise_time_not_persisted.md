# SUMMARY: BUG_timed_exercise_time_not_persisted

## What Was Fixed
Timed exercise duration now pre-fills from the previous session on both the routine-start and mid-workout-add code paths. Previously `ActiveSet.timeSeconds` always defaulted to `""`, so every set started the timer at 0s.

## Root Cause
Neither `startWorkoutFromRoutine()` (lines 483–493) nor `addExercise()` (lines 739–748) copied the previous session's `timeSeconds` into `ActiveSet.timeSeconds`. The field defaulted to `""`, causing `totalSeconds = set.timeSeconds.toIntOrNull() ?: 0` in `TimedSetRow` to evaluate to 0.

## Fix
- `startWorkoutFromRoutine()`: added `timeSeconds = ghost?.timeSeconds?.let { if (it > 0) it.toString() else "" } ?: ""`
- `addExercise()`: added `timeSeconds = prevTimeStr ?: ""` (same `prevTimeStr` used for `ghostTimeSeconds`)

## Files Changed
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt`
- `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` (new tests)

## Tests Added
- `addExercise pre-fills timeSeconds from previous session for all sets`
- `addExercise with zero timeSeconds in previous session does not pre-fill timeSeconds`
