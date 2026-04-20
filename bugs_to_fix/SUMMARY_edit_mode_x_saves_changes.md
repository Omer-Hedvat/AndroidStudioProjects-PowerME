# Fix Summary: Edit mode 'X' (discard) button saves changes instead of discarding

## Root Cause
`deleteRestSeparator()` in `WorkoutViewModel` called `exerciseDao.updateRestDuration(exerciseId, 0)` for every passive separator swipe, including during edit mode. This is a direct DB write that is permanent. `cancelEditMode()` (the 'X' handler) only resets in-memory state — it cannot undo an already-committed DB write. So any rest separator swiped during edit mode was persisted even when the user pressed 'X'.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added `!isEditMode` guard around the passive-separator DAO block in `deleteRestSeparator`. Added `WorkoutEditSnapshot` data class, `workoutEditSnapshot` field on `ActiveWorkoutState`, `enterLiveWorkoutEditMode()` entry point, `isLiveEdit()` helper, branched `cancelEditMode()` (snapshot-restore vs full reset), branched `saveRoutineEdits()` (live path: no routine_exercises writes, no `editModeSaved`), `isLiveEdit()` guards on all eager DB-write mutators and Iron Vault call sites. |
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Added pencil icon for live-workout edit entry; branched `handleEditClose` and discard dialog to skip `onWorkoutFinished()` in live edit; fixed elapsed-timer visibility during live-workout edit. |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added 3 tests for original fix + 14 tests for Phase B′ live-workout edit mode. |

## Surfaces Fixed
- Standalone edit mode: pressing 'X' fully reverts all changes including any rest separator swipes
- Live-workout edit mode (Phase B′): pressing 'X' restores the pre-edit snapshot and returns to the live workout without `onWorkoutFinished()` triggering; no routine template writes occur during live edit; staged changes flow through the Diff Engine only at `finishWorkout()`

## How to QA
**Standalone edit (regression):**
1. Open a routine → tap the pencil icon to enter edit mode
2. Swipe left on a rest separator → tap 'X' → confirm Discard
3. Re-enter edit mode: the rest separator is back

**Live-workout edit (Phase B′):**
1. Start a workout with a routine that has multiple exercises and sets
2. Tap the new pencil icon in the top bar → enter live-workout edit mode (elapsed timer keeps running)
3. Add a set, change a weight, delete a rest separator
4. Tap 'X' → confirm Discard → back to the live workout (not navigated away); verify all changes reverted
5. Re-enter edit mode, re-make changes, tap SAVE CHANGES → snackbar appears, top bar returns to live view; elapsed timer still running
6. Complete a set and finish the workout → Diff Engine fires the routine sync prompt
