# Functional Training — exerciseType Seed Gap Fix (PLYOMETRIC + STRETCH)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `wrapped` |
| **Effort** | S |
| **Depends on** | func_exercise_tags_seed ✅ |
| **Blocks** | — |
| **Touches** | `res/raw/master_exercises.json`, `data/database/MasterExerciseSeeder.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md` — read before touching any file in this task.

---

## Overview

The Exercise Library filter dialog exposes **PLYOMETRIC** and **STRETCH** filter chips, but both return empty or near-empty results because almost no exercises in the DB carry those `exerciseType` values. Currently only 2 exercises use `PLYOMETRIC` (Medicine Ball Slam, Broad Jump) and 0 use `STRETCH`.

This is a **data-only task** — no schema changes, no new enum values, no UI changes. It retags existing exercises that are currently misclassified as `STRENGTH` and adds new stretch entries so both filter chips return useful results.

---

## Behaviour

### Exercises to retag → PLYOMETRIC

The following exercises are currently `STRENGTH` but are explosive/jump movements that belong under `PLYOMETRIC`:

| Exercise | Current type | Correct type |
|---|---|---|
| Box Jump | STRENGTH | PLYOMETRIC |
| Box Jump Over | STRENGTH | PLYOMETRIC |
| Depth Jump | STRENGTH | PLYOMETRIC |
| Tuck Jump | STRENGTH | PLYOMETRIC |
| Jump Squat | STRENGTH | PLYOMETRIC |
| Clap Push-Up | STRENGTH | PLYOMETRIC |
| Plyo Push-Up | STRENGTH | PLYOMETRIC |
| Lateral Bound | STRENGTH | PLYOMETRIC |
| Hurdle Jump | STRENGTH | PLYOMETRIC |
| Broad Jump (already PLYOMETRIC — verify no duplicate) | PLYOMETRIC | keep |

### Exercises to add or retag → STRETCH

Add new entries (or retag if already present) for common stretches:

| Exercise | Type | Notes |
|---|---|---|
| Hip Flexor Stretch | STRETCH | — |
| Hamstring Stretch | STRETCH | — |
| Quad Stretch | STRETCH | — |
| Child's Pose | STRETCH | — |
| Pigeon Pose | STRETCH | — |
| Downward Dog | STRETCH | — |
| Cat-Cow | STRETCH | — |
| Shoulder Cross-Body Stretch | STRETCH | — |
| Tricep Stretch | STRETCH | — |
| Seated Spinal Twist | STRETCH | — |
| Standing Calf Stretch | STRETCH | — |
| Chest Opener Stretch | STRETCH | — |

### Seeder version

Bump `MasterExerciseSeeder.CURRENT_VERSION` to `"2.3.2"` (v2.3.1 was the `BUG_exercise_type_mismatches` fix).

### Invariants

- Only change `exerciseType` on the retag rows — do not modify any other field.
- New STRETCH entries must follow the standard JSON schema.
- Seeder must NOT bump `updatedAt` on rows that haven't changed.

---

## UI Changes

None — the Exercise Library filter dialog already shows PLYOMETRIC and STRETCH chips. Fixing the seed data makes them return results automatically.

---

## Files to Touch

- `app/src/main/res/raw/master_exercises.json` — retag ~9 PLYOMETRIC exercises + add ~12 STRETCH entries
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — bump `CURRENT_VERSION` to `"2.3.2"`

---

## How to QA

1. Fresh install or clear app data. Open Exercise Library → no crash, all exercises intact.
2. Tap Tune icon → open filter dialog → select **Plyometric** → verify Box Jump, Depth Jump, Tuck Jump, Jump Squat, Clap Push-Up appear.
3. Select **Stretch** → verify Hip Flexor Stretch, Hamstring Stretch, Child's Pose etc. appear (~12 entries).
4. Verify Medicine Ball Slam and Broad Jump still appear under Plyometric.
5. Combine Plyometric + Equipment filter → AND logic still works.
6. Verify no existing exercises lost their data (isFavorite, isCustom preserved).
