# Yoga Stretch Seed — Extend STRETCH Exercise Library

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | func_exercise_type_retag_seed ✅ |
| **Blocks** | — |
| **Touches** | `res/raw/master_exercises.json`, `data/database/MasterExerciseSeeder.kt` |

---

## Overview

Extends the STRETCH exercise category with ~15 yoga-based poses added in v2.3.2 (Hip Flexor Stretch, Hamstring Stretch, etc.). These yoga entries give the STRETCH filter chip in the Exercise Library a richer, more varied catalogue — covering standing poses, floor poses, and restorative postures that are common in post-workout mobility routines.

This is a **data-only task** — no schema changes, no UI changes.

---

## Behaviour

### Exercises to add → STRETCH

| Exercise | Muscle Group | Notes |
|---|---|---|
| Warrior I | Full Body | Standing hip flexor + quad opener |
| Warrior II | Full Body | Hip abductor + groin stretch |
| Warrior III | Full Body | Single-leg balance + hip extension |
| Triangle Pose | Full Body | Lateral trunk + hip adductor |
| Bridge Pose | Back | Spine extension + hip flexor |
| Cobra Pose | Back | Prone spine extension / anterior chain |
| Supine Spinal Twist | Back | Lying thoracic rotation |
| Lizard Pose | Legs | Deep hip flexor + groin |
| Reclined Butterfly | Legs | Inner thigh / groin opener |
| Standing Forward Fold | Legs | Hamstring + calf + spine decompression |
| Low Lunge (Crescent) | Legs | Hip flexor / psoas |
| Happy Baby | Legs | Hip external rotation + lower back |
| Legs Up the Wall | Legs | Passive hamstring + restorative |
| Seated Forward Fold | Legs | Bilateral hamstring |
| Wide-Legged Forward Fold | Legs | Adductor + hamstring |

### Seeder version

Bump `MasterExerciseSeeder.CURRENT_VERSION` to `"2.3.3"`.

### Invariants

- New STRETCH entries must follow the standard JSON schema (same fields as v2.3.2 STRETCH entries).
- Do not modify any existing exercise — inserts only.
- Seeder must NOT bump `updatedAt` on rows that haven't changed.

---

## UI Changes

None — STRETCH filter chip already exists. New entries appear automatically.

---

## Files to Touch

- `app/src/main/res/raw/master_exercises.json` — add ~15 yoga STRETCH entries
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — bump `CURRENT_VERSION` to `"2.3.3"`

---

## How to QA

1. Clear app data (or fresh install). Open Exercise Library → no crash.
2. Tap Tune icon → select **Stretch** → verify all ~27 STRETCH entries appear (12 from v2.3.2 + 15 new).
3. Verify existing stretches from v2.3.2 still appear (Hip Flexor Stretch, Child's Pose, etc.).
4. Verify no existing exercises lost their data.
