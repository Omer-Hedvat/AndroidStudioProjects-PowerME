# BUG: Functional block template builder — no weight input + blocks not reorderable

## Status
[x] Fixed

## Severity
P1 high

## Description

Two related issues in the functional block template builder:

**Scope 1 — No weight input for functional block exercises:**
When adding exercises to a functional block in the template builder, `FunctionalExerciseRow` only shows a reps/time field — there is no weight input. Users cannot pre-set a target weight for kettlebell, barbell, or dumbbell functional exercises before starting the workout.

**Scope 2 — Functional blocks cannot be reordered:**
In the template builder (and/or organize mode), functional blocks cannot be dragged to change their order relative to other blocks or exercises. The drag-to-reorder affordance either does not appear or does not work for block rows.

## Steps to Reproduce

**Scope 1:**
1. Create or edit a workout template with a PURE_FUNCTIONAL or HYBRID style.
2. Add a functional block (e.g. AMRAP) and add an exercise to it.
3. Observe: the exercise row has no weight field — only reps/time.

**Scope 2:**
1. Create a workout template with two or more functional blocks.
2. Attempt to drag/reorder the blocks.
3. Observe: drag handles are absent or dragging has no effect.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
**Scope 1 (weight input):** Added `defaultWeight: String` to `DraftExercise` and `RoutineExerciseWithName` (+ SQL query). Added `updateFunctionalWeight()` to `TemplateBuilderViewModel` and wired `defaultWeight` through `save()`. Added a compact `OutlinedTextField` (width 88dp, decimal keyboard) to `FunctionalExerciseRow`.

**Scope 2 (block reordering):** Updated `reorderState` callback to handle `"block-card-*"` String keys in addition to exercise Long keys, calling `viewModel.reorderBlocks()`. Wrapped functional block cards in `ReorderableItem`. Added a `DragHandle` icon to `BlockHeader` via optional `dragHandleModifier` param.
