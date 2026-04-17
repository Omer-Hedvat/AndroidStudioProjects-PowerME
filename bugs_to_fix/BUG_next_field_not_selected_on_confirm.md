# BUG: Next input field not auto-selected when confirming with checkmark

## Status
[ ] Open

## Description
In the active workout screen, after entering a value in the weight field and tapping the keyboard checkmark (done/next action) to move to the reps field, the reps field receives focus but its existing text is NOT selected/highlighted. The user expects all text to be pre-selected so they can immediately type a new value to override it, matching the tap-to-select behavior that already exists for `WorkoutInputField` (which selects all text on tap via `PressInteraction.Release`). The issue is that the select-all logic only triggers on tap, not on programmatic focus changes (e.g., IME action navigation).

Affected screen: `ActiveWorkoutScreen.kt` — `WorkoutInputField` composable.

## Steps to Reproduce
1. Start an active workout with at least one exercise
2. Tap the weight field on a set row — text is selected (correct)
3. Type a weight value (e.g., "80")
4. Tap the keyboard checkmark / next button to move focus to the reps field
5. Observe: reps field gains focus but existing text is NOT selected — user must manually select/delete before typing

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
