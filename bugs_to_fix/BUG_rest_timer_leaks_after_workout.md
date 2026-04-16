# BUG: Rest timer keeps running after workout ends / previous timer not cancelled on new start

## Status
[x] Fixed

## Description
Two related issues with rest timer lifecycle:

1. **Leak on workout finish/cancel:** If a rest timer is actively counting down when the user finishes or cancels a workout, the timer is not cancelled. The user hears the beep/vibration fire seconds or minutes later even though they are no longer in an active workout session.

2. **Previous timer not skipped on new start:** If a rest timer is already running and a new rest timer is started (e.g. user completes another set), the previous timer should be silently skipped/cancelled. Currently both timers may run in parallel, causing double beeps or confusing countdowns.

Expected behavior:
- `finishWorkout()` and `cancelWorkout()` must cancel any in-flight rest timer job immediately.
- Starting a new rest timer must cancel the currently running rest timer before launching the new one.

## Steps to Reproduce

### Issue 1 — timer leaks after workout ends
1. Start an active workout.
2. Complete a set that triggers a rest timer (e.g. 60-second rest).
3. While the rest timer is counting down, tap **Finish Workout** (or **Cancel**).
4. Dismiss the post-workout summary.
5. Wait — the rest timer beep fires after the original countdown would have elapsed.

### Issue 2 — previous timer not cancelled on new set
1. Start an active workout.
2. Complete a set that triggers a rest timer (e.g. 90-second rest).
3. While the rest timer is still running, complete another set in a different exercise.
4. A second rest timer starts. The first timer is NOT cancelled and both run simultaneously.

## Assets
- Related spec: `WORKOUT_SPEC.md §4` (rest timer state machine)

## Fix Notes
`stopRestTimer()` already correctly cancelled both timer paths (coroutine `timerJob` + service `timerService.stopTimer()`), but was never called from `finishWorkout()` or `cancelWorkout()`. Added `stopRestTimer()` as the first statement in both.

`startRestTimer()` and `startRestTimerWithDuration()` each cancelled `timerJob` but not the service timer. Added `if (serviceBound && timerService != null) timerService!!.stopTimer()` alongside `timerJob?.cancel()` in both to close the service→coroutine path edge case.

Two unit tests added: `finishWorkout cancels active rest timer` and `cancelWorkout cancels active rest timer`.
