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

**Rework (QA — beep too short):** `RestTimerNotifier.notifyEnd()` beep duration increased 600ms → 1800ms (3×). Only this call site changed — warning beep (150ms), countdown ticks (150ms/200ms), and `triggerAudioAlert` ROUND_START/FINISH (600ms) are all unchanged.

**Rework 2 (QA — bell/chime/click still short):** Investigation revealed that all sound types use `ToneGenerator` (no audio files). However, proprietary tone types `TONE_PROP_BEEP2` (BELL), `TONE_PROP_ACK` (CHIME), and `TONE_PROP_BEEP` (CLICK) have fixed OS-defined short segments (~100–200ms) and ignore the `durationMs` parameter — they play once and stop. Only `TONE_DTMF_S` (BEEP) is a continuous repeating tone that respects `durationMs`.

Fix: added `playBeepEnd(sound)` private method in `RestTimerNotifier`. For BEEP, behaviour is unchanged (single `startTone(TONE_DTMF_S, 1800)` call). For BELL/CHIME/CLICK, schedules 6 plays at 300ms intervals via `Handler.postDelayed`, delivering ~1800ms of periodic audio. `notifyEnd()` now calls `playBeepEnd()` instead of `playBeep(1800, sound)`. Public API is unchanged — existing WorkoutViewModelTest tests all pass.

**Rework 3 (QA — durations adjusted):** User QA found 6 bell repeats felt like too many. New target durations agreed:
- BEEP: reduce from 1800ms → 1000ms (single continuous tone)
- BELL / CHIME / CLICK: reduce from 6 repeats → 3 repeats at 300ms intervals (~900ms total)

Applied in `playBeepEnd()`: `startTone(TONE_DTMF_S, 1000)` + release after 1100ms; `repeat(3)` for proprietary tones. KDoc on `notifyEnd()` updated. No test changes required — tests assert call presence only, not duration values.

**Rework 4 (QA — BEEP still too long):** BEEP duration reduced 1000ms → 800ms. BELL/CHIME/CLICK unchanged (3 repeats × 300ms).

**Rework 5 (QA — BEEP still too long):** BEEP duration reduced 800ms → 700ms. BELL/CHIME/CLICK unchanged.
