# BUG: Long beep at end of rest timer is missing

## Status
[x] Fixed

## Severity
P2 normal
- User has no audio cue that rest is over unless they're watching the screen.

## Description
When the rest timer reaches zero, the expected long beep (end-of-rest signal) does not play. The warning double-beep mid-timer works correctly, but the final completion beep is absent. This leaves the user with no audio notification that rest time is over.

Likely root cause: the `RestTimerNotifier` or equivalent has a `playWarningBeep()` method that fires at the warn threshold, but the `playEndBeep()` (or equivalent long tone) is either not implemented, not called from the timer completion handler, or being called but with zero duration/volume.

## Steps to Reproduce
1. Complete a set → rest timer starts
2. Wait for rest timer to reach 0
3. Observe: no long beep plays at timer end (only silence or the mid-timer warning beep earlier)

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `RestTimerNotifier.kt`, `WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`, `CLOCKS_SPEC.md`

## Fix Notes
Root cause: `notifyEnd()` was only called inside `onTimerFinish()`, which is dispatched via `withContext(Dispatchers.Main)` AFTER the timer loop completes. If `timerJob.cancel()` races with this dispatch (e.g. user logs a new set at the exact moment the timer reaches 0), the callback is cancelled and the beep never fires.

Fix (`WorkoutViewModel.kt`): moved `restTimerNotifier.notifyEnd(...)` into `onTimerTick(remaining)` at the `remaining == 0` case. The tick at 0 fires INSIDE the loop — before `timerJob` can be cancelled — making the beep cancellation-immune. `onTimerFinish()` is now cleanup-only (clears `restTimer` state, hides separator).

`RestTimerNotifier.notifyEnd()` already existed and correctly calls `playBeep(600, sound)` for audio and `triggerAudioAlert(AlertType.FINISH)` for haptics — no changes needed to that file.

`restTimerNotifier` visibility changed from `private val` to `internal var` to allow mock injection in tests.

Tests added (3): `onTimerTick(0)` calls `notifyEnd` exactly once; `onTimerTick(1/2/3)` plays warning beep and never calls `notifyEnd`; `onTimerFinish` clears state without calling any beep method.
