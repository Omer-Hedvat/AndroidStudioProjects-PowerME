# BUG: Deleted rest timers reappear after reopen

## Status
[x] Fixed

## Severity
P1 high
- Users delete custom rest timers but they come back every time they reopen the app or start a new workout, requiring repeated deletion.

## Description
In the Workouts tab (routine builder or exercise settings), when a user deletes a custom rest timer, the deletion is not persisted. On next app launch or workout open, the deleted timer reappears. This suggests the rest timer value is being reset from a template/default source rather than reading the persisted user change.

Likely root cause: rest timer deletion sets the value to null/default in the ViewModel but the write-back to Room (or Firestore sync) is not being called, or the seeder/template is overwriting the user's value on next load.

## Steps to Reproduce
1. Open Workouts tab → open a routine → open an exercise
2. Delete the rest timer (set to "Off" or remove it)
3. Save and close
4. Reopen the routine / restart the app
5. Observe: rest timer reappears with original value

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `WorkoutsScreen.kt`, `WorkoutDao.kt`, `FirestoreSyncManager.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `deleteRestSeparator()` in `WorkoutViewModel` only added the separator key to the in-memory `hiddenRestSeparators` set (which resets with every `ActiveWorkoutState`). It never wrote to Room, so on next load, `exercises.restDurationSeconds` was still non-zero and the separator re-appeared.

Fix (`WorkoutViewModel.kt`): when `deleteRestSeparator` is called for a passive separator (timer not currently running for that exercise/set), it now calls `exerciseDao.updateRestDuration(exerciseId, 0)` inside a `viewModelScope.launch`. It also mirrors the change into the in-memory `ActiveWorkoutState` so the UI is immediately consistent. If the timer IS currently active, `stopRestTimer()` is still called (prior behavior, no DB write needed since the timer expiry handles state).

Firestore safety: confirmed that Firestore sync only touches `RoutineExercise.restTime` (per-routine-exercise table), not `exercises.restDurationSeconds`. Persisting zero to the exercises table is safe from sync overwriting.

Tests added (4): passive delete persists to Room; passive delete updates in-memory `restDurationSeconds`; passive delete adds key to `hiddenRestSeparators`; active timer delete does NOT call `updateRestDuration`.
