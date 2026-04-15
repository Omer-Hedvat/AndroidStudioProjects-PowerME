# BUG: Discard Changes dialog shown even when nothing was changed in edit mode

## Status
[x] Fixed

## Description
In routine edit mode (`ActiveWorkoutScreen`), pressing the system back button or tapping the X (Close) button always shows the "Discard Changes?" confirmation dialog, even if the user opened edit mode and made zero changes. The dialog is unnecessary friction when the state is clean.

## Steps to Reproduce
1. Open any routine from the Workouts tab
2. Tap the Edit button (pencil icon) to enter edit mode
3. Do NOT change anything
4. Tap the X button (top-right) or press the device Back button
5. **Expected:** Exit edit mode immediately with no dialog
6. **Actual:** "Discard Changes?" dialog appears

## Fix Notes
Added `editModeSnapshot: List<ExerciseWithSets>` to `ActiveWorkoutState`, populated when `startEditMode()` loads exercises. Both the `BackHandler` and the Close `IconButton` now check `exercises != editModeSnapshot` before deciding whether to show the dialog. If nothing changed, they call `cancelEditMode()` + navigate directly. If something changed, they show the dialog as before.
