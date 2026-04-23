# DB Synonym Foundation — UserExerciseSynonym Entity, DAO, Repository

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | AI synonym learning (exercise name matching improvement) |
| **Touches** | `data/database/UserExerciseSynonym.kt`, `data/database/UserSynonymDao.kt`, `data/repository/UserSynonymRepository.kt`, `PowerMeDatabase.kt` (migration wiring — separate session) |

---

## Overview

Adds a `user_exercise_synonyms` table that maps user-typed exercise names (raw strings) to canonical
`Exercise` entities. When a user types "bench" or "flat bench" and selects "Barbell Bench Press", that
mapping is persisted. Future AI parsing and exercise matching can consult this table first before
falling through to fuzzy search, making repeated lookups instant and accurate.

This task covers only the DB layer (entity, DAO, repository). Migration wiring into `PowerMeDatabase`
is a follow-on task.

---

## Behaviour

- `rawName` is stored pre-normalised via `toSearchName()` (lowercase, stripped of spaces/hyphens/parens).
- `useCount` increments on every hit — allows future ranking by frequency.
- Unique index on `rawName` ensures one mapping per normalized alias.
- `CASCADE` delete — if an Exercise is deleted, its synonyms are automatically removed.
- `findExercise(rawName)`: normalise → DB lookup → if hit, increment use count + return Exercise object.
- `saveSynonym(rawName, exerciseId)`: normalise → upsert with `OnConflictStrategy.REPLACE`.

---

## UI Changes

None — this is a pure data layer feature. No composables or screens are modified.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/database/UserExerciseSynonym.kt` — new entity
- `app/src/main/java/com/powerme/app/data/database/UserSynonymDao.kt` — new DAO
- `app/src/main/java/com/powerme/app/data/repository/UserSynonymRepository.kt` — new repository
- `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` — register entity + DAO (migration wiring session)
- `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` — expose DAO via Hilt (migration wiring session)

---

## How to QA

1. Build succeeds with zero errors after migration wiring is complete.
2. Unit tests for `UserSynonymRepository` pass: `findExercise` returns null on miss, returns Exercise on hit, increments `useCount`.
3. Room schema export shows `user_exercise_synonyms` table with correct columns and indices.
