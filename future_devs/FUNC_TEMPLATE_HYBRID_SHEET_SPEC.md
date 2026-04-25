# Functional Training — Hybrid Add-Block-or-Exercise Sheet

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | func_template_wizard ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/AddBlockOrExerciseSheet.kt` (new), `ui/workouts/TemplateBuilderScreen.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §8.1` — read before touching any file in this task.

---

## Overview

When `workoutStyle == HYBRID`, the "+" Add button in the Template Builder should open a two-option bottom sheet ("Add Strength Exercise" / "Add Functional Block") rather than going directly to either flow. This is the Tier 3 companion to `func_template_wizard`.

---

## Behaviour

- `AddBlockOrExerciseSheet` is a simple 24dp-radius bottom sheet with two full-width action items.
- "Add Strength Exercise" → `navController.navigate("exercise_picker")` (unchanged legacy path).
- "Add Functional Block" → opens `FunctionalBlockWizard` bottom sheet.
- When `workoutStyle == PURE_GYM`: Add button navigates directly to exercise picker (no sheet).
- When `workoutStyle == PURE_FUNCTIONAL`: Add button opens `FunctionalBlockWizard` directly (no sheet).
- When `workoutStyle == HYBRID`: Add button opens `AddBlockOrExerciseSheet`.

The dispatch lives in `TemplateBuilderScreen` — a single `when (workoutStyle)` branch on the Add button click handler.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workouts/AddBlockOrExerciseSheet.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderScreen.kt` — Add button dispatch

---

## How to QA

1. Set WorkoutStyle = Hybrid. Open a routine template. Tap "+". Verify the two-option sheet appears.
2. Tap "Add Strength Exercise" → verify it navigates to the regular exercise picker.
3. Tap "Add Functional Block" → verify `FunctionalBlockWizard` opens.
4. Set WorkoutStyle = Pure Gym. Tap "+". Verify direct navigation to exercise picker (no sheet).
5. Set WorkoutStyle = Pure Functional. Tap "+". Verify direct wizard opening (no sheet).
