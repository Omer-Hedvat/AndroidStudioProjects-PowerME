# BUG: Post-workout resume loop regression — cannot escape summary state

## Status
[x] Fixed

## Severity
P0 — blocks core workout flow (cannot start a new workout after finishing one)

## Description
After finishing a workout the app enters a bad state: the active workout bar persists but is not collapsed, and navigating to Resume or Edit takes the user to the finished workout's summary screen instead of the workout. Starting a new workout also redirects to the previous workout's summary. The only way out appears to be force-quitting the app.

This is a regression of BUG_post_workout_state_not_cleared (previously Wrapped). The WorkoutViewModel state machine is not fully clearing post-finish — likely `activeWorkoutState` or `pendingSummary` is still non-null when it should have been reset to idle.

## Steps to Reproduce
1. Start and complete a workout (log sets, tap Finish Workout).
2. Confirm the post-workout summary.
3. Observe: the "Resume Workout" bar is still visible at the bottom (not collapsed).
4. Tap "Resume Workout" or "Edit" — navigates to the finished workout's summary instead of the workout.
5. Try starting a new workout from the Workouts tab — also navigates to the previous summary.
6. App is stuck until force-killed.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `PowerMeNavigation.kt`, `WorkoutViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md` (post-workout state machine section)
- Prior fix: `BUG_post_workout_state_not_cleared.md` (Wrapped — regression)

## Fix Notes
**Root cause:** `startEditMode()` launched its coroutine immediately without a synchronous pre-flight state reset. When the navigation code called `startEditMode(routineId)` followed immediately by `navController.navigate(Routes.WORKOUT)`, `ActiveWorkoutScreen` mounted before the coroutine ran, seeing the stale `pendingWorkoutSummary` from the previous finished workout. `LaunchedEffect(workoutState.pendingWorkoutSummary)` fired immediately → `onWorkoutFinished()` → `_lastFinishedWorkoutId` was also still set (never cleared) → navigation to old workout summary. Same loop as the original bug, different trigger path (template edit instead of new workout start).

**Fix:**
1. `WorkoutViewModel.startEditMode()`: Added synchronous pre-coroutine clearing of `_lastFinishedWorkoutId`, `_lastPendingRoutineSync`, and `pendingWorkoutSummary` — mirrors the pattern already established in `startWorkoutFromRoutine()`.
2. `WorkoutViewModel.dismissWorkoutSummary()`: Added `isActive` guard so calling it while a workout is running (e.g., via `DisposableEffect` race) only clears the summary overlay instead of resetting the entire state.
3. `PowerMeNavigation.kt` (WORKOUT_SUMMARY composable): Added `DisposableEffect(Unit) { onDispose { if (isPostWorkout) workoutViewModel.dismissWorkoutSummary() } }` to clean up post-workout state for all dismissal paths (toolbar back, Done button, system back gesture).
4. Two new regression tests covering synchronous clearing of `pendingWorkoutSummary` and `lastFinishedWorkoutId` in `startEditMode()`.
