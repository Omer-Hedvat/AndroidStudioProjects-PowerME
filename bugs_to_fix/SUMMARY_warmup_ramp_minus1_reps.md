# Fix Summary: Warmup ramp shows -1 reps for last warmup set

## Root Cause
`computeWarmUpRamp` in `ExerciseDetailRepository` included a 100% row with `reps = -1` (sentinel for "this is the working set weight, no warmup reps apply"). The UI rendered `${set.reps}` verbatim, so users saw "-1" in the Reps column of the last row.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/repository/ExerciseDetailRepository.kt` | Removed 100% / -1 row from `computeWarmUpRamp`; ramp now returns only the 3 genuine warmup sets |
| `app/src/test/java/com/powerme/app/data/repository/ExerciseDetailRepositoryTest.kt` | Updated assertion from 4 rows to 3; removed 100% row assertions |

## Surfaces Fixed
- Exercise Detail → About tab → WARM-UP RAMP section no longer shows a "-1 reps" row

## How to QA
1. Open the Exercises tab
2. Tap any barbell compound exercise (e.g. Barbell Back Squat) that you have logged sets for
3. Open Exercise Detail → About tab → scroll to WARM-UP RAMP section
4. Confirm the ramp shows 3 rows: 50%/8 reps, 70%/5 reps, 85%/3 reps — no "-1" row
