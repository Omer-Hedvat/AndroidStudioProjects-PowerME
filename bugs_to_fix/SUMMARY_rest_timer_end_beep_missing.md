# Fix Summary: Long beep at end of rest timer is missing

## Bug
`BUG_rest_timer_end_beep_missing` — P2 normal

## Root Cause
`notifyEnd()` was called only inside `onTimerFinish()`, which is dispatched via `withContext(Dispatchers.Main)` after the `WorkoutTimerService` loop completes. A race condition existed: if `timerJob.cancel()` was called while the coroutine awaited main-thread execution (e.g. user logs a set the instant the timer hits 0), the callback was cancelled and the beep never fired.

## Changes

### `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt`
- `restTimerNotifier` visibility changed from `private val` to `internal var` to allow mock injection in unit tests.
- `onTimerTick(remaining)`: added `remaining == 0` branch that calls `restTimerNotifier.notifyEnd(audioEnabled, hapticsEnabled, sound)`. Tick at 0 fires inside the loop — before any cancellation — making the beep immune to the race.
- `onTimerFinish()`: removed `notifyEnd()` call. Now cleanup-only: clears `restTimer` state and hides the separator. Added comment explaining the design.

### `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt`
Added 3 tests:
1. `onTimerTick at zero calls notifyEnd on restTimerNotifier` — verifies `notifyEnd(true, true, BEEP)` called via reflection
2. `onTimerTick at 1 2 3 plays warning beep but NOT notifyEnd` — verifies `playWarningBeep` × 3, `notifyEnd` × 0
3. `onTimerFinish clears restTimer state and hides separator without playing beep`

## Rework
QA confirmed the beep now fires but is too short. Duration increased 600ms → 1800ms (3×) in `RestTimerNotifier.notifyEnd()`. Only that call site changed — warning beep (150ms), countdown ticks, and `triggerAudioAlert` paths are all unchanged.

## Tests
BUILD SUCCESSFUL — all tests pass.
