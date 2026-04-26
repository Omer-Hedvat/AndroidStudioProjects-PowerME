# BUG: Functional overlays missing mid-round alert (haptic/sound)

## Status
[x] Fixed

## Severity
P2 normal

## Description
All four functional block overlays (AMRAP, RFT, EMOM, TABATA) are missing the mid-round "about to expire" alert that regular rest timers provide (haptic + sound when X seconds remain). `TimerSpec.Emom` has a `warnAtSeconds` field and `TimerSpec.Tabata` has `workWarnAtSeconds`, so the engine supports the threshold — but the overlays never wire up the alert callback/side-effect that fires haptics or a beep when the warning fires.

AMRAP does not have a per-interval countdown at all (count-up timer), so the alert there would fire at the configured danger threshold (≤10s remaining in time cap). RFT has a time-cap countdown and should also alert.

## Steps to Reproduce
1. Create an EMOM block (e.g. 60s interval).
2. Start the workout, launch the block.
3. Watch the timer count down in the last 10 seconds.
4. Observe: no haptic buzz or sound fires.
5. Compare with the rest timer in a regular strength workout — it buzzes/beeps before expiry.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/FunctionalBlockRunner.kt`, `util/timer/TimerEngine.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
Two-part fix: (1) `TimerEngineImpl.runAmrap()` now fires `AlertType.WARNING` once when `remaining == 10`; `runRft()` fires `AlertType.WARNING` once when `cap - elapsed == 10`. (2) `FunctionalBlockRunner.mapToTimerSpec()` now provides a default `warnAtSeconds` for EMOM (`resolveWarnAt(intervalSeconds)` = `min(interval/2, 10)`) and `workWarnAtSeconds` for Tabata when `warnAtSecondsOverride` is null — previously both were `null`, silently skipping the `WARNING` branch in `TimerEngine`.
