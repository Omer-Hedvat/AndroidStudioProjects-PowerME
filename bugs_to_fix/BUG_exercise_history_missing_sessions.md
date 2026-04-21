# BUG: Exercise History Tab Missing Sessions

## Status
[x] Fixed

## Severity
P1 high
- Affects daily use — users cannot see their full training history for a movement

## Description
When opening Exercises → [exercise] → History tab, not all logged sessions appear. Some workouts that
include the exercise are visible in the History tab (main) but are absent from the per-exercise
History tab. The likely root cause is the SQL filter in `TrendsDao.getExerciseWorkoutHistory`:

```sql
WHERE ws.exerciseId = :exerciseId
  AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
  AND w.isCompleted = 1 AND w.isArchived = 0
```

Two candidate causes:
1. **`ws.isCompleted = 1`** — sets within a completed workout may be stored with `isCompleted = false`
   (e.g. if the workout was force-finished while a set was still open). The workout is shown in History
   because `w.isCompleted = 1`, but its sets are invisible to the exercise history query.
2. **`ws.setType != 'WARMUP'`** — a session where the exercise was only performed as warmup sets
   (no working sets logged) is silently excluded.

## Steps to Reproduce
1. Log a workout containing at least one exercise. Either cancel mid-set (leaving a set unchecked) or
   log the exercise only via the warmup ramp without adding working sets.
2. Open Exercises → the same exercise → History tab.
3. Observe: the session from step 1 does not appear in the History tab.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `TrendsDao.kt`, `ExerciseDetailRepository.kt`, possibly `WorkoutViewModel.kt` (set completion behaviour on finish/cancel)

## Assets
- Related spec: `EXERCISES_SPEC.md`, `HISTORY_ANALYTICS_SPEC.md`

## Fix Notes
Root cause: `TrendsDao.getExerciseWorkoutHistory` filtered `ws.setType != 'WARMUP'`, excluding sessions where the exercise was only performed as warmup sets. `getExerciseLastPerformed` had the same filter. Both queries now omit the setType filter so all completed sets (regardless of type) qualify a session for inclusion in exercise history.

Files changed:
- `TrendsDao.kt` — removed `AND ws.setType != 'WARMUP'` from both `getExerciseWorkoutHistory` and `getExerciseLastPerformed`
- `ExerciseDetailRepositoryTest.kt` — added two `getWorkoutHistory` pagination tests
