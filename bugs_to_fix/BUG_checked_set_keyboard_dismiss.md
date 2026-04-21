# BUG: Keyboard does not dismiss when a set is checked off

## Status
[x] Fixed

## Severity
P2 normal

## Description
When the user taps the checkmark (✓) button on a set row to mark it as completed, the soft keyboard remains open if a weight/reps field was focused. The keyboard should collapse immediately when the set is confirmed, freeing up screen space so the user can see the rest separator and timer that appear below.

## Steps to Reproduce
1. Start a workout.
2. Tap into the WEIGHT or REPS field of a set — keyboard appears.
3. Tap the ✓ (check) button in the CHECK column to complete the set.
4. Observe: keyboard stays visible.

Expected: keyboard dismisses on set completion.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §4.4`

## Fix Notes
In `WorkoutSetRow`, the CHECK box `.clickable(onClick = onCompleteSet)` was replaced with `.clickable { focusManager.clearFocus(); onCompleteSet() }`. The `focusManager` local val was already present in the composable scope. This dismisses the soft keyboard immediately when the set is confirmed.
