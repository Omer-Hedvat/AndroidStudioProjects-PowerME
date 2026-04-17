# Fix Summary: exercise_muscle_groups never populated on fresh install

## Root Cause

Room creates the DB at the current schema version on fresh install, skipping all migrations. The migration SQL that populated `exercise_muscle_groups` (MIGRATION_19_20 Part 6) therefore never ran. `MasterExerciseSeeder.performSeed()` only wrote to the `exercises` table, leaving `exercise_muscle_groups` permanently empty.

Both `MuscleGroupVolumeCard` and `EffectiveSetsCard` JOIN `workout_sets` with `exercise_muscle_groups` — with an empty join table, every query returned zero rows and both cards showed their "not enough data" empty states regardless of workout history.

Upgrade users were also partially affected: new exercises added by seeder v1.7 (~90 exercises) had no EMG rows because the migration only covered exercises that existed at migration time.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/ExerciseMuscleGroupDao.kt` | Added `getAllExerciseIds()` query for efficient backfill |
| `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` | Added `@Provides` for `ExerciseMuscleGroupDao`; injected into `provideMasterExerciseSeeder` |
| `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` | Injected `ExerciseMuscleGroupDao`; insert/update paths now write EMG rows; backfill sweep at end of performSeed; `CURRENT_VERSION` bumped to `"1.8"` |
| `app/src/main/java/com/powerme/app/data/csvimport/StrongCsvImporter.kt` | Injected `ExerciseMuscleGroupDao`; inserts EMG row when creating custom exercises |
| `app/src/test/java/com/powerme/app/data/database/MasterExerciseSeederTest.kt` | New: 4 tests covering fresh seed, update-with-missing-EMG, no-duplicate, and backfill paths |

## Surfaces Fixed

- **Trends tab → MuscleGroupVolumeCard** — was always empty on fresh install; now shows weekly muscle group volume breakdown
- **Trends tab → EffectiveSetsCard** — was always empty on fresh install; now shows effective sets per muscle group

## How to QA

1. **Fresh install test:**
   - Uninstall the app completely (or clear app data)
   - Install the new debug APK
   - Sign in and complete at least 2 workouts targeting different muscle groups (e.g. one Chest day, one Legs day), each with at least a few sets
   - Navigate to Trends tab → scroll down to MuscleGroupVolumeCard
   - **Expected:** chart shows bars for the muscle groups you trained (not empty state)
   - Scroll to EffectiveSetsCard — same: should show data, not empty state

2. **Upgrade test:**
   - Install the previous build, complete some workouts, then install the new build
   - Open the app — seeder v1.8 will run the backfill automatically on first launch
   - Navigate to Trends → MuscleGroupVolumeCard and EffectiveSetsCard should show data

3. **No duplicate rows:**
   - Relaunch the app after the first seeder run
   - Charts should still show correct data (no duplicate volume due to double-inserted EMG rows)
