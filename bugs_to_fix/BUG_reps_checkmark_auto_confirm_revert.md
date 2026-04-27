# BUG: Revert — tapping ✓ in the reps column box should auto-confirm the set

## Status
[x] Fixed

## Severity
P1 high
- Core workout interaction: confirming a set requires two taps (keyboard ✓ + row ✓). The intended UX is that tapping ✓ / Done on the reps field keyboard auto-confirms the set in one gesture.

## Description
A prior decision (BUG_confirm_set_after_reps) explicitly wired the keyboard IME ✓ to only dismiss the keyboard and NOT confirm the set. The user now wants to revert this: pressing ✓ / Done on the keyboard after entering reps should immediately call `confirmSet()`, marking the set as done without a second tap on the row ✓ button.

The row ✓ button should remain functional as an alternative confirm path.

## Steps to Reproduce
1. Start an active workout.
2. Tap the REPS field on a set row → type a value.
3. Tap the ✓ (Done) action on the soft keyboard.
4. Observe (current broken): set stays in editable state — user must also tap the row ✓ button.
5. Expected: set is confirmed immediately upon keyboard ✓.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §4.4`

## Fix Notes
<!-- populated after fix is applied -->
