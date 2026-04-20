# Fix Summary: Post-workout state not cleared — Resume button loops to finished workout's summary

## Root Cause

All three `startWorkout*` functions (`startWorkout`, `startWorkoutFromRoutine`, `startWorkoutFromPlan`) used `_workoutState.update { it.copy(...) }` to set up the new workout. This preserved `pendingWorkoutSummary` (and other fields like `hiddenRestSeparators`, `restTimeOverrides`, `collapsedExerciseIds`, `deletedSetClipboard`) from the **previous** finished workout.

When `ActiveWorkoutScreen` rendered for the new workout, `LaunchedEffect(pendingWorkoutSummary)` at line 168 fired immediately because `pendingWorkoutSummary` was still non-null. This called `onWorkoutFinished()`, which read the stale `_lastFinishedWorkoutId` (also never cleared on new workout start) and navigated directly to the old workout's summary — bypassing the new workout entirely.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Synchronous full state reset in all `startWorkout*` functions; clear `_lastFinishedWorkoutId`/`_lastPendingRoutineSync` in start functions and `dismissWorkoutSummary()` |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | 4 new tests covering the regression scenario |

## Surfaces Fixed

- Starting any workout (routine, quick-start, AI) after finishing a previous one in the same session now correctly launches the new workout instead of redirecting to the old summary
- `dismissWorkoutSummary()` now also clears `_lastFinishedWorkoutId` for consistency

## How to QA

1. Start a routine-based workout
2. Complete at least one set
3. Tap **Finish Workout**
4. View the post-workout summary
5. Navigate **back** to the Workouts tab (via back button or bottom nav)
6. Tap **Start Workout** on any routine — should navigate to a fresh active workout ✅
7. Alternatively tap **Quick Start** — should also enter a fresh workout ✅
8. Finish the second workout — summary should show the second workout's data, not the first ✅
