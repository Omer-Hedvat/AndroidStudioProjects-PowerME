# Fix Summary: Post-workout resume loop regression

## Root Cause

`startEditMode()` launched its entire coroutine without a synchronous pre-flight state reset. The navigation flow was:

```kotlin
workoutViewModel.startEditMode(routineId)     // launches coroutine — hasn't run yet
navController.navigate(Routes.WORKOUT)         // ActiveWorkoutScreen mounts immediately
```

`ActiveWorkoutScreen` mounted before the coroutine had a chance to clear state, seeing the stale `pendingWorkoutSummary` from the previous finished workout. `LaunchedEffect(workoutState.pendingWorkoutSummary)` fired immediately → `onWorkoutFinished()` ran → `_lastFinishedWorkoutId` was also still set (never cleared by template-edit path) → navigated to the old workout summary. The loop was identical to the original bug (`BUG_post_workout_state_not_cleared`), triggered via a different code path.

Additionally, `dismissWorkoutSummary()` was never called from the navigation when the post-workout summary was dismissed, leaving `pendingWorkoutSummary` and `_lastFinishedWorkoutId` stale for any subsequent navigation to the WORKOUT route.

## Files Changed

| File | Change |
|---|---|
| `WorkoutViewModel.kt` | `startEditMode()`: synchronous pre-coroutine clear of `_lastFinishedWorkoutId`, `_lastPendingRoutineSync`, `pendingWorkoutSummary` |
| `WorkoutViewModel.kt` | `dismissWorkoutSummary()`: `isActive` guard so it never destroys a running workout |
| `PowerMeNavigation.kt` | `Routes.WORKOUT_SUMMARY` composable: `DisposableEffect` that calls `dismissWorkoutSummary()` on dispose when `isPostWorkout = true` |
| `WorkoutViewModelTest.kt` | 2 new regression tests: `startEditMode clears pendingWorkoutSummary synchronously` and `startEditMode clears lastFinishedWorkoutId synchronously` |

## Surfaces Fixed

- Finishing a workout, dismissing the post-workout summary, then editing a routine template no longer redirects to the old workout summary
- `pendingWorkoutSummary` and `lastFinishedWorkoutId` are now cleared on all paths that leave the post-workout summary screen

## How to QA

1. Start any routine-based workout, complete sets, tap **Finish Workout**
2. On the post-workout summary screen, tap **Done** (or swipe back)
3. In the Workouts tab, tap the **⋮ / Edit** button on any routine — the edit screen should open normally (not redirect to the finished workout's summary)
4. Make a change (e.g. add a rep), tap **Save**
5. Confirm: you are taken to the Workouts tab, not back to the previous workout's summary
6. Repeat steps 1–2, then tap **Start Workout** on a routine and confirm the new workout launches cleanly
