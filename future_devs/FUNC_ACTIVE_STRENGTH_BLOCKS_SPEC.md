# Functional Training — Active Workout Block Headers (Strength)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | func_block_entities_migration ✅ |
| **Blocks** | func_active_functional_runner |
| **Touches** | `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §9` (shared chassis only — functional runner is the next task) — read before touching any file in this task.

---

## Overview

Updates the active workout `LazyColumn` (`activeWorkoutListItems`) to group exercises under block headers. `WorkoutViewModel.startWorkout*` materialises `WorkoutBlock` rows from the template at workout start.

Functional blocks show a header with a disabled "▶ Coming soon" CTA (the runner is implemented in the next task). STRENGTH blocks are completely unchanged unless the workout is a Hybrid (≥2 mixed-type blocks) — in that case a minimal STRENGTH block header is shown for clarity.

This task is intentionally narrow: **no new overlays, no timers, no functional UI.** It surfaces any `activeWorkoutListItems` regressions before the overlay layer is added.

---

## Behaviour

- `WorkoutViewModel.startWorkoutFromRoutine` / `startWorkout` / `startWorkoutFromPlan` each materialise one `WorkoutBlock` row per `RoutineBlock` at start of workout (Iron Vault pattern — persisted up front).
- `ActiveWorkoutState` gains `blocks: List<WorkoutBlock>` and `activeBlockId: String?`.
- `activeWorkoutListItems` inserts a `BlockHeader` item before each block's exercise group.
- `BlockHeader` shows: type badge (STRENGTH / AMRAP / RFT / EMOM), block name, parameters summary (e.g. "12 min cap"), and for functional blocks: a disabled "▶ START BLOCK" button (greyed, `"Coming soon"` tooltip).
- A workout with a single STRENGTH block (all existing users) shows **no block header** (backward-compatible visual).
- `WorkoutEditSnapshot` is extended to capture `blocks: List<WorkoutBlock>` for cancel-edit restore.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — `BlockHeader` composable, `activeWorkoutListItems` grouping
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — `startWorkout*` block materialisation, `ActiveWorkoutState.blocks`, `WorkoutEditSnapshot` extension

---

## How to QA

1. Start a legacy (STRENGTH-only) workout from an existing routine. Verify it looks exactly the same as before — no block header, no regressions in set logging, rest timers, superset mode, edit mode.
2. Create a Hybrid routine (1 STRENGTH block + 1 AMRAP block). Start the workout. Verify two block headers appear: "STRENGTH" and "AMRAP 12min".
3. Tap the "▶ START BLOCK" on the AMRAP block header. Verify a "Coming soon" tooltip or disabled state (no crash).
4. Run `WorkoutViewModelTest`: multi-block routine start produces correct `WorkoutBlock` rows in the DB.
5. Enter edit mode; reorder an exercise; cancel edit. Verify block structure is fully restored.
