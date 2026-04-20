# SUMMARY: BUG_prev_results_mixed_set_types

## What changed

**`WorkoutViewModel.kt` — `startWorkoutFromRoutine`**
- Replaced `dbSets.mapIndexed { i, ws -> val ghost = ghostSets.getOrNull(i) }` with type-grouped lookup: `ghostSets.groupBy { it.setType }`, per-type counter, `ghostByType[ws.setType]?.getOrNull(typeIndex)`.
- Ghost sets are now matched by SetType (WARMUP→WARMUP, NORMAL→NORMAL, DROP→DROP, FAILURE→FAILURE), not positional index.

**`WorkoutViewModelTest.kt`**
- Added 2 new tests: `startWorkoutFromRoutine ghost warmup set matches previous warmup not working set` and `startWorkoutFromRoutine ghost working set ignores previous warmup data`.

## How to QA

1. Create a routine for an exercise with 2 warmup sets (e.g. W1=40 kg, W2=60 kg) and 2 working sets (100 kg, 95 kg).
2. Complete a workout, logging those sets.
3. Start a new workout with the same routine.
4. Verify the PREV column: warmup rows show 40 kg and 60 kg; working rows show 100 kg and 95 kg. No cross-contamination.
