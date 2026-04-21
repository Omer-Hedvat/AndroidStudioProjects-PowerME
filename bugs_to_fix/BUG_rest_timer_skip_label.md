# BUG: Rest timer TimerControlsSheet skip button shows checkmark icon instead of "Next" label

## Status
[x] Fixed

## Severity
P3 low

## Description
The Skip button in `TimerControlsSheet` (opened by tapping the active rest separator) is rendered as a checkmark icon (`✓`). This is ambiguous — it looks like "confirm" rather than "skip rest and proceed to next set". The label should read **"Next"** as plain text to make the action self-evident.

## Steps to Reproduce
1. Complete a set — rest timer starts.
2. Tap the active (primary-tinted) rest separator to open `TimerControlsSheet`.
3. Observe: the skip action is shown as a `✓` icon.

Expected: the skip action is labelled "Next".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §4.6.1`

## Fix Notes
In `TimerControlsSheet`, the `TextButton` content `Text("Skip")` was changed to `Text("Next")` to match the intended label — the action skips the remaining rest and proceeds to the next set.
