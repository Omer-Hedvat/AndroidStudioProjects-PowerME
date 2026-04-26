# Functional Template Builder Polish — Block View, Weights, Reorder, Edit, Supersets

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | M |
| **Depends on** | func_active_block_card_ui ✅, func_block_card_layout ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt`, `ui/workouts/FunctionalBlockWizard.kt`, `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`, `ui/workouts/WorkoutsScreen.kt`, `data/database/RoutineExercise.kt`, `data/database/PowerMeDatabase.kt`, `data/repository/RoutineRepository.kt`, `DB_UPGRADE.md` |

---

## Overview

Five related gaps in the functional block template-builder experience, consolidated into a single session because they share the same files:

1. **Routine detail/preview shows a flat exercise list** — functional blocks look identical to strength routines; no block type badge, parameters, or grouped card structure.
2. **No weight target input** — exercises inside functional blocks have no way to specify a prescribed load (e.g. "21 Thrusters @ 95 lb"). Active workout card also lacks "Weight" / "Reps" column headers.
3. **No reorder capability** — exercises cannot be dragged within a block; blocks cannot be reordered within the routine.
4. **No way to edit a block's parameters after creation** — once a functional block is created via the wizard, there is no "Edit block" action to change its type, duration, rounds, or time cap.
5. **Superset UI shown inside functional blocks** — supersets have no semantic meaning in AMRAP/RFT/EMOM/TABATA blocks; the create-superset affordance should be hidden there while remaining available in STRENGTH blocks.

---

## Scope 1 — Routine detail/preview: functional block structure

### Behaviour
- When the user taps a routine card in the Workouts tab, the routine detail/preview composable renders functional blocks as grouped cards (same visual treatment as `TemplateBuilderScreen`): colour-coded block-type badge + parameters (duration / rounds / time cap) at the top, exercises listed below.
- STRENGTH blocks / unblocked exercises keep the existing per-exercise card.
- The edit button on a functional block card in this view should navigate into `TemplateBuilderScreen` focused on that block.

### Files
- `ui/workouts/WorkoutsScreen.kt` — routine detail/preview composable
- `ui/workout/WorkoutViewModel.kt` — ensure blocks are loaded for preview

---

## Scope 2 — Weight target input + active workout column headers

### Template builder
- Each functional exercise row in `TemplateBuilderScreen` and in `FunctionalBlockWizard` (step 3) shows a **Weight** input (kg/lb, respects unit pref).
- Blank / 0 → display placeholder "BW" (bodyweight).
- Weight stored in a new nullable `targetWeightKg: Double?` column on `RoutineExercise` (requires DB migration — bump version and add migration in `PowerMeDatabase.kt`; record in `DB_UPGRADE.md`).
- Use `SurgicalValidator.validateWeight()` for validation; `OutlinedTextField` with `keyboardType = Decimal`.

### Active workout
- `FunctionalBlockActiveCard` gains a `Row` header with `Text("Weight", style=labelSmall)` and `Text("Reps", style=labelSmall)` (or "Hold" for time-capped rows) aligned above their respective input fields, matching strength card column headers.
- `targetWeightKg` is copied from `RoutineExercise` → `WorkoutSet.weight` as a suggestion when the workout starts (user can still edit it during the workout).

### Files
- `ui/workouts/TemplateBuilderScreen.kt` — weight input on functional exercise rows
- `ui/workouts/TemplateBuilderViewModel.kt` — persist `targetWeightKg` on save
- `ui/workout/ActiveWorkoutScreen.kt` — column header row in `FunctionalBlockActiveCard`; carry `targetWeightKg` into set suggestion
- `data/database/RoutineExercise.kt` — add `targetWeightKg: Double?` column
- `data/database/PowerMeDatabase.kt` — bump version + migration
- `DB_UPGRADE.md` — record schema change

---

## Scope 3 — Exercise reorder within blocks + block reorder

### Exercise reorder within a block
- Each functional exercise row inside a block section gets a `DragHandle` icon (trailing end) — same composable used for strength exercise reorder.
- Dragging moves the row only within its block (cross-block drag not supported in v1).
- On drop, `RoutineExercise.order` values updated and persisted via `RoutineRepository.reorderExercisesInBlock(blockId, newOrder)`.
- Uses `sh.calvin.reorderable:reorderable-compose` (already in project).

### Block reorder within the routine
- Each `BlockHeader` gets a `DragHandle` icon (leading end).
- Dragging a block header moves the entire block (header + all exercise rows) to a new position.
- On drop, `RoutineBlock.order` updated and persisted via `RoutineRepository.reorderBlocks(routineId, newOrder)`.
- A STRENGTH block can be reordered relative to functional blocks in the same routine.

### UI during drag
- Row/block lifts with 4dp shadow elevation (same pattern as strength reorder).
- Drag handles hidden in read-only preview mode.

### Files
- `ui/workouts/TemplateBuilderScreen.kt` — wire drag handles on functional exercise rows and block headers
- `ui/workouts/TemplateBuilderViewModel.kt` — expose `reorderExercisesInBlock()` and `reorderBlocks()`
- `data/repository/RoutineRepository.kt` — implement `reorderExercisesInBlock()` and `reorderBlocks()`

---

## Scope 4 — Edit block parameters after creation

### Behaviour
- A functional block's `BlockHeader` overflow menu (or a tap on the header itself) offers an **Edit block** action.
- Tapping it re-opens `FunctionalBlockWizard` with the current block parameters pre-filled (type, duration, rounds, time cap).
- Saving updates the existing block in-place; cancelling leaves it unchanged.

### Files
- `ui/workouts/TemplateBuilderScreen.kt` — wire "Edit block" in BlockHeader overflow
- `ui/workouts/TemplateBuilderViewModel.kt` — expose `editBlock(blockId)` → `updateBlock()` actions
- `ui/workouts/FunctionalBlockWizard.kt` — accept pre-filled `DraftBlock` for edit mode

---

## Scope 5 — Disable supersets for functional exercises

### Behaviour
- **Template builder:** the "Create Superset" action (long-press menu, swipe, or inline chip) is hidden for exercise rows inside a functional block (AMRAP / RFT / EMOM / TABATA). Remains available for STRENGTH block rows.
- **Active workout:** superset badges and the pairing affordance are hidden for exercises inside functional blocks. STRENGTH block exercises are unaffected.
- Existing `supersetGroupId` values in the DB are preserved — only the UI is suppressed.

### Files
- `ui/workouts/TemplateBuilderScreen.kt` — gate superset action on `blockType != STRENGTH`
- `ui/workout/ActiveWorkoutScreen.kt` — gate superset badge on `blockType != STRENGTH`

---

## How to QA

### Scope 1
1. Create a PURE_FUNCTIONAL routine with one AMRAP and one EMOM block.
2. Return to Workouts tab and tap the routine card.
3. Confirm the preview shows grouped block cards with type badges + parameters, not a flat list.

### Scope 2
1. Open a routine with an RFT block in template builder → confirm each exercise row shows a weight input.
2. Enter a target weight (e.g. 43 kg for Thruster) → save → reopen → confirm it persisted.
3. Start a workout → in the active workout functional block card, confirm "Weight" and "Reps" column headers appear and the weight is pre-filled.

### Scope 3
1. Open a routine with a functional block containing ≥3 exercises.
2. Long-press drag the second exercise to the first position → confirm order updates and persists after reopening.
3. Open a Hybrid routine with ≥2 blocks. Drag the second block above the first → confirm block order updates.
4. Confirm strength block exercise reorder still works (no regression).

### Scope 4
1. Create an AMRAP block (12 min). Open the BlockHeader overflow → confirm "Edit block" appears.
2. Tap it → wizard opens pre-filled with 12 min. Change to 15 min → save.
3. Confirm the block now shows 15 min. Confirm Cancel leaves it at 12 min.

### Scope 5
1. Open template builder for a Hybrid routine. Long-press an exercise in an AMRAP block → confirm no "Create Superset" option.
2. Long-press an exercise in the STRENGTH block of the same routine → confirm "Create Superset" is still available.
3. In an active workout with a functional block, confirm no superset badge renders on functional exercises.
