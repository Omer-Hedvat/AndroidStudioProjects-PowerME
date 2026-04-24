# BUG: Saved routine missing from Workouts tab after post-workout Save Routine flow

## Status
[x] Fixed

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

**Root cause (ordering):** `RoutineDao.getAllActiveRoutinesWithExerciseNames()` ordered by `r.lastPerformed DESC`. SQLite treats NULL as smaller than any value, so in DESC order NULLs sort last. A brand-new routine always has `lastPerformed = null`, so it landed at the very bottom — invisible unless the user scrolled all the way down. Fix: `ORDER BY COALESCE(r.lastPerformed, r.updatedAt) DESC` in both active and archived queries.

**Root cause (silent no-op):** `WorkoutViewModel.saveWorkoutAsRoutine()` read `_workoutState.value.workoutId` directly (could be null in edge cases) and returned early if the DB had zero completed sets (`sets.isEmpty()`). The latter triggers when a user finishes a workout without tapping the per-set completion button — all sets are "incomplete", deleted by `deleteIncompleteSetsByWorkout()`, and the save silently does nothing. Also, `workoutId` was read from ViewModel state rather than the nav arg, creating a dependency on internal state that doesn't hold across all navigation paths.

**Fix:** Changed `saveWorkoutAsRoutine(routineName)` → `saveWorkoutAsRoutine(workoutId, routineName)`. Navigation now passes `workoutId` directly from the nav argument. Removed the `sets.isEmpty()` early-return guard: if no completed sets exist in the DB, falls back to building the routine from `_workoutState.value.exercises` (in-memory state preserved after `finishWorkout()`), using each exercise's current sets for structure, weights, and reps.
