# BUG: Timed exercise — rest timer and setup timer run in parallel when set fires

## Status
[x] Fixed

## Severity
P1 high — visible UX confusion, two timers competing for attention during an active set

## Description
In a timed exercise, when the user taps the Play button to fire a set, the rest timer from
the previous completed set and the per-set setup countdown are not dismissed. All three timers
(rest timer bar, setup timer display, running exercise timer) are visible simultaneously, which
is confusing and incorrect.

Expected: firing a timed set should cancel the active rest timer and hide the setup countdown
(or any other pre-set timer) so only the running set timer is shown.

## Steps to Reproduce
1. Create or open a routine that contains at least one timed exercise.
2. Start the workout.
3. Complete set 1 — the rest timer starts counting down.
4. While the rest timer is still running, tap Play on set 2 to start the timed exercise.
5. Observe: the rest timer ("0:59" purple bar) and the set-level setup timer ("0:01" green PREV
   column) both remain visible while the timed exercise is counting.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

## Assets
- Screenshot: provided by user (shows set 2 completed with rest timer 0:59, set 3 running at
  0:01 — rest timer and setup timer both visible while exercise timer is active)
- Related spec: `WORKOUT_SPEC.md` (timed exercise state machine, rest timer lifecycle)

## Fix Notes
Root cause: The Play button in `TimedSetRow` only changed local Compose state (`timerState`). It
never called `viewModel.stopRestTimer()`. The ViewModel's rest timer state remained active, keeping
`isThisTimerActive = true` for the previous set's separator — so the purple rest pill stayed
visible alongside the timed set countdown.

Fix: Added `onStopRestTimer: () -> Unit = {}` parameter to `TimedSetRow`. The Play button click
handler now calls `onStopRestTimer()` before transitioning state to SETUP or RUNNING. The parameter
is threaded through `SetWithRestRow` and `ExerciseCard` up to the two `ExerciseCard` call sites in
the main screen, where it is wired to `{ viewModel.stopRestTimer() }`.

Files changed: `ActiveWorkoutScreen.kt` — `TimedSetRow`, `SetWithRestRow`, `ExerciseCard`
signatures + Play button handler + both call sites in the screen composable.
New unit test: `stopRestTimer deactivates running rest timer immediately` in `WorkoutViewModelTest`.
