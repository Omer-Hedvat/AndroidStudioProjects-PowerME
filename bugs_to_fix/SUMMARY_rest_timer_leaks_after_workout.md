# Fix Summary: Rest timer keeps running after workout ends

## Root Cause

`stopRestTimer()` (WorkoutViewModel.kt) correctly cancels both timer paths — it calls `timerJob?.cancel()` and `timerService!!.stopTimer()`. However, neither `finishWorkout()` nor `cancelWorkout()` called it; they only cancelled `elapsedTimerJob`. This left any in-flight rest timer (coroutine or service) orphaned, allowing phantom beeps/haptics to fire after the workout ended.

Secondary: `startRestTimer()` and `startRestTimerWithDuration()` each guarded against the previous coroutine via `timerJob?.cancel()` but did not call `timerService!!.stopTimer()`. In the edge case where the service timer was running and the service became unbound before the next `startRestTimer()` call, both timers could run in parallel.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added `stopRestTimer()` as first line of `finishWorkout()` and `cancelWorkout()`; added `timerService!!.stopTimer()` guard to `startRestTimer()` and `startRestTimerWithDuration()` |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added 2 new tests: `finishWorkout cancels active rest timer` and `cancelWorkout cancels active rest timer` |

## Surfaces Fixed

- Finishing a workout while a rest timer is counting down — timer is now silently cancelled, no phantom beep
- Cancelling a workout while a rest timer is counting down — same fix
- Starting a new rest timer while a service-based timer is still running (edge case) — previous service timer is stopped first

## How to QA

1. Start a routine-based workout.
2. Complete a set that has a rest timer (e.g. any normal set with a 60s or 90s rest configured).
3. While the countdown is audibly ticking, tap **Finish Workout**.
4. Dismiss the summary / navigate away.
5. Wait the full rest duration — confirm **no beep or vibration fires**.

Repeat steps 1–5 using **Cancel Workout** instead of Finish.
