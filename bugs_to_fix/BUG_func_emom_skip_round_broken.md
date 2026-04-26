# BUG: EMOM "Skip Round" button does not advance to the next round

## Status
[x] Fixed

## Severity
P1 high
- Skip Round is a core action in EMOM; if it does nothing, the user cannot advance rounds manually and the overlay is stuck

## Description
In the EMOM overlay, tapping **SKIP ROUND** does not advance to the next round. The timer keeps running on the current round and the "Round X of N" counter does not increment. The expected behaviour is:

1. Tapping SKIP ROUND immediately ends the current round.
2. The round counter increments: "Round 2 of 10" → "Round 3 of 10".
3. The progress ring resets to full and starts counting down from the interval duration again.
4. The skip is logged in the tap log (same as a completed round but marked as skipped).

If the user is already in round N (the last round), SKIP ROUND should trigger the same flow as FINISH BLOCK (present BlockFinishSheet).

## Steps to Reproduce
1. Create an EMOM block (e.g. 10 rounds, 60s interval, Kettlebell Swing).
2. Start the block.
3. During round 2, tap **SKIP ROUND**.
4. Observe: nothing changes — the timer continues, the round counter stays on "Round 2 of 10".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/EmomOverlay.kt`, `ui/workout/FunctionalBlockRunner.kt`, `util/timer/TimerEngine.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
