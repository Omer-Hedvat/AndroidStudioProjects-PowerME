# Functional Blocks — Disable Supersets for Functional Exercises

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | XS |
| **Depends on** | func_active_block_card_ui ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/TemplateBuilderScreen.kt`, `ui/workout/ActiveWorkoutScreen.kt` |

---

## Overview

The superset grouping UI (long-press / swipe to pair exercises, superset badge on grouped rows) currently appears on all exercises regardless of block type. Supersets have no semantic meaning inside functional blocks (AMRAP, RFT, EMOM, TABATA) — these blocks are already a circuit by definition. Showing the superset option inside functional blocks is confusing and should be suppressed.

Note: per `FUNCTIONAL_TRAINING_SPEC.md §12 Invariant #2`, exercises in a STRENGTH block can still be supersetted. This change affects functional blocks only.

---

## Behaviour

- **Template builder:** The "Create Superset" action (long-press menu, swipe gesture, or inline chip) is hidden / disabled for exercise rows inside a functional block (AMRAP / RFT / EMOM / TABATA). It remains available for STRENGTH block rows.
- **Active workout:** Superset badges and the "Superset" pairing affordance are hidden for exercises inside functional blocks. STRENGTH block exercises are unaffected.
- Existing `RoutineExercise` or `WorkoutSet` rows that already have a `supersetGroupId` assigned and belong to a functional block: the `supersetGroupId` is preserved in the DB (no migration needed) but the superset UI is not rendered. On the next template edit, the user cannot create new supersets in functional blocks but existing data is not corrupted.

---

## UI Changes

- In `TemplateBuilderScreen`, pass `blockType` down to the exercise row composable. Conditionally hide the superset action when `blockType != STRENGTH`.
- In `ActiveWorkoutScreen`, similarly gate the superset badge and pairing affordance on `blockType`.
- No new composables required — this is a conditional render guard on existing UI elements.

---

## Files to Touch

- `ui/workouts/TemplateBuilderScreen.kt` — hide superset action for functional block rows
- `ui/workout/ActiveWorkoutScreen.kt` — hide superset badge for functional block exercises

---

## How to QA

1. Open template builder for a Hybrid routine. Long-press an exercise in an AMRAP block → confirm no "Create Superset" option appears.
2. Long-press an exercise in the STRENGTH block of the same routine → confirm "Create Superset" option is still available.
3. In an active workout with a functional block, confirm no superset badge renders on functional exercises.
