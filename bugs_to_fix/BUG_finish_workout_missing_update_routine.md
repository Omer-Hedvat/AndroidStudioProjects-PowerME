# BUG: No "Update Routine" option when finishing a workout started from a saved routine

## Status
[x] Fixed

## Severity
P1 high
- Core post-workout flow: users who start from a saved routine and make changes (remove/add exercises) have no way to persist those changes back to the routine. Only "Save as Routine" (creates a new entry) and "Done" are offered.

## Description
When a user starts a workout from an existing saved routine, modifies it (e.g. removes an exercise), and then taps Finish, the post-workout summary screen only shows:
- "Save as Routine" — creates a brand-new routine
- "Done" — discards changes

There is no "Update Routine" option to save changes back to the original routine. This forces users to either create a duplicate or lose their edits.

Expected: if the workout was started from a saved routine (`sourceRoutineId != null`), an "Update Routine" CTA should appear that overwrites the original routine's exercise list / parameters with the workout's final state.

## Steps to Reproduce
1. Go to Workouts tab → select a saved routine → Start Workout.
2. Remove one exercise during the workout.
3. Finish the workout.
4. Observe: post-workout options are "Save as Routine" + "Done". No "Update Routine".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModel.kt`, `WorkoutViewModel.kt`, `RoutineRepository.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
