# BUG: Exercise alternatives all show "You've done this" incorrectly

## Status
[x] Fixed

## Severity
P2 normal
- Incorrect data shown to the user — all alternative exercises are marked as previously done even when the user has never logged them.

## Description
In the Exercise Detail screen → Alternatives section/tab, every alternative exercise displays a "You've done this" badge or label regardless of whether the user has actually logged that exercise. The "done" check is either returning true for all exercises by default, or the query is incorrectly matching against the wrong data (e.g. comparing exercise IDs incorrectly, or checking existence in the wrong table/column).

## Steps to Reproduce
1. Go to Exercises tab → tap any exercise
2. Navigate to the Alternatives tab/section in the Exercise Detail screen
3. Observe: every listed alternative shows "You've done this" even for exercises never performed

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ExerciseDetailScreen.kt`, `ExerciseDetailRepository.kt`, `ExercisesViewModel.kt`

## Assets
- Related spec: `future_devs/EXERCISE_DETAIL_TABS_V2_SPEC.md`, `EXERCISES_SPEC.md`

## Fix Notes
`AlternativeExercise.estimatedStartingWeight == null` was being used as a proxy for "user has done this exercise". But `null` also occurs when `estimateStartingWeight()` finds no e1RM on the source exercise, even if the user has never done the candidate. Added explicit `userHasDone: Boolean` field, set from `workoutSetDao.getExerciseSessionCount(candidate.id) > 0`, and updated the UI `when` expression to check `userHasDone` directly.
