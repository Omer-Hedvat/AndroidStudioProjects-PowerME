# BUG: Timed exercise PREV data only shown for first set, not subsequent sets

## Status
[x] Fixed

## Severity
P1 high
- PREV column is a core training tool — missing it for sets 2+ makes progressive overload tracking impossible for multi-set timed exercises.

## Description
In the active workout screen, timed exercise rows only show PREV session data in the first set row. Sets 2, 3, etc. show "--" in the PREV column even when prior session data exists for those sets.

Standard exercises correctly show PREV for all sets. The timed exercise PREV lookup likely matches by set index but only retrieves index 0, or the data structure stores only one prev value per exercise rather than one per set.

## Steps to Reproduce
1. Have a routine with a timed exercise containing 3+ sets that has prior session data
2. Start a new workout using that routine
3. Look at the timed exercise set rows
4. Observe: set 1 shows PREV data, sets 2+ show "--"

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `addExercise()` in `WorkoutViewModel.kt` never set `ghostTimeSeconds` when building `ActiveSet` from previous session data — only `ghostWeight`, `ghostReps`, and `ghostRpe` were populated. All timed exercise set rows therefore showed "--" for PREV time.

Fix: extracted `prevTimeStr` from `prevSet.timeSeconds` (guarded `> 0`) and assigned it to both `ghostTimeSeconds` (PREV column hint) and `timeSeconds` (active input pre-fill) in `addExercise()`. The `startWorkoutFromRoutine()` path already correctly mapped ghost data by set index; only `timeSeconds` pre-fill was added there.
