# Functional Training — Exercise Tags Column + Seed Expansion

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `wrapped` |
| **Effort** | M |
| **Depends on** | — |
| **Blocks** | func_block_entities_migration |
| **Touches** | `data/database/Exercise.kt`, `di/DatabaseModule.kt` (MIGRATION_48_49), `data/database/Converters.kt`, `data/database/MasterExerciseSeeder.kt`, `res/raw/master_exercises.json`, `ui/exercises/ExercisesViewModel.kt`, `ui/exercises/ExercisesScreen.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §6` — read before touching any file in this task.

---

## Overview

Add a `tags: String` JSON-array column to the `Exercise` entity (e.g. `["functional","olympic"]`). Seed ~40 new functional movements (Olympic lifts, MetCon staples, Gymnastics, Monostructural cardio) with appropriate tags. Retag existing dual-use exercises (Burpee, KB Swing, Double-Under, etc.). Add a "Functional" filter chip to the Exercise Library screen.

This task is Tier 0 — no block schema changes, no runtime changes.

---

## Behaviour

- `tags` column: `TEXT NOT NULL DEFAULT '[]'` (JSON array, parsed via new `Converters` entry).
- `MasterExerciseSeeder` bumps `CURRENT_VERSION` from `"2.0"` → `"2.1"`. Reseed preserves `isFavorite`, `isCustom`, `syncId`; must NOT bump `updatedAt` on unchanged rows.
- `ExercisesViewModel.applyFilters()` gains a 4th dimension: `tagFilter: Set<String>`. When `"functional" in tagFilter`, only exercises with `"functional" in tags` are shown.
- New "Functional" filter chip in `ExercisesScreen` (alongside Muscle Group and Equipment chips). Default: off. Shows `~55 exercises` when toggled on.
- Gym Picker (used when adding to a Strength block in future tasks): hard-filters `"functional-only" !in tags`. No chip needed — the filter is automatic.
- Functional Picker (future task): hard-filters `"functional" in tags`.

See `FUNCTIONAL_TRAINING_SPEC.md §6.3` for the full list of ~40 new movements and their tags/familyId assignments.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/database/Exercise.kt` — add `tags: String = "[]"`
- `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` — add `MIGRATION_48_49` (`ALTER TABLE exercises ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'`)
- `app/src/main/java/com/powerme/app/data/database/Converters.kt` — `@TypeConverter` for `tags` ↔ `List<String>`
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — parse `tags` field; bump version
- `app/src/main/res/raw/master_exercises.json` — add tags to existing entries + ~40 new movements
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — add `tagFilter` dimension
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — "Functional" chip

---

## How to QA

1. Install freshly migrated APK. Open Exercise Library. Verify no crash, existing exercises intact.
2. Toggle "Functional" chip; verify ~55 functional exercises appear (Olympic, MetCon, Gymnastics, Monostructural groups present).
3. Search "rope climb" — appears only when Functional chip is on (or when explicitly searched).
4. Search "barbell back squat" — appears normally (no functional tag, always visible).
5. Check that `isFavorite` state is preserved across reseed.
