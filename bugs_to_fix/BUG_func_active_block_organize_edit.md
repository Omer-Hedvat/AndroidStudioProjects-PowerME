# BUG: Functional block cards in active workout have no edit/delete; organize mode ignores block structure

## Status
[x] Fixed

## Severity
P1 high
- Users cannot correct a wrongly-configured block (e.g. wrong time cap or round count) once a workout has started
- Organize mode flattens functional exercises into the strength list, enabling invalid supersets and broken reorder behaviour in hybrid workouts

## Description

Three related gaps in the active workout screen for functional blocks:

### 1. No edit or delete controls on functional block cards
The functional `FunctionalBlockActiveCard` shows a block badge and exercise rows but has no way to:
- Edit block parameters (time cap, round count, work/rest intervals) — there is no long-press menu or edit icon
- Delete the block from the active workout

Strength exercises have a long-press context menu (edit, delete, move). Functional blocks need equivalent actions scoped to the whole block (not per-exercise).

### 2. Organize mode does not understand block grouping
When the user taps "Organize exercises" inside one of the strength exercise rows in a hybrid workout, the organize mode presents every exercise as a flat draggable row — including the exercises that belong to a functional block. This is wrong:
- The entire functional block (all its exercises) must move as a single atomic unit — a block-level drag handle, not per-exercise handles inside the block
- Strength exercises inside a STRENGTH block keep individual drag handles within their block group
- Unblocked strength exercises remain individually reorderable

### 3. Invalid superset paths in hybrid organize mode
In the current organize mode, the user can attempt to superset a functional block exercise with a strength exercise (or vice versa). The rules should be:
- Functional blocks cannot be supersetted with anything
- Strength exercises can only be supersetted with other strength exercises in the same (implicit) group
- The UI must not surface a "Superset" option when a functional block is involved

## Steps to Reproduce
1. Start a HYBRID workout (has at least 1 functional block + 1 strength exercise).
2. Look at the functional block card — observe: no edit icon, no delete option, no long-press menu.
3. Long-press a strength exercise → tap "Organize" → observe: functional block exercises appear as individual draggable rows in the flat list, interleaved with strength exercises.
4. In organize mode, attempt to superset a strength exercise with a functional block exercise — observe: the action is not blocked.

## Dependencies
- **Depends on:** func_active_block_card_ui ✅
- **Blocks:** FUNC_TEMPLATE_BUILDER_POLISH_SPEC (reorder UX should be consistent)
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`, `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes

**Fix 1 — Edit/delete controls on FunctionalBlockActiveCard:**
- Added `onDeleteBlock` and `onEditBlock` parameters to `FunctionalBlockActiveCard`.
- Edit button (pencil icon) only visible before block has started; opens `EditBlockParamsSheet` — a new `ModalBottomSheet` with type-specific fields (AMRAP: duration, RFT: rounds, EMOM: interval + total, TABATA: work/rest seconds).
- Delete button (trash icon) always visible; opens `AlertDialog` for confirmation, then calls `viewModel.removeBlock(blockId)`.
- New ViewModel methods: `removeBlock(blockId)` removes all exercises with that blockId + the block entry; `updateBlock(blockId, ...)` patches the block's plan fields in-state.

**Fix 2 — Block-level drag handle in organize mode:**
- `activeWorkoutListItems` now detects `hasFunctionalBlocks` and branches: when in organize mode AND functional blocks exist, builds a mixed-item list where each functional block is a single `ReorderableItem` (key `"org_block_$blockId"`) using the new `FunctionalBlockOrganizeRow` composable, and strength/unblocked exercises remain individual `SupersetSelectRow` items.
- Non-first exercises of functional blocks are skipped in the LazyList (they're represented by the block row).
- The `rememberReorderableLazyListState` callback now calls `viewModel.reorderOrganizeItem(from.key, to.key)`, which handles Long/Long (exercise swap), String/Long (block→exercise), and Long/String (exercise→block) key pairs. Blocks move atomically — all their exercises relocate together, with direction inferred from source vs target position.

**Fix 3 — Suppress superset for functional block exercises:**
- `toggleSupersetCandidate` silently returns if the target exercise belongs to a non-STRENGTH block.
- Fix 2 naturally prevents functional block exercises from appearing as selectable checkboxes in organize mode.

**Tests added (5):** `removeBlock removes all exercises and the block`, `updateBlock updates plan params on target block only`, `reorderOrganizeItem with two Long keys performs adjacent swap`, `reorderOrganizeItem with block key moves entire block atomically`, `toggleSupersetCandidate silently ignores functional block exercises`.
