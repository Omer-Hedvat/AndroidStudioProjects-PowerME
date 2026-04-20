# BUG: "Update Rest Timers" re-adds previously deleted rest timers

## Status
[x] Fixed

## Severity
P1 high
- User explicitly deletes a rest timer, taps "Update Rest Timers", and the deleted timers come back.

## Description
In the active workout "Set Rest Timers" dialog, users can delete individual rest timers. When they tap "Update Rest Timers" to confirm, the removed timers are re-applied because the update logic applies the full default timer set rather than a diff against the current per-exercise timer state. Any exercise whose rest timer was removed now gets it back, defeating the deletion.

Likely root cause: the "Update Rest Timers" action iterates all exercises and unconditionally sets a rest timer duration, ignoring whether the user had previously deleted that exercise's timer (i.e., it does not check for `restTime == 0` or a null/absent timer sentinel).

## Steps to Reproduce
1. Start any workout (routine-based or quick start)
2. Open "Set Rest Timers" dialog (kebab menu or similar)
3. Delete the rest timer for one or more exercises (set to 0 / remove)
4. Tap "Update Rest Timers" to confirm
5. Observe: the deleted timers are re-added for the affected exercises

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Clarification: "Update Rest Timers" IS the intentional restore mechanism — swiping to delete separators and then confirming "Update Rest Timers" is how users restore deleted rest timers. The original bug description was misleading.

Real fix (two issues):
1. **Active workout mode** — no code change needed; `updateExerciseRestTimers` correctly clears `hiddenRestSeparators` and restores in-memory `restDurationSeconds`, so separators come back as intended.
2. **Edit mode** — `startEditMode` (template builder) was not resetting `hiddenRestSeparators`, meaning leftover hidden entries from a prior session could carry into a fresh template edit. Fixed by adding `hiddenRestSeparators = emptySet()` to the `startEditMode` state update.

Tests added to document the correct restore behavior in both modes.
