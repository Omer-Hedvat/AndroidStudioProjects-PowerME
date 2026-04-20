# BUG: Tapping ✓ after entering reps does not confirm the set

## Status
[x] Fixed

## Severity
P1 high
- Core workout interaction broken: user enters reps, taps the checkmark, but the set is not confirmed/completed.

## Description
In the active workout screen, after typing a value in the reps field and tapping the ✓ (confirm) button, the set is not marked as completed. The checkmark tap appears to be ignored or the focus/state is in a bad state after reps input. Working sets are the primary affected case.

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
Root cause: Compose's `clickable` modifier fires on the touch UP event. When the soft keyboard is
showing and the user taps the checkmark, the IME window manager intercepts the DOWN event to dismiss
the keyboard, causing a recomposition between DOWN and UP. The gesture tracker loses the sequence
and `onClick` never fires — only the second tap (after keyboard is gone) would confirm the set.

Fix: Replaced `.clickable(onClick = onCompleteSet)` with
`.pointerInput(onCompleteSet) { detectTapGestures { onCompleteSet() } }` on the checkmark `Box`
in `WorkoutSetRow`. `detectTapGestures` uses `requireUnconsumed = false` on the down event,
capturing the touch even after the IME dismissal has consumed it.

Files changed: `ActiveWorkoutScreen.kt` (import added + modifier changed at line ~1722).
