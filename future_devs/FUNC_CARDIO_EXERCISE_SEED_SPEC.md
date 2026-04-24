# Functional Training — Cardio Exercise Seed Expansion

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `wrapped` |
| **Effort** | S |
| **Depends on** | func_exercise_tags_seed ✅, func_exercise_expanded_seed ✅ |
| **Blocks** | — |
| **Touches** | `res/raw/master_exercises.json`, `data/database/MasterExerciseSeeder.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md` — read before touching any file in this task.

---

## Overview

The current DB has very few dedicated cardio entries: Run, Rowing (meters/calories), Ski Erg (calories), Assault Bike (calories), Single Under, Double Under, and Jump Rope variants. This task seeds a comprehensive cardio catalogue covering steady-state, interval, and machine-based cardio so users can log cardio sessions in PowerME alongside strength and functional work.

This is a **data-only task** — no schema changes, no new exercise types, no UI changes beyond what `func_exercise_tags_seed` already ships. All new entries use the existing `exerciseType` values (`TIMED` for steady-state/machine work, `BODYWEIGHT` for jump rope / bodyweight cardio) and carry `tags: ["cardio"]`.

---

## Behaviour

- All new entries tagged `["cardio"]` at minimum. Machine-based entries also get `["functional","cardio"]` where appropriate (e.g. Assault Bike, SkiErg, Rowing are already used in functional WODs).
- `exerciseType`:
  - Distance/time machine work (treadmill, bike, rower, ski erg, elliptical, stair climber) → `"TIMED"`
  - Jump rope / bodyweight cardio → `"BODYWEIGHT"`
  - Swimming → `"TIMED"`
- `MasterExerciseSeeder.CURRENT_VERSION` bumps from `"2.2"` → `"2.3"` (func_exercise_expanded_seed ships at 2.2; this follows).
- Seeder must NOT bump `updatedAt` on unchanged rows.
- Calories and meters variants are separate entries (matching the existing Rowing pattern) where the tracking unit differs.

### Scope — categories to cover

| Category | Examples |
|---|---|
| Treadmill | Walk, Jog, Run (already exists — retag only), Sprint, Incline Walk, Incline Run |
| Cycling | Stationary Bike (calories), Stationary Bike (minutes), Spin Bike, Outdoor Cycling |
| Rowing machine | Rowing (meters) ✅, Rowing (calories) ✅, Rowing (watts) |
| Ski Erg | Ski Erg (calories) ✅, Ski Erg (meters), Ski Erg (watts) |
| Assault / Echo Bike | Assault Bike (calories) ✅, Assault Bike (watts), Echo Bike (calories), Echo Bike (watts) |
| Jump rope | Single Under ✅, Double Under ✅, Triple Under, Speed Rope (calories) |
| Swimming | Freestyle Swim, Backstroke Swim, Breaststroke Swim, Butterfly Swim, Pool Laps |
| Elliptical | Elliptical (minutes), Elliptical (calories) |
| Stair climber | Stair Climber (minutes), Stair Climber (floors) |
| Walking / Hiking | Walk (already covered by Treadmill Walk), Hiking, Rucking |
| LISS / HIIT protocols | LISS Cardio (30+ min steady state), HIIT Cardio (interval notation) |

### Existing exercises needing `"cardio"` retag only (no new entry)

These are already in the DB but lack the `"cardio"` tag:
- Run → add `"cardio"`
- Rowing (meters) → add `"cardio"`
- Rowing (calories) → add `"cardio"`
- Ski Erg (calories) → add `"cardio"`
- Assault Bike (calories) → add `"cardio"`
- Single Under → add `"cardio"`
- Double Under (Jump Rope) → add `"cardio"`
- Jumping Jacks → add `"cardio"`
- Mountain Climber → add `"cardio"` (already functional, add cardio too)

---

## UI Changes

None — the existing Functional filter chip and standard exercise search surface all new entries automatically. A future "Cardio" filter chip can be added as a separate task.

---

## Files to Touch

- `app/src/main/res/raw/master_exercises.json` — add new entries + retag existing cardio exercises
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — bump `CURRENT_VERSION` to `"2.3"`

---

## How to QA

1. Install freshly migrated APK. Open Exercise Library. Verify no crash, existing exercises intact.
2. Search "treadmill" — treadmill variants appear.
3. Search "swim" — freestyle/backstroke/etc. appear.
4. Search "stair" — Stair Climber entries appear.
5. Verify Run, Rowing, Ski Erg, Assault Bike now carry the `"cardio"` tag (search within filtered list if chip is available).
6. Verify `isFavorite` state is preserved for any pre-existing exercise.
