# SUMMARY: exercise_history_missing_sessions

## Root Cause
`TrendsDao.getExerciseWorkoutHistory` filtered `AND ws.setType != 'WARMUP'`. Sessions where an exercise was only performed as warmup sets (no working sets logged) contained no qualifying rows, so those workouts were silently excluded from the per-exercise History tab. The main History tab showed them because its query does not filter by set type.

`getExerciseLastPerformed` had the same filter, so "last performed" date could also be stale for warmup-only users.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/TrendsDao.kt` | Removed `AND ws.setType != 'WARMUP'` from `getExerciseWorkoutHistory` and `getExerciseLastPerformed` |
| `app/src/test/java/com/powerme/app/data/repository/ExerciseDetailRepositoryTest.kt` | Added two `getWorkoutHistory` pagination tests |

## Surfaces Fixed
- Exercises → [exercise] → History tab now shows all sessions including warmup-only ones
- "Last performed" date now reflects warmup-only sessions too

## How to QA
1. Open any exercise that you know you've used in warmup sets (via the warmup ramp) but may not have added working sets for
2. Navigate to Exercises → that exercise → History tab
3. Confirm those warmup-only sessions now appear in the list
4. Cross-check: open the same workout from the main History tab and verify the exercise appears in the workout detail
