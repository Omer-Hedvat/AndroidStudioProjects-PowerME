# SUMMARY: BUG_prev_rpe_multiplied

## What changed

**`WorkoutViewModel.kt`**
- Added `formatGhostRpe(rpe: Int): String` file-level helper: `val d = rpe / 10.0; if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)`. Matches the live RPE display logic in `ActiveWorkoutScreen.kt`.
- `startWorkoutFromRoutine`: changed `ghostRpe = ghost?.rpe?.toString()` → `ghostRpe = ghost?.rpe?.let { formatGhostRpe(it) }`.
- `addExercise`: same change for `prevSet.rpe?.toString()`.

**`WorkoutViewModelTest.kt`**
- Fixed 2 pre-existing tests that used `rpe = 8`/`9` (wrong scale) — updated to `rpe = 80`/`90` (correct ×10 scale).
- Added 5 new tests covering whole-number RPE ("9"), decimal RPE ("6.5"), null RPE, and both `startWorkoutFromRoutine` and `addExercise` paths.

## How to QA

1. Complete a workout with RPE 9 logged on a set.
2. Start a new workout with the same exercise.
3. Verify the PREV column shows e.g. `100×5@9` — not `100×5@90`.
4. Also verify a 6.5 RPE shows as `@6.5`.
