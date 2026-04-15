# Fix Summary: Discard Changes dialog shown even when nothing changed in edit mode

## Root Cause
`showDiscardEditDialog = true` was unconditionally triggered by both the BackHandler and the Close IconButton in edit mode, with no check of whether the user had actually modified anything.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added `editModeSnapshot: List<ExerciseWithSets>` to `ActiveWorkoutState` (populated in `startEditMode()`); added `editModeHasChanges()` helper that compares current exercises against the snapshot |
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Extracted `handleEditClose` lambda; BackHandler and Close IconButton now call it — shows dialog only when `editModeHasChanges()` returns true, otherwise calls `cancelEditMode()` + navigates immediately |

## Surfaces Fixed
- Active workout edit mode: pressing Back or tapping X with zero changes now exits immediately without showing the "Discard Changes?" dialog

## How to QA
1. Open any routine from the Workouts tab
2. Tap the Edit (pencil) button to enter edit mode
3. Do NOT change anything
4. Tap the X button (top-right) — **expected: exits edit mode immediately, no dialog**
5. Repeat step 2–3, then press the device Back button — **expected: same, no dialog**
6. Now enter edit mode, change a set's weight or reps, then press X — **expected: "Discard Changes?" dialog appears as before**
