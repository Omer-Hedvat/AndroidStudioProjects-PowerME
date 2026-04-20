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

## Rework 1
QA confirmed the beep now fires but is too short. Duration increased 600ms → 1800ms (3×) in `RestTimerNotifier.notifyEnd()`. Only that call site changed — warning beep (150ms), countdown ticks, and `triggerAudioAlert` paths are all unchanged.

## Rework 2 — Bell/Chime/Click still short

### Root Cause (Rework 2)
All sound types use `ToneGenerator` — there are no audio files. However, proprietary tone types `TONE_PROP_BEEP2` (BELL), `TONE_PROP_ACK` (CHIME), and `TONE_PROP_BEEP` (CLICK) have fixed OS-defined short segments (~100–200ms) that play once and stop, regardless of the `durationMs` argument passed to `startTone()`. Only `TONE_DTMF_S` (BEEP) is a continuous repeating tone that honours `durationMs`.

### Fix (Rework 2)

| File | Change |
|---|---|
| `RestTimerNotifier.kt` | Added private `playBeepEnd(sound)` method. For BEEP: single `startTone(TONE_DTMF_S, 1800)` call (unchanged). For BELL/CHIME/CLICK: 6 plays scheduled via `Handler.postDelayed` at 300ms intervals → ~1800ms total. `notifyEnd()` now calls `playBeepEnd()` instead of `playBeep(1800, sound)`. Public API unchanged. |

### Surfaces Fixed
- Rest timer end-of-rest audio now lasts ~1800ms for ALL sound types (BEEP, BELL, CHIME, CLICK)
- Warning beeps, countdown ticks, and `triggerAudioAlert` paths are all unchanged

### How to QA
1. Open Settings → Timer Sound and select **Bell**
2. Start a workout, complete a set, let the rest timer count down to 0
3. Hear: 6 bell tones spaced ~300ms apart over ~1.8 seconds (not a single short ding)
4. Repeat with **Chime** and **Click** — same ~1.8s periodic pattern
5. Switch back to **Beep** — hear the continuous 1.8s tone as before

## Rework 3 — Durations shortened

### Fix (Rework 3)

| File | Change |
|---|---|
| `RestTimerNotifier.kt` | `playBeepEnd`: BEEP `startTone` duration 1800→1000ms, release delay 1900→1100ms. BELL/CHIME/CLICK `repeat(6)` → `repeat(3)`. KDoc on `notifyEnd()` updated to reflect new durations. |

### How to QA (Rework 3)
1. Open Settings → Timer Sound and select **Bell**
2. Start a workout, complete a set, let the rest timer count down to 0
3. Hear: 3 bell tones spaced ~300ms apart (~900ms total — noticeably shorter than before)
4. Repeat with **Chime** and **Click** — same 3-tone pattern
5. Switch to **Beep** — hear a 1-second continuous tone

## Tests
BUILD SUCCESSFUL — all tests pass (clean build, no regressions).
