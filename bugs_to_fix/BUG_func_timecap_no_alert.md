# BUG: RFT time cap expiry triggers no haptics, sound, or BlockFinishSheet

## Status
[x] Fixed

## Severity
P1 high — functional block cap expiry is silently ignored; the defining end-condition for time-capped RFT never fires

## Description
When an RFT block has an optional time cap configured (e.g. "5 rounds / 25 min cap"), the cap reaching 00:00 should:
1. Auto-present `BlockFinishSheet` with elapsed = cap and rounds = number of `[ROUND ✓]` taps.
2. Fire the same `FINISH` alert (haptic + sound) as a manual `[FINISH WOD]` press.

Instead, the timer continues past the cap with no feedback — no haptics, no sound, and no finish sheet. The workout is stuck running indefinitely unless the user presses `[FINISH WOD]` manually.

Per spec §9.5: "At cap expiry the `BlockFinishSheet` is auto-presented … Cap auto-finish fires the same `FINISH` alert (sound + haptic) as manual finish."

Reported against RFT. AMRAP should also be verified: at 00:00 countdown the finish sheet must auto-appear and `FINISH` alert must fire.

## Steps to Reproduce
1. Create an RFT routine block with targetRounds = 5 and a time cap of e.g. 3 minutes (for easy testing).
2. Start a workout from that routine.
3. Tap `▶ START BLOCK`.
4. Wait for the 3-minute cap to elapse without pressing `[FINISH WOD]`.
5. Observe: timer counts past cap, no haptic, no sound, no BlockFinishSheet.

## Dependencies
- **Depends on:** BUG_func_start_block_noop (START BLOCK must work before cap behaviour is testable)
- **Blocks:** —
- **Touches:** `ui/workout/runner/RftOverlay.kt`, `ui/workout/FunctionalBlockRunner.kt`, `util/timer/TimerEngine.kt`, `util/WorkoutTimerService.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md §9.5` — optional time cap, FINISH alert
- Related spec: `future_devs/FUNC_ACTIVE_FUNCTIONAL_RUNNER_SPEC.md`

## Fix Notes
`FunctionalBlockRunner.start()` now accepts an `onFinish: suspend () -> Unit` callback (was defaulting to `{}`). `WorkoutViewModel.startFunctionalBlock()` passes a lambda that sets `blockAutoFinished = true` on `ActiveWorkoutState`. `ActiveWorkoutScreen` observes this flag via `LaunchedEffect` and sets `showBlockFinishSheet = true`, then calls `consumeBlockAutoFinished()`. The `FINISH` alert (haptic + sound) was already firing in `TimerEngine.runInternal()` — only the UI auto-present was missing.
