# Functional Training — Exercise Gap Analysis (CrossFit / Hyrox / Calisthenics)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | func_exercise_expanded_seed |
| **Touches** | No code changes — research output only |

---

## Overview

The current `FUNCTIONAL_TRAINING_SPEC.md §6.3` defines ~40 functional movements targeting CrossFit staples. Before seeding the expanded exercise list, this task audits what is actually missing for full coverage across three disciplines:

1. **CrossFit** — benchmark WOD movements ("The Girls", "The Heroes", Open workouts)
2. **Hyrox** — the 8 official Hyrox stations + common Hyrox training accessories
3. **Calisthenics** — bodyweight skill progressions (rings, bar, floor) beyond what §6.3 already lists

The deliverable is a clean, prioritised list of missing exercises with their `tags`, `familyId`, `exerciseType`, `muscleGroup`, and `equipmentType` fields pre-filled, ready to be pasted into `master_exercises.json` in the next task (`func_exercise_expanded_seed`).

---

## Behaviour

- **No code changes.** This is a research-only task.
- Spawn a web agent to survey:
  - CrossFit benchmark WODs (crossfit.com/wod, Beyond the Whiteboard exercise index)
  - Official Hyrox race format (hyrox.com/race) — 8 stations: SkiErg, Sled Push, Sled Pull, Burpee Broad Jump, Rowing, Farmer's Carry, Sandbag Lunges, Wall Balls
  - Calisthenics progressions: ring work, bar work, floor gymnastics, L-sit/planche/front lever families
- Cross-reference every found movement against the existing 240 exercises in `app/src/main/res/raw/master_exercises.json` and the §6.3 planned additions.
- Output: a structured list of **net-new** movements not yet covered, grouped by discipline (CrossFit / Hyrox / Calisthenics), with all JSON fields pre-filled.
- Flag any existing exercises that need a `tags` addition only (no new entry needed, just a retag in §6.3).

---

## UI Changes

None.

---

## Files to Touch

- No source files.
- Research output is saved to `future_devs/FUNC_EXERCISE_GAP_ANALYSIS_RESULTS.md` for handoff to `func_exercise_expanded_seed`.

---

## How to QA

1. Open `future_devs/FUNC_EXERCISE_GAP_ANALYSIS_RESULTS.md` after the agent run.
2. Verify all 8 Hyrox stations appear in the results (either as existing exercises that need retagging, or as new entries).
3. Verify at least 10 CrossFit benchmark WOD movements beyond §6.3 are identified.
4. Verify calisthenics progressions beyond what §6.3 lists (Planche, Front Lever, Back Lever, Ring work families) are represented.
