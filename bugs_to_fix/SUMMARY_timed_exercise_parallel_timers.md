# SUMMARY: BUG_timed_exercise_parallel_timers

## What Changed

- **`ActiveWorkoutScreen.kt`** — Added `onStopRestTimer: () -> Unit = {}` parameter to `TimedSetRow`,
  `SetWithRestRow`, and `ExerciseCard`. The Play button click handler in `TimedSetRow` now calls
  `onStopRestTimer()` before transitioning `timerState` to SETUP or RUNNING. Both `ExerciseCard`
  call sites in the screen composable wire this to `{ viewModel.stopRestTimer() }`.

- **`WorkoutViewModelTest.kt`** — Added `stopRestTimer deactivates running rest timer immediately`
  test that verifies the ViewModel's rest timer state is cleared after calling `stopRestTimer()`.

## How to QA

1. Open a routine that has at least two sets and one timed exercise.
2. Complete set 1 — the purple rest timer bar should appear and start counting down.
3. While the rest timer is still running (e.g. at 0:45), tap Play on the timed exercise set.
4. The purple rest timer bar should **immediately disappear**.
5. Only the timed set countdown should be visible (setup countdown if configured, then running timer).
6. No stale rest timer bar or setup countdown should remain alongside the timed exercise timer.
