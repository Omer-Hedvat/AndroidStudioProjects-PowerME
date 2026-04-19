# BUG: Timed exercise target duration resets to 0 between workouts

## Status
[x] Fixed

## Severity
P1 high
- Core tracking feature broken: the duration set for a timed exercise during a workout is not saved, so every new workout starts at 0s instead of the previously used duration.

## Description
When a user sets a timed exercise duration during a workout (e.g. 60s for Plank), that value is not persisted for the next workout. The following session shows 0 (or the template default) instead of the last-used duration.

Standard exercises correctly persist weight and reps from the previous session into the PREV column and pre-fill the current session. Timed exercises should behave the same way for duration.

Root cause likely: the timed exercise duration is not being saved to the workout history (`WorkoutSet`), or the PREV-session lookup for timed exercises does not read the `durationSeconds` field.

## Steps to Reproduce
1. Start a workout with a timed exercise (e.g. Plank)
2. Set the duration to 60s and complete the set
3. Finish the workout
4. Start a new workout with the same routine
5. Observe: timed exercise duration shows 0 (or blank) instead of 60s

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `WorkoutDao.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: Neither `startWorkoutFromRoutine()` nor `addExercise()` pre-filled `ActiveSet.timeSeconds` from previous session ghost data. The field defaulted to `""`, so `totalSeconds = set.timeSeconds.toIntOrNull() ?: 0` → 0 every time.

Fix: in `startWorkoutFromRoutine()`, added `timeSeconds = ghost?.timeSeconds?.let { if (it > 0) it.toString() else "" } ?: ""`. In `addExercise()`, added `timeSeconds = prevTimeStr ?: ""` where `prevTimeStr` is derived from the previous session's `timeSeconds` field. Duration is now pre-filled from the most recent session on both code paths.
