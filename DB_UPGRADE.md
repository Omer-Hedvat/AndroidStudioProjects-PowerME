# PowerME Database Upgrade Log

## v22 — Iron Vault Sprint (Active Workout DB Persistence)

**Migration:** `MIGRATION_21_22`
**Date:** 2026-02-25

### Changes

#### Table: `workout_sets`
- New column: `isCompleted INTEGER NOT NULL DEFAULT 0` — tracks whether the set was marked complete during the session; used by rehydration to restore UI state after process death; `finishWorkout()` calls `deleteIncompleteSetsByWorkout()` to clean skeleton rows

### Risk Fixes Included
| Area | Description | Fix |
|------|-------------|-----|
| Process Death | Active workout state lost on kill | Iron Vault: Workout + WorkoutSets written to DB at session start; rehydrateIfNeeded() on VM init |
| Cancel Cleanup | Orphaned isCompleted=0 row left in workouts table | cancelWorkout() deletes WorkoutSets + Workout by ID |
| Finish Path | Duplicate workout record created | finishWorkout() updates existing Workout row (not insert) |
| SetType Persistence | SetType lost on process death | cycleSetType() calls updateSetType() DAO immediately |

### SQL
```sql
ALTER TABLE workout_sets ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0;
```

---

## v21 — Data Hardening Phase (PowerME Phase 0 Risk Resolution)

**Migration:** `MIGRATION_20_21`
**Date:** 2026-02-25

### Changes

#### Table: `workouts` (recreated)
- `routineId INTEGER` is now nullable (was `NOT NULL`)
- Foreign key `ON DELETE` changed from `CASCADE` → `SET NULL` (orphan protection: deleting a Routine no longer cascades to delete workout history)
- New column: `isCompleted INTEGER NOT NULL DEFAULT 0` — settled-data gate; `finishWorkout()` sets it to `1`; existing records migrated with `isCompleted = 1` to preserve History visibility

#### Table: `routine_exercises`
- New column: `stickyNote TEXT` (nullable) — merged from the deleted `routine_exercise_cross_ref` table

#### Table: `routine_exercise_cross_ref` — **DROPPED**
- Sticky-note data migrated into `routine_exercises.stickyNote` before drop

### Risk Fixes Included
| Risk | Description | Fix |
|------|-------------|-----|
| RISK 3 | CASCADE deletes history | SET_NULL FK + nullable routineId |
| RISK 1 | No @Transaction on instantiate | `instantiateWorkoutFromRoutine` wrapped in `database.withTransaction` |
| RISK 2 | Ghost data from abandoned workouts | `getPreviousSessionSets` filters `AND w.isCompleted = 1` |
| RISK 4 | updateLastPerformed never called | `finishWorkout()` calls `routineDao.updateLastPerformed()` |
| RISK 5 | Dual-table redundancy | `routine_exercise_cross_ref` dropped; data merged into `routine_exercises` |
| RISK 6 | History has no drill-down | `getAllCompletedWorkoutsWithExerciseNames` query + `HistoryViewModel` collapse + exercise chip row in `WorkoutHistoryCard` |

### SQL
```sql
-- 1. Recreate workouts with nullable routineId + SET_NULL FK + isCompleted
CREATE TABLE workouts_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    routineId INTEGER,
    timestamp INTEGER NOT NULL,
    durationSeconds INTEGER NOT NULL,
    totalVolume REAL NOT NULL,
    notes TEXT,
    isCompleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE SET NULL
);
-- Existing records get isCompleted=1 to preserve History visibility
INSERT INTO workouts_new SELECT id, routineId, timestamp, durationSeconds, totalVolume, notes, 1 FROM workouts;
DROP TABLE workouts;
ALTER TABLE workouts_new RENAME TO workouts;
CREATE INDEX IF NOT EXISTS index_workouts_routineId ON workouts(routineId);

-- 2. Merge stickyNote and drop cross_ref
ALTER TABLE routine_exercises ADD COLUMN stickyNote TEXT;
UPDATE routine_exercises SET stickyNote = (
    SELECT cr.stickyNote FROM routine_exercise_cross_ref cr
    WHERE cr.routineId = routine_exercises.routineId
      AND cr.exerciseId = routine_exercises.exerciseId
);
DROP TABLE routine_exercise_cross_ref;
```

### Files Changed
- `Workout.kt` — nullable routineId, SET_NULL FK, isCompleted field
- `RoutineExercise.kt` — stickyNote field
- `PowerMeDatabase.kt` — v21, removed CrossRef entity/dao
- `RoutineExerciseCrossRef.kt` — **DELETED**
- `RoutineExerciseCrossRefDao.kt` — **DELETED**
- `RoutineExerciseDao.kt` — getStickyNote + updateStickyNote
- `WorkoutSetDao.kt` — isCompleted=1 filter in getPreviousSessionSets
- `WorkoutDao.kt` — WorkoutExerciseNameRow + getAllCompletedWorkoutsWithExerciseNames
- `WorkoutRepository.kt` — withTransaction + PowerMeDatabase injection
- `WorkoutViewModel.kt` — isCompleted=true + updateLastPerformed in finishWorkout; RoutineExerciseDao replaces CrossRefDao
- `ActionExecutor.kt` — RoutineExerciseDao replaces CrossRefDao
- `DatabaseModule.kt` — MIGRATION_20_21 added, CrossRefDao provider removed
- `HistoryViewModel.kt` — WorkoutWithExerciseSummary flow
- `HistoryScreen.kt` — exercise name chips in WorkoutHistoryCard

---

## v20 — Routine Archive + RoutineExercise Table

**Migration:** `MIGRATION_19_20`
**Date:** 2026-02-22

### Changes
- **Table:** `routines` — Added `isArchived INTEGER NOT NULL DEFAULT 0`
- Created `routine_exercises` table (id, routineId FK, exerciseId FK, sets, reps, restTime, order, supersetGroupId) with indices on routineId + exerciseId

---

## v19 — Targeted Cleanse of "181.5 cm:" Prefix in setupNotes

**Migration:** `MIGRATION_18_19`
**Date:** 2026-02-22

### Changes
- **Table:** `exercises`
- **Operation:** `UPDATE` (no schema change — data-only migration)
- Strips the literal `"181.5 cm:"` prefix from `setupNotes` values where it was prepended during the v17→18 migration window. This prefix is longer than 20 chars when combined with coaching cues so it bypassed the v17→18 surgical guard.
- `NULLIF(TRIM(...), '')` ensures any remainder that is empty after stripping becomes `NULL`.

### Purpose
Fixes a second wave of leaked profile metric data: exercises whose `setupNotes` began with `"181.5 cm:"` followed by legitimate coaching cues. The v18 cleanse (≤ 20 chars guard) did not catch these because the combined string exceeded 20 characters.

### SQL (SurgicalValidator.MIGRATION_SQL_V19)
```sql
UPDATE exercises
SET setupNotes = NULLIF(TRIM(REPLACE(setupNotes, '181.5 cm:', '')), '')
WHERE setupNotes LIKE '%181.5 cm:%'
```

---

## v18 — Surgical Cleanse of Leaked Profile Metrics in setupNotes

**Migration:** `MIGRATION_17_18`
**Date:** 2026-02-21

### Changes
- **Table:** `exercises`
- **Operation:** `UPDATE` (no schema change — data-only migration)
- Clears `setupNotes` values that are ≤ 20 characters AND end with a unit suffix (`cm`, `kg`, `lbs`, `lb`), indicating they were accidentally set to a height/weight value from the user profile instead of an actual setup cue.

### Purpose
Fixes a bug where the `setupNotes` field on exercises contained leaked profile metrics (e.g. "180cm", "72 kg") instead of legitimate coaching cues. Surgical guard: notes longer than 20 chars (e.g. "Keep chest up, brace core…") are preserved.

### SQL (SurgicalValidator.MIGRATION_SQL)
```sql
UPDATE exercises SET setupNotes = NULL
WHERE setupNotes IS NOT NULL
AND LENGTH(setupNotes) <= 20
AND (
    setupNotes LIKE '%cm' OR setupNotes LIKE '% cm'
    OR setupNotes LIKE '%kg' OR setupNotes LIKE '% kg'
    OR setupNotes LIKE '%lbs' OR setupNotes LIKE '% lbs'
    OR setupNotes LIKE '%lb' OR setupNotes LIKE '% lb'
)
```

### New DAO Query (ExerciseDao)
```kotlin
@Query(SurgicalValidator.MIGRATION_SQL)
suspend fun clearLeakedMetricNotes()
```

---

## v17 — Sticky Notes for Routine Exercises

**Migration:** `MIGRATION_16_17`
**Date:** 2026-02-20

### Changes
- **Table:** `routine_exercise_cross_ref`
- **Added column:** `stickyNote TEXT` (nullable)

### Purpose
Allows users to attach a persistent "sticky note" to an exercise within a routine. Unlike session notes (which are volatile/in-memory only), sticky notes are stored in Room and reloaded each time the same exercise is used in the same routine.

### SQL
```sql
ALTER TABLE routine_exercise_cross_ref ADD COLUMN stickyNote TEXT;
```

### New DAO Queries (RoutineExerciseCrossRefDao)
```kotlin
@Query("UPDATE routine_exercise_cross_ref SET stickyNote = :note WHERE routineId = :routineId AND exerciseId = :exerciseId")
suspend fun updateStickyNote(routineId: Long, exerciseId: Long, note: String?)

@Query("SELECT stickyNote FROM routine_exercise_cross_ref WHERE routineId = :routineId AND exerciseId = :exerciseId")
suspend fun getStickyNote(routineId: Long, exerciseId: Long): String?
```

---

## v16 — Superset Group IDs

**Migration:** `MIGRATION_15_16`

### Changes
- **Table:** `workout_sets`
- **Added column:** `supersetGroupId TEXT` (nullable) — UUID linking paired exercises into a superset group

---

## v15 — User Body Measurements

**Migration:** `MIGRATION_14_15`

### Changes
- **Table:** `users`
- Added: `weightKg REAL`, `bodyFatPercent REAL`, `gender TEXT`, `trainingTargets TEXT`

---

## v14 — Gym Dumbbell Ranges

**Migration:** `MIGRATION_13_14`

### Changes
- **Table:** `gym_profiles`
- Added: `dumbbellMinKg REAL`, `dumbbellMaxKg REAL`

---

## v13 — Metric Log + Language Setting

**Migration:** `MIGRATION_12_13`

### Changes
- Created `metric_log` table with `id`, `timestamp`, `type`, `value`
- Added: `user_settings.language TEXT DEFAULT 'Hebrew'`

---

## v12 — State History

**Migration:** `MIGRATION_11_12`

### Changes
- Created `state_history` table for audit trail of document changes

---

## v11 — Set Timing

**Migration:** `MIGRATION_10_11`

### Changes
- **Table:** `workout_sets`
- Added: `startTime INTEGER`, `endTime INTEGER`, `restDuration INTEGER`

---

## v10 — Users + Medical Ledger

**Migration:** `MIGRATION_9_10`

### Changes
- Created `users` table
- Created `medical_ledger` table

---

## v9 — Gym Profiles

**Migration:** `MIGRATION_8_9`

### Changes
- Created `gym_profiles` table with default Home and Work profiles

---

## v8 — Exercise Media + Chat Actions

**Migration:** `MIGRATION_7_8`

### Changes
- **Table:** `exercises`
- Added: `isFavorite`, `isCustom`, `youtubeVideoId`, `familyId`
- **Table:** `chat_messages`
- Added: `actionData TEXT`

---

## v7 — Exercise Types + User Settings

**Migration:** `MIGRATION_6_7`

### Changes
- **Table:** `exercises`
- Added: `exerciseType`, `setupNotes`, `barType`
- **Table:** `workout_sets`
- Added: `setNotes`, `distance`, `timeSeconds`
- Created `user_settings` table
- Created `health_connect_sync` table
