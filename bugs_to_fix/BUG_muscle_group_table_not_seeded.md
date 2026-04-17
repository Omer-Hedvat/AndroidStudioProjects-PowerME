# BUG: exercise_muscle_groups table never populated on fresh install — MuscleGroupVolumeCard and EffectiveSetsCard always empty

## Status
[x] Fixed

## Description
`MuscleGroupVolumeCard` and `EffectiveSetsCard` both always show their empty-state message ("Log at least 1 week of workouts to see muscle breakdown") even when the user has months of workout history.

**Root cause:** Both Trends DAO queries JOIN `workout_sets` with `exercise_muscle_groups`:
```sql
JOIN exercise_muscle_groups emg ON ws.exerciseId = emg.exerciseId
```
On a **fresh install**, `exercise_muscle_groups` is always empty:
- Room creates the DB at the current schema version and skips all migrations.
- The migration SQL (`INSERT INTO exercise_muscle_groups SELECT id, muscleGroup ... FROM exercises`) only runs during an upgrade, not on fresh install.
- `MasterExerciseSeeder.performSeed()` inserts exercises into the `exercises` table but never writes to `exercise_muscle_groups`.

Result: the JOIN always produces zero rows, so both cards render the empty state regardless of data.

**Upgrade users:** partly affected — `exercise_muscle_groups` was populated at migration time for exercises that existed then, but any new exercises added by later seeder versions (`v1.7` adds ~90 new exercises) also have no entries.

## Steps to Reproduce
1. Fresh install (or clear app data)
2. Sign in and complete several workouts across multiple muscle groups
3. Navigate to Trends tab → scroll to MuscleGroupVolumeCard
4. Observe: "Log at least 1 week of workouts to see muscle breakdown" even though workout data exists

## Fix Direction
`MasterExerciseSeeder.performSeed()` must write to `exercise_muscle_groups` for every exercise it inserts. On update path, also backfill any exercises missing from `exercise_muscle_groups`. Minimal fix:

```kotlin
// After inserting/updating an exercise:
val inserted = exerciseDao.insertExercise(...)  // returns new row id
exerciseMuscleGroupDao.insertIfAbsent(
    ExerciseMuscleGroup(
        exerciseId = inserted,
        majorGroup = masterExercise.muscleGroup,
        subGroup   = masterExercise.muscleGroup,
        isPrimary  = true
    )
)
```

Also add a one-time backfill on `seedIfNeeded()` for upgrade users: for every exercise in `exercises` with no corresponding row in `exercise_muscle_groups`, insert the default single-primary-group row.

## Assets
- Related spec: `future_devs/TRENDS_CHARTS_SPEC.md §Step 4`, `§Step 5`
- Affected cards: `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`
- Root data path: `MasterExerciseSeeder.kt` → `exercise_muscle_groups` table

## Fix Notes
`MasterExerciseSeeder` now injects `ExerciseMuscleGroupDao` and writes to `exercise_muscle_groups` at three points:

1. **Insert path** — after `exerciseDao.insertExercise()`, immediately inserts an `ExerciseMuscleGroup` row (`majorGroup = exercise.muscleGroup`, `isPrimary = true`).
2. **Update path** — after `exerciseDao.updateExercise()`, inserts the primary EMG row if the exercise had none (guarded by a `HashSet` of existing IDs fetched once at the start of the seed run).
3. **Backfill sweep** — after the main loop, queries all exercises and bulk-inserts EMG rows for any that are still missing (covers custom exercises skipped by the main loop, and any edge cases on upgrade users).

`StrongCsvImporter` also updated: when it creates a custom exercise during CSV import, it now also inserts the EMG row.

`CURRENT_VERSION` bumped `"1.7"` → `"1.8"` so existing installs trigger a re-seed that runs the backfill.

`ExerciseMuscleGroupDao.getAllExerciseIds()` query added to enable the efficient single-query backfill.

`DatabaseModule.kt` updated to provide `ExerciseMuscleGroupDao` via Hilt and pass it to `provideMasterExerciseSeeder`.

4 new unit tests in `MasterExerciseSeederTest` verify all paths.
