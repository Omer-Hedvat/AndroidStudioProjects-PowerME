# Functional Block Exercises — Weight Targets & Lock During Workout

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `in-progress` |
| **Effort** | M |
| **Depends on** | func_active_block_card_ui ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/TemplateBuilderScreen.kt`, `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`, `data/database/RoutineExercise.kt`, `data/database/WorkoutSet.kt` |

---

## Overview

Functional block exercises currently have no weight field — athletes cannot specify target loads. This feature adds a weight-target input in the template builder (editable before starting the workout) and locks it once the workout starts. Post-workout, users can adjust the stored weight from the workout summary / edit mode.

---

## Behaviour

### Template builder
- Each exercise row in a functional block shows a **Weight** input (kg/lb respecting user unit pref).
- If weight = 0 / blank, display placeholder "BW" for bodyweight.
- Weight is stored on `RoutineExercise.targetWeight` (already exists in the schema).

### Active workout
- The weight field is **read-only** during the workout — displayed as a static chip/label, not an editable field.
- "Weight locked" — no tap-to-edit, no keyboard.
- The value shown is the target weight from the template; it does NOT auto-update from set input.

### Post-workout
- In the Workout Summary or History edit mode, the weight for functional block exercises can be edited (same flow as editing a regular set's weight).

---

## UI Changes

- Template builder functional exercise row: add `OutlinedTextField` (numeric, suffix kg/lb) after the reps/hold-seconds field.
- Active workout functional card: render weight as a `SuggestionChip` (non-clickable) next to the exercise name.

---

## Files to Touch

- `ui/workouts/TemplateBuilderScreen.kt` — add weight input to `FunctionalExerciseRow`
- `ui/workout/ActiveWorkoutScreen.kt` — render weight label in functional block card
- `ui/workout/WorkoutViewModel.kt` — carry `targetWeight` from `RoutineExercise` into `ExerciseWithSets`
- `data/database/RoutineExercise.kt` — verify `targetWeight` column exists
- `data/database/WorkoutSet.kt` — store confirmed weight on finish (if applicable)

---

## How to QA

1. Template builder → add a functional block → add exercises → set weight on one exercise (e.g. 50 kg).
2. Start the workout. Confirm the weight is shown as a read-only label, not an editable field.
3. Finish the block. Open the workout summary. Confirm the weight is shown and can be edited.
