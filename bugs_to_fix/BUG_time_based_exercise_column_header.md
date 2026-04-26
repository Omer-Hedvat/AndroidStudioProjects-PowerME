# BUG: Time-based exercise column header shows 'Reps' instead of 'Time'

## Status
[x] Fixed

## Severity
P2 normal
- Cosmetic/label issue — wrong column header displayed for time-based exercises in active workout screen

## Description
In the active workout screen, exercises that are time-based (measured in seconds/duration rather than rep count) display a column header of "Reps" instead of "Time". The label should reflect the set's tracking mode: "Reps" for rep-based exercises, "Time" for time/hold-based exercises.

Likely root cause: the column header composable in `ActiveWorkoutScreen.kt` (or a shared set row header) uses a static "Reps" string rather than checking the exercise's tracking type (reps vs. time/holdSeconds).

## Steps to Reproduce
1. Create a routine that includes at least one time-based exercise (e.g. Plank, Wall Sit, or any exercise tracked by duration/holdSeconds).
2. Start an active workout from that routine.
3. Observe: the column header for the time-based exercise reads "Reps" instead of "Time".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, possibly `ui/workout/components/SetRow.kt` or equivalent set header composable

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
In `FunctionalBlockActiveCard`, computed `val allTimed = exercises.all { it.exercise.exerciseType == ExerciseType.TIMED }` immediately after `alreadyRun`. The column header now renders `if (allTimed) "TIME" else "REPS"` instead of the hardcoded `"REPS"`.

Touches: `ui/workout/ActiveWorkoutScreen.kt` (2 lines changed)
