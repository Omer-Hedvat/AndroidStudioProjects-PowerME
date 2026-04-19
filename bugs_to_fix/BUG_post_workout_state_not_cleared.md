# BUG: Post-workout state not cleared — Resume button loops to finished workout's summary

## Status
[x] Fixed

## Severity
P0 blocker
- Blocks core workout flow: user cannot start a new workout after finishing one in the same session.

## Description
After finishing a workout and viewing the summary, the WorkoutViewModel state is not fully cleared. As a result:

1. Navigating to the Workouts tab shows a **"Resume Workout"** button as if a workout is still in progress
2. Tapping **"Resume Workout"** navigates to the **summary screen of the already-finished workout** instead of an active workout
3. Tapping **Start Workout** on any routine also navigates to the finished workout's summary instead of starting a new one
4. The user is stuck — cannot start a new workout or escape the loop without force-closing the app

Likely root cause: `finishWorkout()` in `WorkoutViewModel` does not fully reset the active workout state (e.g. `activeWorkoutId`, `ActiveWorkoutState`, or the in-memory workout object). The navigation guard that shows "Resume Workout" reads stale state and incorrectly concludes a workout is still active.

## Steps to Reproduce
1. Start a routine-based workout
2. Complete some sets
3. Tap **Finish Workout** → view the post-workout summary
4. Navigate back to the **Workouts tab**
5. Observe: "Resume Workout" button is visible
6. Tap "Resume Workout" → taken to the finished workout's summary (not an active workout)
7. Navigate back, tap **Start Workout** on any routine → same summary appears again

## Dependencies
- **Depends on:** —
- **Blocks:** BUG_post_workout_triple_sync_prompt (shares WorkoutViewModel finish flow)
- **Touches:** `WorkoutViewModel.kt`, `WorkoutsScreen.kt`, `PowerMeNavigation.kt`, `ActiveWorkoutScreen.kt`, `WorkoutViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
**Root cause:** All three `startWorkout*` functions used `_workoutState.update { it.copy(...) }` which preserved `pendingWorkoutSummary` from the previous finished workout. When `ActiveWorkoutScreen` rendered for the new workout, `LaunchedEffect(pendingWorkoutSummary)` fired immediately (stale non-null value) and called `onWorkoutFinished()`, navigating to the old workout's summary. Additionally, `_lastFinishedWorkoutId` was never cleared on new workout start, so the redirect pointed to the old workout.

**Fix:**
1. In `startWorkoutFromRoutine()`, `startWorkout()`, and `startWorkoutFromPlan()`: added a synchronous `_workoutState.update { ActiveWorkoutState(availableExercises = it.availableExercises) }` before the `viewModelScope.launch` — fully resets all stale state (pendingWorkoutSummary, hiddenRestSeparators, restTimeOverrides, etc.) before launching the new workout coroutine.
2. Added `_lastFinishedWorkoutId = null; _lastPendingRoutineSync = null` before the coroutine in all three start functions.
3. Added the same var clears in `dismissWorkoutSummary()` for consistency with `cancelWorkout()`.
