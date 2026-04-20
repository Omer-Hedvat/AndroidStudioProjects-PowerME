# Fix Summary: Keyboard ✓ must not confirm set — only row ✓ button should

## Root Cause

The `onDone` keyboard action on the reps `TextField` in `WorkoutSetRow` called `onRepsDone()`, which
was wired at both call sites to `onCompleteSet(set.setOrder)` → `confirmSet()`. This meant tapping
the soft keyboard's IME ✓ confirmed the set immediately, bypassing the intended UX where only the
row ✓ button should confirm.

The prior fix introduced `detectTapGestures` on the row ✓ button (replacing `.clickable`), which was
a red herring — the `detectTapGestures` itself was fine, but `onRepsDone` was the actual problem.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Removed `onRepsDone()` call from reps `TextField` `onDone` handler; reverted row ✓ button from `detectTapGestures` back to `.clickable`; removed `onRepsDone` parameter from `WorkoutSetRow` and `SetWithRestRow`; removed unused `detectTapGestures`/`pointerInput` imports |

## Surfaces Fixed

- Active workout screen — weighted exercise set rows
- Tapping keyboard ✓ now only dismisses the keyboard (or moves focus to next set's weight field)
- Only the row ✓ button calls `confirmSet()`

## How to QA

1. Start a workout with at least one weighted exercise (bench press, squat, etc.)
2. Tap the reps field on any set → keyboard opens → type a value (e.g. "8")
3. **Tap the keyboard ✓ (IME Done button)** → keyboard should dismiss; the row stays editable (NOT confirmed, NOT green)
4. **Tap the row ✓ button** (checkmark on the far right of the set row) → set should turn green (confirmed)
5. Repeat for a second set to confirm consistent behavior
6. Verify focus navigation: keyboard ✓ should move focus to the next incomplete set's weight field when one exists
