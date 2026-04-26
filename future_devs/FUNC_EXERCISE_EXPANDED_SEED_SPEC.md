# Functional Training — Expanded Exercise Seed (CrossFit / Hyrox / Calisthenics)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | func_exercise_gap_analysis ✅, func_exercise_tags_seed ✅, func_crossfit_verification ✅ |
| **Blocks** | — |
| **Touches** | `res/raw/master_exercises.json`, `data/database/MasterExerciseSeeder.kt` |

---

## Overview

Populate `master_exercises.json` with the full set of exercises required for comprehensive CrossFit, Hyrox, and calisthenics support. Two research passes have been completed and all JSON is pre-filled — this task is purely implementation (paste + normalize + test).

**Research outputs to use (read both before touching any file):**
- `future_devs/FUNC_EXERCISE_GAP_ANALYSIS_RESULTS.md` — 42 net-new exercises (first pass)
- `future_devs/FUNC_CROSSFIT_VERIFICATION_RESULTS.md` — 19 additional exercises missed by first pass, verified against crossfit.com, gym-mikolo.com, and a CF workout PDF

**Total scope: 61 net-new exercises + 41 existing exercises needing `"functional"` retag.**

Target disciplines and coverage goals:

- **CrossFit** — movements appearing in The Girls, The Heroes, standard Open/Quarterfinal workouts, and the official crossfit.com movement library (35 net-new)
- **Hyrox** — all 8 official race stations + common Hyrox training accessories (4 net-new)
- **Calisthenics / Gymnastics** — planche, front lever, back lever, ring strength, bar skills, one-arm pull-up, human flag, manna (22 net-new)

---

## Behaviour

- All new entries follow the standard exercise JSON schema (see any existing entry in `master_exercises.json` for field list).
- Every functional movement must include `tags` (at minimum `["functional"]`), `familyId`, `exerciseType`, `muscleGroup`, and `equipmentType`.
- Hyrox-specific movements get `tags: ["functional", "hyrox"]`. Calisthenics skills get `tags: ["functional", "calisthenics"]`.
- **`exerciseType` normalization (critical):** The research result files contain some entries using `BODYWEIGHT` or `WEIGHTED` — these are not valid schema values. Replace all occurrences with `STRENGTH` before saving. Valid values are: `STRENGTH`, `TIMED`, `CARDIO`, `PLYOMETRIC`.
- `MasterExerciseSeeder.CURRENT_VERSION` bumps from `"2.1"` → `"2.2"` (§6.3 seed bumps to 2.1; this is the follow-on).
- Seeder must NOT bump `updatedAt` on rows that haven't changed (prevents Firestore push storm).
- New entries with `exerciseType = TIMED` require `restDurationSeconds` set to a sensible default (e.g. 60s).
- For the 41 existing exercises in the retag list: update only the `tags` field — do not modify any other field on those rows.

### Tag taxonomy extension (additions to §6.1 canonical tags)

| Tag | Meaning |
|---|---|
| `"hyrox"` | Appears in Hyrox race format; subset of `"functional"` |
| `"calisthenics"` | Bodyweight skill or progression movement |

All `"hyrox"` and `"calisthenics"` tagged exercises must also carry `"functional"` so they surface in the Functional Picker.

---

## UI Changes

None beyond what `func_exercise_tags_seed` already introduces (Functional filter chip). The expanded exercise list is surfaced automatically through the same chip.

---

## Files to Touch

- `app/src/main/res/raw/master_exercises.json` — add all 61 net-new entries from both result files; retag 41 existing entries; normalize any BODYWEIGHT/WEIGHTED → STRENGTH
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — bump `CURRENT_VERSION` to `"2.2"`

---

## How to QA

1. Install APK on device. Open Exercise Library.
2. Toggle "Functional" chip → verify all Hyrox stations visible (SkiErg, Sled Push, Sled Pull, Burpee Broad Jump, Rowing, Farmer's Carry, Sandbag Lunge, Wall Ball Shot).
3. Search "planche" → Tuck Planche Hold, Advanced Tuck Planche Hold, Straddle Planche Hold, Full Planche Hold, Planche Push-Up all appear.
4. Search "front lever" → full progression family appears (Tuck → Advanced Tuck → One-Leg → Straddle → Full → Front Lever Row).
5. Search "air squat" → appears. Search "butterfly" → Butterfly Pull-Up appears. Search "man maker" → appears.
6. Search "zercher" → Zercher Squat appears. Search "tire" → Tire Flip appears.
7. Search "barbell back squat" → has functional tag (verify it appears when Functional chip is ON).
8. Verify no existing exercises lost their data (isFavorite, isCustom, syncId preserved).
9. Verify total exercise count increased by ~61 from the pre-seed count.
