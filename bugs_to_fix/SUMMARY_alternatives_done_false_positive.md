# Fix Summary: Exercise alternatives all show "You've done this" incorrectly

## Root Cause
`AlternativeExercise.estimatedStartingWeight == null` was used as the sole signal for "user has done this exercise" in the UI. However, `estimatedStartingWeight` can also be `null` when `estimateStartingWeight()` finds no e1RM data on the source (currently-viewed) exercise — i.e., when neither exercise has any history. This caused every alternative to show "You've done this" whenever the user had no workout data at all.

## Files Changed
| File | Change |
|---|---|
| `ui/exercises/detail/ExerciseDetailModels.kt` | Added `userHasDone: Boolean = false` field to `AlternativeExercise` |
| `data/repository/ExerciseDetailRepository.kt` | Pass `userHasDone = userHasHistory` when constructing `AlternativeExercise` |
| `ui/exercises/detail/DetailComponents.kt` | Changed `if/else` to `when` expression: checks `userHasDone` first, then `estimatedStartingWeight != null` |
| `data/repository/ExerciseDetailRepositoryTest.kt` | Updated existing tests to assert `userHasDone`; added new test for the false-positive case (neither main nor alt has history) |

## Surfaces Fixed
- Exercise Detail Screen → Alternatives tab/section: "You've done this" badge now only appears for exercises the user has actually logged sets for

## How to QA
1. Open any exercise you have **never** logged → tap Alternatives tab
2. Verify: no alternative shows "You've done this" (unless you genuinely have history for that exercise)
3. Log a set for one of those alternatives, then reopen the original exercise's Alternatives tab
4. Verify: that specific alternative now shows "You've done this"
5. For exercises with no prior data on either side: verify the card shows no badge at all (neither "You've done this" nor a starting weight estimate)
