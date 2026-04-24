# Functional Training — Block Entities + Migration (v49→v50)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | L |
| **Depends on** | func_style_preference ✅, func_exercise_tags_seed ✅, func_timer_engine_extract ✅ |
| **Blocks** | func_firestore_sync_blocks, func_template_wizard, func_active_strength_blocks |
| **Touches** | `data/database/RoutineBlock.kt` (new), `data/database/WorkoutBlock.kt` (new), `data/database/RoutineBlockDao.kt` (new), `data/database/WorkoutBlockDao.kt` (new), `data/database/PowerMeDatabase.kt`, `di/DatabaseModule.kt`, `data/database/RoutineExercise.kt`, `data/database/WorkoutSet.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §3`, `§4` — read before touching any file in this task. This is the HIGHEST-risk task in the entire P8 phase.

---

## Overview

Introduces the `Block` concept between a Workout and its exercises by adding two new Room entities (`RoutineBlock`, `WorkoutBlock`) and a MIGRATION_49_50 that backfills one implicit `STRENGTH` block per existing routine and workout, pointing all existing `RoutineExercise` and `WorkoutSet` rows at it.

**This migration is irreversible on production databases.** It must ship alone (no UI in the same PR). Write and document rollback SQL before merging.

---

## Behaviour

- `RoutineBlock` entity: `id` (UUID PK), `routineId` (FK CASCADE), `order`, `type` (STRENGTH/AMRAP/RFT/EMOM/TABATA), `name?`, `durationSeconds?`, `targetRounds?`, `emomRoundSeconds?`, `tabataWorkSeconds?`, `tabataRestSeconds?`, `tabataSkipLastRest?` (Int 0/1), `setupSecondsOverride?`, `warnAtSecondsOverride?`, `syncId`, `updatedAt`.
- `WorkoutBlock` entity: same plan fields (including the 5 new columns above) + result fields: `totalRounds?`, `extraReps?`, `finishTimeSeconds?`, `rpe?`, `perExerciseRpeJson?`, `roundTapLogJson?`, `blockNotes?`, `runStartMs?`, `syncId`, `updatedAt`.
- **New column budget:** `routine_blocks` gains 5 columns; `workout_blocks` gains 7 (5 plan + 2 result).
- `RoutineExercise` gains `blockId: String?` and `holdSeconds: Int?`. `holdSeconds` may be populated for **any** exercise type inside AMRAP/RFT blocks (not only `TIMED`-typed exercises) — it is a per-prescription decision. Inside EMOM/TABATA/STRENGTH blocks it is always `NULL`. See `FUNCTIONAL_TRAINING_SPEC.md §10` and §12 Invariant #10.
- `WorkoutSet` gains `blockId: String?`.
- Backfill: `MIGRATION_49_50` inserts one STRENGTH block per routine and per workout via SQL; updates all child rows' `blockId`.
- `blockId IS NULL` is handled gracefully in all UI layers — renders as an implicit unnamed STRENGTH block.
- **Pre-migration validation:** `PreMigrationValidator` asserts row counts for `routine_exercises` and `workout_sets` are identical before and after migration.

See `FUNCTIONAL_TRAINING_SPEC.md §3` for full entity field lists and `§4` for the complete migration SQL.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/database/RoutineBlock.kt` (NEW)
- `app/src/main/java/com/powerme/app/data/database/WorkoutBlock.kt` (NEW)
- `app/src/main/java/com/powerme/app/data/database/RoutineBlockDao.kt` (NEW)
- `app/src/main/java/com/powerme/app/data/database/WorkoutBlockDao.kt` (NEW)
- `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` — bump to v50, add 2 entities, 2 DAOs
- `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` — add `MIGRATION_49_50` + register
- `app/src/main/java/com/powerme/app/data/database/RoutineExercise.kt` — add `blockId`, `holdSeconds`
- `app/src/main/java/com/powerme/app/data/database/WorkoutSet.kt` — add `blockId`

---

## How to QA

1. Export a pre-migration DB fixture (v49). Run migration test: assert every `routine_exercises.blockId` and `workout_sets.blockId` is non-null after migration.
2. Install migrated APK on a device with existing workout history. Open History — existing workouts must display exactly as before.
3. Verify `RoutineBlockDao` and `WorkoutBlockDao` CRUD tests all pass.
4. Verify rollback SQL is documented in the PR description.
