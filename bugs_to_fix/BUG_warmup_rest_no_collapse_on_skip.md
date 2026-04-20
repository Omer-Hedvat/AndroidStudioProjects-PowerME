# BUG: Warmup rest timer doesn't collapse when set is skipped

## Status
[x] Fixed

## Severity
P2 normal
- Rest timer row stays visible after skipping a warmup set, cluttering the workout screen.

## Description
When a warmup set is skipped (dismissed without completing), the rest timer row associated with that warmup set does not collapse/hide. Working sets correctly collapse the rest timer when skipped. The warmup skip path likely doesn't call the same collapse logic as the working set skip path.

## Steps to Reproduce
1. Start a workout with warmup sets that have a rest timer configured
2. Skip a warmup set without completing it (tap skip or dismiss)
3. Observe: rest timer row remains visible below the skipped warmup set

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
**First fix attempt (rework):** `deleteSet()` hoisted the `setToDelete` lookup and added `isPrecedingTimerForWarmup` to cancel the active timer when deleting a warmup while its predecessor's timer is running. This fixed the active-timer case but not the passive-separator case.

**Root cause of regression (rework → this fix):** `stopRestTimer()` only resets `restTimer = RestTimerState()` — it does NOT update `hiddenRestSeparators`. After the timer was cancelled, the UI's passive-separator condition `(isNotLastSet && effectiveRest > 0) && !isSeparatorHidden` still evaluated `true`, so the separator row persisted even with no active timer.

**Final fix:** Inside the `_workoutState.update {}` block in `deleteSet()`, added two additional state mutations:
1. **Issue B (rest separator):** When the deleted set is WARMUP and `setOrder > 1`, add `"${exerciseId}_${setOrder-1}"` to `hiddenRestSeparators`. This hides the preceding set's passive separator unconditionally — whether an active timer was running or not.
2. **Issue A (auto-collapse bonus fix):** Recompute `collapsedWarmupExerciseIds` after deletion: if no remaining warmups → remove from collapsed; if all remaining warmups are completed → add to collapsed; otherwise → remove from collapsed. This covers the case where deleting an incomplete warmup leaves all remaining warmups done.

Files changed: `WorkoutViewModel.kt` — `deleteSet()` function.
New unit tests in `WorkoutViewModelTest`:
- `deleteSet auto-collapses warmups when deleting incomplete warmup leaves all remaining completed`
- `deleteSet does not auto-collapse when some remaining warmup sets are still incomplete`
- `deleteSet hides preceding warmup separator when deleting a warmup set`
- `deleteSet does not hide preceding separator when deleting a NORMAL set`
