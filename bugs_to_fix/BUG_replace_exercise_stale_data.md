# BUG: Replacing an exercise in active workout keeps stale data from old exercise

## Status
[x] Fixed

## Severity
P1 high
- Core workout flow: replacing an exercise should start fresh; carrying over weights, reps, and notes from a different exercise is misleading and incorrect.

## Description
When the user replaces an exercise mid-workout (via the exercise kebab menu → Replace), the new exercise inherits the in-memory set rows from the old one — including weights, reps, prev values, and notes. The new exercise should start with default/empty state.

Likely root cause: the replace flow swaps the exercise reference but reuses the existing `ExerciseWithSets` entry (sets list, notes, etc.) rather than initialising fresh sets for the new exercise.

## Steps to Reproduce
1. Start an active workout with at least one exercise.
2. Enter some weight, reps, or notes for a set.
3. Open the exercise kebab menu → Replace.
4. Select a different exercise.
5. Observe: new exercise row shows the old weights, reps, prev values, and notes.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
