# Functional Training — Functional Block Wizard (Template Builder)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | L |
| **Depends on** | func_block_entities_migration ✅, func_style_preference ✅, func_exercise_tags_seed ✅ |
| **Blocks** | func_template_hybrid_sheet |
| **Touches** | `ui/workouts/FunctionalBlockWizard.kt` (new), `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt`, `data/repository/RoutineRepository.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §8` — read before touching any file in this task.

---

## Overview

Adds the `FunctionalBlockWizard` bottom sheet (3 steps: block type → parameters → exercise selection) and updates `TemplateBuilderScreen` to render exercises grouped by block with a `BlockHeader` item per block.

When `workoutStyle == PURE_FUNCTIONAL`, the existing "+" Add button in the template builder opens this wizard directly. When `workoutStyle == HYBRID`, this wizard is one branch of the `AddBlockOrExerciseSheet` (next task).

---

## Behaviour

**Wizard Step 1 — Block type:** Four large tappable tiles: AMRAP, RFT, EMOM, TABATA. Each shows a one-line description.

**Wizard Step 2 — Parameters:**
- AMRAP: minutes wheel (1–60 min). Auto-name: "AMRAP 12min".
- RFT: rounds stepper (1–20) + optional "Time cap [mm:ss]" field (empty = no cap; stored as `durationSeconds`). Auto-name: "5RFT". When cap is set, appended: "5RFT / 25min cap".
- EMOM: total duration (minutes) + interval-length preset chips `[60s (EMOM)] [90s] [2min] [3min] [5min]` + "Other [mm:ss]" custom field. Default 60s. Auto-name reflects interval: "EMOM 10min", "E2MOM 10min", "E3MOM 15min".
- TABATA: Work stepper (default 20s) + Rest stepper (default 10s) + Rounds stepper (default 8) + "Skip last rest" switch (default off). Auto-name: "Tabata 8rds". Defaults match Clocks Tabata (`ToolsUiState` in `ToolsViewModel.kt:56–71`).
- Block name field (optional, auto-filled from above).
- **Advanced (collapsible row):** "Setup seconds [N]" + "Warn at [N]s". Blank = use settings defaults from `timedSetSetupSeconds` + `resolveWarnAt`. Stored as `setupSecondsOverride?` and `warnAtSecondsOverride?` on `RoutineBlock`.

**Wizard Step 3 — Add exercises:** Multi-select `ExercisesScreen` entered with `functionalFilter = true` pre-set (Functional chip ON by default). User can toggle the chip off to access any exercise including Back Squat, Bench Press, Treadmill, etc. Grouped by `familyId`. Selected exercises become `RoutineExercise` rows with `blockId` pointing at the new `RoutineBlock`.

After selection, each exercise row in the builder shows a `[Reps] [Time]` segmented toggle (right-aligned, `bodySmall`, 32dp height):
- **AMRAP / RFT blocks only.** Default: `[Reps]` for all `exerciseType` except `TIMED` (which defaults to `[Time]` with `holdSeconds = Exercise.restDurationSeconds ?: 30`). Switching modes with a value entered triggers a confirm dialog.
  - `[Reps]` → qty stepper; saves `RoutineExercise.reps`, clears `holdSeconds`.
  - `[Time]` → `[mm:ss]` picker; saves `RoutineExercise.holdSeconds`, ignores `reps`.
- **EMOM / TABATA blocks.** Toggle is hidden; only the reps stepper is shown. `holdSeconds` is always `null`. If an exercise row is drag-copied from AMRAP/RFT into EMOM/TABATA, its `holdSeconds` is cleared silently.

**Template Builder layout:** The exercise list is now block-sectioned. Each `BlockHeader` shows: block name + type badge + parameters summary + overflow menu ("Add exercise to block", "Delete block"). Per-exercise rows are reorderable within a block. Blocks themselves can be reordered via a block-level drag handle.

STRENGTH blocks (legacy) use an unnamed header only when the workout has ≥2 blocks of mixed types. A single STRENGTH block = no header (backward-compatible visual).

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workouts/FunctionalBlockWizard.kt` (NEW) — 3-step bottom sheet
- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderScreen.kt` — block-sectioned list, BlockHeader composable
- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderViewModel.kt` — block CRUD: `addFunctionalBlock`, `deleteBlock`, `reorderBlock`, `addExerciseToBlock`
- `app/src/main/java/com/powerme/app/data/repository/RoutineRepository.kt` — block CRUD helpers

---

## How to QA

1. Set WorkoutStyle = Pure Functional. Open a routine. Tap "+". Verify wizard opens (not exercise picker).
2. Create an AMRAP block (12min, 3 exercises). Verify exercises appear under a "AMRAP 12min" block header in the builder.
3. Create an RFT block (5 rounds, 25min cap). Verify auto-name shows cap.
4. Create an EMOM block (4 rounds, 2-minute interval). Wizard shows "(8min total)". Verify auto-name shows "E2MOM 8min".
5. Create a TABATA block (defaults). Verify auto-name "Tabata 8rds".
6. In Step 3 picker: toggle Functional chip OFF. Verify Back Squat / Bench Press appear. Toggle ON. Verify filtered back.
7. Create second block. Verify two block headers appear; exercises reorderable within each block.
8. Delete a block. Verify exercises are removed. Undo is not available (confirm dialog fires before delete).
9. Save the routine. Reopen it; verify block structure persisted.
10. Set WorkoutStyle = Pure Gym. Open a different routine; tap "+". Verify the wizard does NOT open (legacy exercise picker instead).
11. In an AMRAP block Step 3: add "Double Unders" (PLYOMETRIC). Verify `[Reps]` is the default. Switch to `[Time]`, enter 1:00. Verify recipe row shows "1min Double Unders". Switch back to `[Reps]` — confirm dialog fires. Confirm. Verify recipe row shows "50 × Double Unders".
12. In an EMOM block Step 3: add any exercise. Verify the `[Reps] [Time]` toggle is absent. Verify `holdSeconds = null` is saved.
13. Drag-copy a time-capped row from an AMRAP block into an EMOM block. Verify `holdSeconds` is cleared to `null` and the row shows reps.
