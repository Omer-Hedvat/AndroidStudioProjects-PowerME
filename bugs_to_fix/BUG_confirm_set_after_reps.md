# BUG: Tapping ✓ after entering reps does not confirm the set

## Status
[x] Fixed

## Severity
P1 high
- Core workout interaction broken: user enters reps, taps the checkmark, but the set is not confirmed/completed.

## Description
**[Rework — original fix was wrong]**
The keyboard IME ✓ (soft keyboard's action button) must NOT confirm the set — it should only commit the typed value and dismiss the keyboard. Only the row-level ✓ button (the confirm icon to the right of the set row) should call `confirmSet()`.

The previous fix wired `detectTapGestures` on the row ✓ button to fire even during IME dismissal, which caused the keyboard ✓ to accidentally confirm the set. This is wrong behavior — tapping the keyboard checkmark should leave the set in editable state.

Likely root cause: the reps field retains focus after input, and the ✓ button tap is being consumed by the keyboard dismiss event rather than triggering `confirmSet()` in the ViewModel. Or the reps value hasn't been committed to state before the confirm handler reads it.

## Steps to Reproduce
1. Start an active workout
2. Tap the reps field on a set row → type a value
3. Tap the ✓ (checkmark) button
4. Observe: set is not confirmed — row stays in IDLE/editable state

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
**Rework (second attempt):**

Root cause of the regression: the `onDone` keyboard action on the reps `TextField` in `WorkoutSetRow`
was calling `onRepsDone()`, which at both call sites was wired to `onCompleteSet(set.setOrder)` →
`confirmSet()`. So tapping the soft keyboard's ✓ (IME Done) was confirming the set immediately,
without the user tapping the row ✓ button.

The `detectTapGestures` on the row ✓ button was a red herring — it worked correctly by itself. The
real bug was the `onRepsDone()` call in the keyboard action handler.

Fix:
1. Removed `onRepsDone()` call from the reps `TextField` `onDone` handler — it now only navigates
   focus (moves to next set's weight field, or clears focus). Keyboard ✓ never calls confirmSet.
2. Reverted row ✓ button to `.clickable(onClick = onCompleteSet)`. Since the keyboard ✓ dismisses
   the keyboard first, the row ✓ button is tapped with no keyboard present, so `.clickable` fires
   normally.
3. Removed `onRepsDone` parameter entirely from `WorkoutSetRow` and `SetWithRestRow` (dead code).
4. Removed `detectTapGestures` / `pointerInput` imports (no longer used).

Files changed: `ActiveWorkoutScreen.kt`.
