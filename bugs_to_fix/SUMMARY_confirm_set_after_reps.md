# SUMMARY: BUG_confirm_set_after_reps

## What Changed

- **`ActiveWorkoutScreen.kt`** — Added imports for `detectTapGestures` and `pointerInput`. Replaced
  `.clickable(onClick = onCompleteSet)` with `.pointerInput(onCompleteSet) { detectTapGestures { onCompleteSet() } }`
  on the checkmark `Box` in `WorkoutSetRow`. This makes the confirm tap register even when the soft
  keyboard is open, fixing the "first tap only dismisses keyboard" problem.

## How to QA

1. Start an active workout with any strength exercise.
2. Tap the reps field and type a rep count (e.g. "8"). Keep the keyboard visible.
3. Tap the ✓ checkmark button **once**.
4. The set row should immediately turn green (completed) on the first tap.
5. Confirm that tapping the checkmark again un-completes the set (toggle works).
6. Also verify that pressing the keyboard Done key still confirms the set (existing path unchanged).
