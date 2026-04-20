# Fix Summary: "Update Rest Timers" re-adds previously deleted rest timers

## Root Cause

The original bug description was misleading. **"Update Rest Timers" confirming a non-zero value is the intentional restore mechanism** — swiping to delete a separator and then opening "Update Rest Timers" and confirming is how users restore a deleted rest timer. This behavior is correct and should not be changed.

The actual issue was two-fold:

1. **Active workout mode**: no code issue — `updateExerciseRestTimers` correctly clears `hiddenRestSeparators` and updates in-memory `restDurationSeconds`, so swiped separators come back after confirm. ✓

2. **Edit mode (template builder)**: `startEditMode` was not resetting `hiddenRestSeparators`. Any leftover hidden-separator entries from a prior session (or prior workout) would carry into a fresh template edit session, causing separators to appear hidden with no user action. Fixed by adding `hiddenRestSeparators = emptySet()` to `startEditMode`.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | `startEditMode` now resets `hiddenRestSeparators = emptySet()` for a clean slate on each template edit session |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | 4 new tests documenting the correct restore behavior and the `startEditMode` reset |

## Surfaces Fixed

- Template builder edit mode: entering edit mode for a routine no longer carries over hidden rest separator state from prior sessions
- Active workout + edit mode: swipe-delete → "Update Rest Timers" → confirm correctly restores all deleted separators in both modes

## How to QA

**Active workout restore:**
1. Start a workout with at least one exercise that has a rest timer (e.g. 90s)
2. After each set, swipe left on BOTH rest separators between sets 1-2 and 2-3 to delete them — they disappear
3. Open the exercise's kebab menu (⋮) → **Set Rest Timers**
4. Confirm without changing anything
5. **Expected:** both rest separators reappear with the original duration (90s)

**Edit mode (template builder):**
1. Open a routine for editing (Workouts tab → kebab on a routine → Edit)
2. Swipe to delete a rest separator — it hides
3. Open "Set Rest Timers" and confirm
4. **Expected:** separator comes back
5. Exit edit mode, re-open the same routine for editing
6. **Expected:** separators visible (no phantom-hidden separators from the prior session)
