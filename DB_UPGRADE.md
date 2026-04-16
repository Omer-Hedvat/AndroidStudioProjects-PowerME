# PowerME Database Upgrade Log

## v42 — History Query Performance Indexes

**Migration:** `MIGRATION_41_42`

### Changes

- **Table:** `workout_sets`
  - New index: `(workoutId, exerciseId)` — covers `SELECT DISTINCT workoutId, exerciseId` subquery in History listing query
  - New index: `(workoutId, isCompleted, setType)` — covering index for set count `GROUP BY` aggregation
- **Table:** `workouts`
  - New index: `(isCompleted, isArchived, timestamp)` — covers `WHERE isCompleted=1 AND isArchived=0 ORDER BY timestamp DESC` in History listing and PR detection queries

### Query Changes
- `WorkoutDao.getAllCompletedWorkoutsWithExerciseNames()` — replaced per-row correlated subquery for `setCount` with a pre-aggregated CTE `set_counts` (computed once via `GROUP BY workoutId` instead of once per result row)

---

## v41 — Session Rating on Workouts

**Migration:** `MIGRATION_40_41`

### Changes

- **Table:** `workouts`
- Added: `sessionRating INTEGER` — optional 1–5 star rating for the workout session

---

## v40 — Health History Ledger

**Migration:** `MIGRATION_39_40`

### Changes

- Created `health_history_entries` table — stores user injury, surgery, condition, and restriction history
- Columns: `id TEXT PK`, `userId TEXT`, `type TEXT` (INJURY/SURGERY/CONDITION/RESTRICTION/OTHER), `title TEXT`, `bodyRegion TEXT`, `severity TEXT` (MILD/MODERATE/SEVERE/RESOLVED), `startDate INTEGER`, `resolvedDate INTEGER`, `notes TEXT`, `affectedExerciseIds TEXT`, `createdAt INTEGER`, `isArchived INTEGER DEFAULT 0`, `firestoreId TEXT DEFAULT ''`, `lastModifiedAt INTEGER DEFAULT 0`
- New entity: `HealthHistoryEntry` with `HealthHistoryType` and `HealthHistorySeverity` enums
- New DAO: `HealthHistoryDao` — `getActiveEntries()` Flow, `getById()`, `insert()`, `softDelete()`, `getAllForSync()`
- New repository: `HealthHistoryRepository` — CRUD + `rebuildMedicalLedger()` auto-mapping (SEVERE+ongoing → red list, MODERATE+ongoing → yellow list, RESOLVED → removed from both)
- Hilt providers added to `DatabaseModule`

---

## v39 — Fitness Level + Training Age on Users

**Migration:** `MIGRATION_38_39`

### Changes

- **Table:** `users`
- Added: `experienceLevel TEXT` — `ExperienceLevel` enum name (NOVICE/TRAINED/EXPERIENCED/ATHLETE), nullable default
- Added: `trainingAgeYears INTEGER` — years of consistent training (0–30+), nullable default
- New `ExperienceLevel` enum in `User.kt` with `displayName` and `description` labels

---

## v38 — Rest After Last Set Flag

**Migration:** `MIGRATION_37_38`

### Changes

Schema change on `exercises` table:
- `ALTER TABLE exercises ADD COLUMN restAfterLastSet INTEGER NOT NULL DEFAULT 0` — when `false` (default), the rest timer is suppressed after the final set of an exercise; when `true`, rest timer fires as normal; configurable per exercise via "Set Rest Timers" dialog
- `Exercise.restAfterLastSet: Boolean = false` field added
- `WorkoutViewModel.completeSet()` checks `isLastSet && !ex.exercise.restAfterLastSet` to gate rest timer start
- `UpdateRestTimersDialog` gains a "Rest after last set" toggle switch; `onUpdateExerciseRestTimers` lambda signature gains a 4th `restAfterLastSet: Boolean` param
- `ExerciseDao.updateRestTimers()` updated to persist the new column

---

## v37 — Remove Chat Messages Table

**Migration:** `MIGRATION_36_37`

### Changes

Data removal — Gemini/chat infrastructure removed:
- `DROP TABLE IF EXISTS chat_messages` — removes the Gemini chat message table
- Removed `ChatMessage` entity, `ChatMessageDao`, and `ChatRepository`
- `PowerMeDatabase` version bumped 36 → 37; `ChatMessage::class` removed from `@Database(entities = [...])`

---

## v36 — Routine Name Denormalization

**Migration:** `MIGRATION_35_36`

### Changes

Schema change on `workouts` table:
- `ALTER TABLE workouts ADD COLUMN routineName TEXT DEFAULT NULL` — snapshot of the routine name at workout creation time
- Back-fills from `routines.name` for rows where `routineId IS NOT NULL`
- History cards now retain routine name even after the routine is deleted (FK is `SET_NULL`, but `routineName` snapshot persists)
- `WorkoutDao.getAllCompletedWorkoutsWithExerciseNames` uses `COALESCE(w.routineName, r.name)` for display
- `WorkoutRepository.instantiateWorkoutFromRoutine()` stamps `routineName` at creation
- `WorkoutViewModel.finishWorkout()` writes `routineName` on the saved entity

---

## v35 — Sync Foundation

**Migration:** `MIGRATION_34_35`

### Changes

Sync identity and conflict-resolution columns across multiple tables:
- Added `syncId TEXT NOT NULL DEFAULT ''` (stable cross-device Firestore identity, UUID-backfilled) to: `exercises`, `exercise_muscle_groups`, `medical_ledger`, `metric_log`, `gym_profiles`, `warmup_log`, `warmup_library`, `state_history`
- Added `updatedAt INTEGER NOT NULL DEFAULT 0` (LWW epoch-ms) to: `exercises`, `medical_ledger`, `gym_profiles`, `user_settings`, `users`
- Existing rows backfilled with SQLite `randomblob`-based UUID v4

---

## v34 — Date of Birth

**Migration:** `MIGRATION_33_34`

### Changes

Schema change on `users` table:
- `ALTER TABLE users ADD COLUMN dateOfBirth INTEGER DEFAULT NULL` — epoch-millis timestamp for date of birth
- Legacy `age INTEGER` column preserved for backward compatibility (existing rows keep their integer age as a fallback)
- `User.ageYears: Int?` extension property computes age from `dateOfBirth` if set, otherwise falls back to `age`
- `WarmupService` and `MetricsViewModel` updated to use `ageYears`
- `ProfileSetupScreen` now uses M3 `DatePickerDialog` for DOB input instead of a numeric age field

---

## v33 — Equipment Type Consolidation

**Migration:** `MIGRATION_32_33`

### Changes

Data-only (no schema modifications):
- `UPDATE exercises SET equipmentType = 'Bench' WHERE equipmentType IN ('Bench/Chair', 'Bench/Couch', 'Bench/Floor', 'Box/Bench', 'Couch/Bench')` — merges 7 exercises from 5 compound synonyms into single `Bench` type
- `UPDATE exercises SET equipmentType = 'Bodyweight' WHERE equipmentType = 'Wall'` — reassigns Tibialis Raise (Wall) to Bodyweight
- `MasterExerciseSeeder` bumped v1.5 → v1.6 (forces reseed from updated `master_exercises.json`)
- Equipment filter chips now display with priority ordering: **Barbell, Dumbbell, Bench, Bodyweight** first, then remaining A-Z (applied in `ExercisesViewModel.sortEquipmentTypes()`)

---

## v31 — UUID String Primary Keys + Soft Delete + Firestore Sync

**Migration:** `MIGRATION_30_31`

### Changes

**Entity PK type changes (Long → String)**
- `Workout.id: String` (UUID), `routineId: String?`, `+updatedAt: Long`, `+isArchived: Boolean`
- `WorkoutSet.id: String` (UUID), `workoutId: String`
- `Routine.id: String` (UUID), `+updatedAt: Long` (was already present)
- `RoutineExercise.id: String` (UUID), `routineId: String`
- `WarmupLog.workoutId: String?` (FK side-effect only; PK stays INTEGER AUTOINCREMENT)

**Migration strategy:** create-new-table → `INSERT … SELECT CAST(id AS TEXT)` → drop old → rename. Existing numeric IDs become `"1"`, `"2"`, etc. New records from the app get proper UUIDs.

**`@Insert` methods now return `Unit`** — callers pre-generate UUIDs before inserting.

**Added `AND isArchived = 0` filters** to `getAllCompletedWorkoutsWithExerciseNames()`, `getActiveWorkout()`, and `getAllWorkouts()` DAO queries.

**New file: `data/sync/FirestoreSyncManager.kt`**
- `pushWorkout(workoutId)` / `pushRoutine(routineId)` — fire-and-forget (no `.await()`); Firestore SDK queues offline
- `pullFromCloud()` — LWW conflict resolution on `updatedAt`; handles `isArchived=true` tombstones
- Firestore paths: `users/{uid}/workouts/{uuid}` (with embedded sets), `users/{uid}/routines/{uuid}` (with embedded exercises)

**Soft deletes:** deleting a workout/routine sets `isArchived=true` + `updatedAt=now` + pushes to Firestore instead of hard-deleting.

---

## v30 — Session Timestamps

**Migration:** `MIGRATION_29_30`

### Changes
- `workouts.startTimeMs INTEGER NOT NULL DEFAULT 0` — epoch ms when session was started (set at workout creation in `WorkoutRepository`)
- `workouts.endTimeMs INTEGER NOT NULL DEFAULT 0` — epoch ms when session was finished (set in `finishWorkout()`)
- `WorkoutExerciseNameRow` DTO updated to include both fields
- `HistoryCard` and `WorkoutDetailScreen` can now display accurate session duration as `endTimeMs - startTimeMs`

---

## v28 — Routine Per-Set Slot Configuration (PLANNED — not yet implemented)

**Migration:** `MIGRATION_27_28`
**Spec:** `WORKOUT_SPEC.md §28`

### Planned Changes

#### New table: `routine_set_slots`
- `id INTEGER PK AUTOINCREMENT`
- `routineExerciseId INTEGER FK` → `routine_exercises.id` ON DELETE CASCADE
- `slotOrder INTEGER NOT NULL` — 1-indexed position within the exercise
- `setType TEXT NOT NULL DEFAULT 'NORMAL'`
- `defaultWeight TEXT NOT NULL DEFAULT ''`
- `reps INTEGER NOT NULL DEFAULT 0`
- `restTimeSeconds INTEGER NOT NULL DEFAULT 90`

### Data Migration
Expand each `routine_exercises` row into N `routine_set_slots` rows (N = `routine_exercises.sets`), copying `.defaultWeight`, `.reps`, `.restTime` into each slot. Existing `routine_exercises.sets`/`.defaultWeight`/`.reps`/`.restTime` columns deprecated (not dropped in v28).

### Future: Exercise Data Fixes (planned, version TBD)
- Romanian Deadlift name normalization migration
- Hammer Curl / Face Pull deduplication migration

---

## v27 — exercise_muscle_groups Index Name Fix (crash fix)

**Migration:** `MIGRATION_26_27`
**Date:** 2026-03-14

### Changes

#### Table: `exercise_muscle_groups` (schema fix — no data change)
- Drops `idx_emg_exerciseId` (the name used by `MIGRATION_23_24`) and recreates it as `index_exercise_muscle_groups_exerciseId` (Room's auto-generated convention: `index_{tableName}_{columnName}`).

### Root Cause
`MIGRATION_23_24` created `idx_emg_exerciseId` but `@Entity(..., indices = [Index(value = ["exerciseId"])])` on `ExerciseMuscleGroup` makes Room expect `index_exercise_muscle_groups_exerciseId`. The mismatch caused `IllegalStateException: Migration didn't properly handle: exercise_muscle_groups` on every cold launch since v24. Mirrors the v26 fix applied to the `exercises` table.

### SQL
```sql
DROP INDEX IF EXISTS idx_emg_exerciseId;
CREATE INDEX IF NOT EXISTS index_exercise_muscle_groups_exerciseId ON exercise_muscle_groups(exerciseId);
```

---

## v26 — Schema Mismatch Fix (crash fix)

**Migration:** `MIGRATION_25_26`
**Date:** 2026-03-14

### Changes

#### Table: `exercises` (schema fix — no data change)
- Added `@ColumnInfo(defaultValue = "")` annotation to `Exercise.searchName` field so Room's expected schema matches the SQL default `''` set by `MIGRATION_24_25`.
- Dropped `idx_exercises_master_unique` partial unique index — Room cannot represent partial indexes via `@Entity(indices=[...])`, causing `IllegalStateException: Migration didn't properly handle: exercises` on every cold start after v24→v25 migration.

### Root Cause
Two schema mismatches between Room-generated expected schema and actual SQLite DB:
1. `searchName` column added in v25 with `DEFAULT ''` but `Exercise.searchName` lacked `@ColumnInfo(defaultValue = "")` → Room expected `defaultValue='undefined'`.
2. `MIGRATION_23_24` created a partial `UNIQUE INDEX` that Room's annotation processor cannot represent → index was untracked, causing schema validation failure.

### SQL
```sql
DROP INDEX IF EXISTS idx_exercises_master_unique;
```

---

## v25 — Pre-Normalized Search Column

**Migration:** `MIGRATION_24_25`
**Date:** 2026-03-14

### Changes

#### Table: `exercises` (schema + data migration)
- New column: `searchName TEXT NOT NULL DEFAULT ''` — pre-normalized version of `name` for fast fuzzy search.
- Back-fill SQL: `LOWER(REPLACE(REPLACE(REPLACE(REPLACE(name, '-', ''), ' ', ''), '(', ''), ')', ''))` applied to all existing rows.
- `MasterExerciseSeeder` bumped to `v1.3` — populates `searchName` via `toSearchName()` extension on every insert/update.
- `DatabaseSeeder` also populates `searchName` for its 6 hardcoded legacy exercises.

### Query Changes
- `ExerciseDao.searchExercises(normalizedQuery)` now targets `searchName` column instead of `name`.
- `ExerciseRepository.searchExercises(query)` normalizes input via `toSearchName()` before passing to DAO.
- `ExercisesViewModel.applyFilters()` normalizes search query once via `toSearchName()` and matches against `exercise.searchName` — eliminates per-exercise Regex allocation on every keystroke.

### Kotlin
- `Exercise.searchName: String = ""` field added (kotlinx.serialization ignores it when reading JSON due to `ignoreUnknownKeys = true`).
- Top-level `fun String.toSearchName()` extension added in `Exercise.kt`.

### UX
- Search ✕ clear button added to `ExercisesScreen` search `OutlinedTextField` (shows when query is non-empty).
- Fuzzy matching now works across hyphens/spaces/parens: "pull up" → "Pull-Up", "rdl" → "RDL-BB", "goblet squat" → "Goblet Squat (KB)".

---

## v24 — Exercise Normalization & Multi-Muscle Group Support

**Migration:** `MIGRATION_23_24`
**Date:** 2026-03-14

### Changes

#### Table: `exercises` (data migration)
- **13 MERGE deduplication**: Legacy DatabaseSeeder exercises with different names but identical canonical counterparts (e.g. "Face Pulls" → "Face Pull") merged: FK references in `routine_exercises` + `workout_sets` re-pointed to canonical ID, legacy row deleted.
- **KEEP-BOTH renames**: Exercises that are genuinely distinct but had ambiguous names renamed for clarity: "Romanian Deadlift (RDL)" → "Romanian Deadlift (RDL) - BB", "Romanian Deadlift (DB)" → "Romanian Deadlift (RDL) - DB", "Weighted Pull-Ups" → "Weighted Pull-Up", "Incline Row" → "Incline Dumbbell Row".
- **Equipment normalization**: `Dumbbells` → `Dumbbell`, `Bodyweight+` → `Bodyweight` (all master exercises).
- **MuscleGroup normalization**: Non-canonical group names standardized — `Rear Delts`→`Shoulders`, `Lats`→`Back`, `Hamstrings`→`Legs`, `Triceps`→`Arms`, `Biceps`→`Arms`, `Abs`→`Core`, `Upper Chest`→`Chest`, `Side Delts`→`Shoulders`, `Chest/Triceps`→`Chest`.

#### New table: `exercise_muscle_groups`
- `id INTEGER PK`, `exerciseId FK`, `majorGroup TEXT`, `subGroup TEXT?`, `isPrimary INTEGER DEFAULT 0`
- FK: `exercises.id ON DELETE CASCADE`
- Index: `idx_emg_exerciseId`
- Populated on migration: every exercise gets one `isPrimary=1` row from its `muscleGroup`.
- Secondary rows added for key compound exercises (Conventional Deadlift, Barbell Bench Press, Pull-Up, etc.)

#### Partial UNIQUE index
- `idx_exercises_master_unique ON exercises(name, equipmentType) WHERE isCustom = 0` — prevents future seeder collisions for master exercises.

### JSON / Seeder
- `master_exercises.json` version bumped `1.1` → `1.2`; "Romanian Deadlift (RDL)" renamed to "Romanian Deadlift (RDL) - BB".
- `DatabaseSeeder.seedExercises()` reduced to 6 genuinely-unique legacy entries; 13 merge-duplicates removed.

---

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
