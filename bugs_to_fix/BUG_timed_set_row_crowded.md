# BUG: Timed set row layout is crowded in IDLE state

## Status
[ ] Open

## Description
In the active workout screen, timed exercises (e.g. Bird-Dog, Plank) show a crowded row when a set is not yet started (IDLE state). The row squeezes six visual elements into one line: set number | weight input | time input | RPE input | [▶ Play] | [✓ Check]. The play button (purple/violet) and the manual-complete checkmark end up side by side at the far right with very little space, making the row look inconsistent and cramped compared to completed timed sets (which show the clean green checkmark only).

Root source: `TimedSetRow` IDLE branch in `ActiveWorkoutScreen.kt` uses weights `0.10 + 0.22 + 0.25 + 0.15 + 0.18 + 0.10 = 1.0f` — six columns where the last two are both action buttons with no visual separation or hierarchy.

## Steps to Reproduce
1. Create or open a routine that contains a timed exercise (any TIMED type, e.g. Bird-Dog, Plank).
2. Start an active workout session with that routine.
3. Observe the uncompleted (IDLE) timed set rows — two small buttons appear side by side at the right edge.

## Assets
- Screenshot: `bugs_to_fix/assets/timed_set_row_crowded/Screenshot_20260415_153532_PowerME.jpg`
- Related spec: `WORKOUT_SPEC.md §4.8` (TimedSetRow state machine)

## Fix Notes
<!-- populated after fix is applied -->
