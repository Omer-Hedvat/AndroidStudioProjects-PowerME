# BUG: EMOM overlay has unnecessary "COMPLETED ✓" button

## Status
[x] Fixed

## Severity
P3 low

## Description
The EMOM overlay (`EmomOverlay.kt`) shows a large green "COMPLETED ✓" button at the top of the action area. In EMOM, rounds are driven by the timer — the minute fires and the next round starts automatically. There is no meaningful action for the user to "complete" a round mid-interval; the timer is the authority. The button is confusing and clutters the action bar.

TABATA already correctly omits this button — only EMOM needs the fix.

The EMOM overlay should only show: SKIP ROUND · FINISH BLOCK · Abandon.

## Steps to Reproduce
1. Create an EMOM block.
2. Start the workout and launch the block.
3. Observe: "COMPLETED ✓" button appears above "SKIP ROUND".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/EmomOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
Removed `onRoundCompleted` parameter and "COMPLETED ✓" `Button` from `EmomOverlay.kt`. Action area now shows only SKIP ROUND · FINISH BLOCK · Abandon. Updated `ActiveWorkoutScreen` callsite to remove the `onRoundCompleted` argument.
