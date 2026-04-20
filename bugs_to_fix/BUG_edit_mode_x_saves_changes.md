# BUG: Edit mode 'X' (discard) button saves changes instead of discarding

## Status
[x] Fixed

## Severity
P0 blocker
- Tapping 'X' to cancel/discard changes in active workout edit mode actually saves the changes. Users believe they are discarding but data is being mutated — data integrity issue.

## Description
In active workout edit mode, there are two exit buttons: a save/confirm action and an 'X' (discard/cancel) action. Tapping 'X' should revert all changes made during edit mode and restore the pre-edit state. Instead, 'X' is saving the changes, making it indistinguishable from the save action. This is the opposite of the intended behaviour and risks silent data corruption.

## Steps to Reproduce
1. Start a workout
2. Enter edit mode (pencil icon)
3. Make changes (e.g. modify a rest timer, change a value)
4. Tap 'X' to discard changes
5. Observe: changes are saved, not discarded

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `deleteRestSeparator()` in `WorkoutViewModel` called `exerciseDao.updateRestDuration(exerciseId, 0)` unconditionally for passive separators — including during edit mode. This DB write is permanent and is not reverted when the user presses 'X' (`cancelEditMode()`), which only resets in-memory state.

Fix: added an `!isEditMode` guard around the passive-separator DAO block. In edit mode, `deleteRestSeparator` now only adds the key to `hiddenRestSeparators` (session-scoped) and never touches the database. `cancelEditMode()` resets `hiddenRestSeparators` along with all other state, so 'X' truly discards everything.

Tests added (3): edit mode does not call `exerciseDao.updateRestDuration`; edit mode still adds the key to `hiddenRestSeparators`; `cancelEditMode` after a swipe clears `hiddenRestSeparators`.

**Phase B′ generalization (live-workout edit mode):** The same discard-safety invariant was extended to cover live-workout edit mode (`workoutId != null && isEditMode`). A new `WorkoutEditSnapshot` is captured on entry to edit mode via `enterLiveWorkoutEditMode()`, and the branched `cancelEditMode()` restores all state fields from the snapshot (including `hiddenRestSeparators`, `exercises`, `restTimeOverrides`) while preserving `isActive` and `workoutId`. Additionally, `isLiveEdit()` guards were added to all eager DB-write mutators (`updateExerciseStickyNote`, `updateExerciseRestTimer`, `updateExerciseRestTimers`, `updateSetupNotes`) and all Iron Vault writes (`debouncedSaveSet`, `debouncedSaveTimedSet`, `updateRpe`, `updateCardioSet`, `updateTimedSet`, `deleteSet`, `selectSetType`, `addSet`, `addExercise`) so that no routine template or workout_sets writes occur while in live-workout edit mode.

Tests added for Phase B′ (14): entry point, snapshot preservation, guards on DAO calls, save path correctness, cancel/restore, and regression fences for the standalone-edit path and invariant #15.
