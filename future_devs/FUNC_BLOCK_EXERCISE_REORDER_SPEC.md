# Functional Block — Exercise Reorder in Template Builder

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | S |
| **Depends on** | func_block_card_layout (`not-started`) |
| **Blocks** | — |
| **Touches** | `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt`, `data/repository/RoutineRepository.kt` |

---

## Overview

Exercises within a functional block in `TemplateBuilderScreen` currently have no reorder capability. The spec (§8.3) states "reordering allowed within a block," but this was not implemented. Users need to be able to drag exercises up/down within a functional block, and also reorder entire blocks within the routine — matching the UX available for strength exercises today.

---

## Behaviour

### Exercise reorder within a block

- Each functional exercise row inside a block section gets a drag handle on the right (same `DragHandle` icon used for strength exercise reorder).
- Dragging a row moves it only within its block. Cross-block drag is **not** supported in v1 — the drag is constrained to the block's section boundary.
- On drop, `RoutineExercise.order` values are updated for all exercises in the block. Persisted via `RoutineRepository.reorderExercisesInBlock(blockId, newOrder)`.
- Reorder uses `sh.calvin.reorderable:reorderable-compose` (already in project) with a `ReorderableLazyColumn` scoped to each block section.

### Block reorder within the routine

- Each `BlockHeader` gets a drag handle icon on its left edge.
- Dragging a block header moves the entire block (header + all its exercise rows) to a new position in the routine.
- On drop, `RoutineBlock.order` values are updated and persisted via `RoutineRepository.reorderBlocks(routineId, newOrder)`.
- A STRENGTH block can be reordered relative to functional blocks in the same routine.

---

## UI Changes

- Add `DragHandle` icon (trailing end) to functional exercise rows — same composable used for strength rows.
- Add `DragHandle` icon (leading end) to `BlockHeader` composable to signal block-level drag.
- During drag: row/block lifts with `4dp` shadow elevation (same pattern as strength reorder).
- Drag handles hidden when the template is opened in read-only preview mode (if applicable).

---

## Files to Touch

- `ui/workouts/TemplateBuilderScreen.kt` — wire drag handles on functional exercise rows and block headers
- `ui/workouts/TemplateBuilderViewModel.kt` — expose `reorderExercisesInBlock()` and `reorderBlocks()` actions
- `data/repository/RoutineRepository.kt` — implement `reorderExercisesInBlock()` and `reorderBlocks()`

---

## How to QA

1. Open a routine with a functional block containing ≥3 exercises.
2. Long-press drag the second exercise to the first position → confirm order updates and persists after leaving and reopening the screen.
3. Open a Hybrid routine with ≥2 blocks. Drag the second block above the first → confirm block order updates.
4. Confirm strength block exercise reorder still works (no regression).
