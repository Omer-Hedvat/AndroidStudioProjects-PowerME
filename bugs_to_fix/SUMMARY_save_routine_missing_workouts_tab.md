# Fix Summary: Saved routine missing from Workouts tab after Save Routine flow

## Root Causes

Two separate bugs, both fixed:

**1. Ordering (RoutineDao):** `ORDER BY r.lastPerformed DESC` placed new routines (with `lastPerformed = null`) at the very bottom of the Workouts list because SQLite sorts NULLs last in DESC order. Fixed with `ORDER BY COALESCE(r.lastPerformed, r.updatedAt) DESC`.

**2. Silent no-op (WorkoutViewModel):** `saveWorkoutAsRoutine` had two early-return paths that could silently do nothing:
- It read `workoutId` from `_workoutState.value.workoutId` instead of the nav arg — fragile if state was cleared.
- It returned early with `if (sets.isEmpty()) return@launch`. This triggers whenever a user finishes a workout without tapping the individual per-set completion button (all sets are "incomplete", deleted by `deleteIncompleteSetsByWorkout()`, leaving zero completed sets in the DB). A user testing the flow by starting a workout and immediately finishing it would hit this every time.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/RoutineDao.kt` | `ORDER BY COALESCE(r.lastPerformed, r.updatedAt) DESC` in both active and archived queries |
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | `saveWorkoutAsRoutine(workoutId, routineName)` — `workoutId` now a parameter; removed `sets.isEmpty()` guard; added fallback to in-memory `_workoutState.value.exercises` when no completed sets exist in DB |
| `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` | Pass `workoutId` from nav arg directly: `workoutViewModel.saveWorkoutAsRoutine(summaryWorkoutId, name)` |

## Surfaces Fixed

- Workouts tab — newly saved routines now appear near the top of the list
- Post-workout WorkoutSummaryScreen — "Save as Routine" now reliably saves even if no sets were marked completed

## How to QA

1. Start a Quick Start workout, add 1–2 exercises, **without tapping any set's completion button**
2. Tap Finish Workout → post-workout summary screen
3. Tap "Save as Routine", enter a new name (e.g. "My Test Routine"), confirm
4. Navigate to the Workouts tab
5. Confirm the new routine appears near the top of the list with the correct exercises

Bonus: repeat steps 1–5 but this time complete at least one set (tap the checkmark). Both paths should work.
