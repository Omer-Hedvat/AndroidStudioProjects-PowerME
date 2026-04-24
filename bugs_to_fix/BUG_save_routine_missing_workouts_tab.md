# BUG: Saved routine missing from Workouts tab after post-workout Save Routine flow

## Status
[ ] Open

## Severity
P1 — visible regression affecting daily use; saving a workout as a routine is a core flow

## Description
After finishing a workout and going through the post-workout "Save Routine" sheet (entering a new routine name and confirming), the newly saved routine does not appear in the Workouts tab (routines list). The routine may be written to the database but the Workouts screen does not reflect it, or the save may be silently failing.

## Steps to Reproduce
1. Start and complete a workout (Quick Start or from an existing routine)
2. On the post-workout summary, tap "Save Routine" (or equivalent CTA)
3. Enter a new routine name and confirm
4. Navigate to the Workouts tab
5. Observe: the newly named routine is absent from the list

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutSummaryViewModel.kt`, `WorkoutSummaryScreen.kt`, `WorkoutsViewModel.kt`, `WorkoutsScreen.kt`, `RoutineRepository.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
