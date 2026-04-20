# BUG: Deleting one rest timer removes all rest timers for that exercise

## Status
[x] Fixed

## Severity
P1 high
- Deleting a single set's rest timer in edit mode wipes all rest timers for every set of that exercise type (e.g. deleting one working set rest timer removes all working set rest timers for exercise X).

## Description
In active workout edit mode, when the user deletes the rest timer for a single set, all rest timers for sets of the same type on that exercise are also deleted. The deletion logic likely keys on exerciseId + setType instead of the specific setOrder/setId, causing a bulk delete instead of a targeted one.

## Steps to Reproduce
1. Start a workout with an exercise that has multiple working sets, each with a rest timer configured
2. Enter edit mode
3. Delete the rest timer on one working set (e.g. Set 2)
4. Observe: rest timers on ALL other working sets for that exercise are also gone

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: the passive-separator branch of `deleteRestSeparator()` in `WorkoutViewModel` called `exerciseDao.updateRestDuration(exerciseId, 0)` AND then mirrored `restDurationSeconds = 0` into the in-memory exercise. Since `restDurationSeconds` is shared by all working sets, setting it to 0 in memory made every working-set separator for that exercise use `effectiveRest = 0`, hiding them all in the current session.

Fix: removed the in-memory `exercise.copy(restDurationSeconds = 0)` mirror entirely. The per-set hide for the current session is handled correctly by `hiddenRestSeparators`, which is already keyed by `"${exerciseId}_${setOrder}"`. Sibling sets now retain their `effectiveRest` from the exercise default (still 90 s in memory) so their separators remain visible. The DAO write (`updateRestDuration`) is kept for non-edit-mode to maintain persistence across sessions; the edit-mode guard (Bug A fix) prevents it from running in edit mode.

Tests updated (1): `deleteRestSeparator on passive separator does NOT mirror restDurationSeconds zero into in-memory exercise` — verifies that sibling sets' rest duration stays 90 in memory while only the swiped set's key appears in `hiddenRestSeparators`.

**Phase B′ regression fence:** Test `deleteRestSeparator in live edit scopes hide to single set, preserves sibling restDurationSeconds` confirms the fix holds in live-workout edit mode — the DAO is not called (guarded by `isLiveEdit()`), only the targeted `"${exerciseId}_${setOrder}"` key appears in `hiddenRestSeparators`, and sibling sets retain their `restDurationSeconds = 90` in memory.
