# BUG: PREV results mixed between warmup and working sets

## Status
[x] Fixed

## Severity
P1 high
- PREV column shows incorrect data: warmup sets show previous working set values, working sets show previous warmup values. Misleads the user on every set.

## Description
In the active workout screen, the PREV column (previous session data) is misaligned with set types. Warmup rows (W) display the PREV data from working sets of the prior session, and working set rows show data that belongs to warmup sets. The root cause is likely that the ghost data lookup uses positional index rather than matching by set type — so set index 1 returns the first set of the prior session regardless of whether it was a warmup or working set.

Screenshot confirmed: warmup sets W show PREV "32×8@80", "32×8@90", "32×8@95" (clearly working set weights) while working sets show lighter warmup weights.

## Steps to Reproduce
1. Create a routine with warmup sets + working sets for an exercise
2. Complete a workout (log different weights for warmup vs working sets)
3. Start a new workout with the same routine
4. Observe PREV column on that exercise — warmup rows show working set data and vice versa

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`
- Screenshot: workout screen showing W rows with working set PREV values

## Fix Notes
Root cause: `startWorkoutFromRoutine` used `ghostSets.getOrNull(i)` with a positional index over all ghost sets regardless of type. When a previous session had e.g. 2 warmup sets and 1 working set, the first working set (index 2) would receive the 3rd ghost set (2nd warmup) instead of the 1st working set.

Fix: group ghost sets by `SetType` (`ghostByType = ghostSets.groupBy { it.setType }`), track a per-type counter, and resolve each current set's ghost by looking up the nth ghost of the matching type.

Changed: `WorkoutViewModel.kt` — `startWorkoutFromRoutine` section (lines ~487–500). `addExercise` was unaffected by the type-mixing issue (its previous sets are already per-exercise and ordered by `setOrder`).
