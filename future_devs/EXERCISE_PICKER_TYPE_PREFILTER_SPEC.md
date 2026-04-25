# Exercise Picker — ExerciseType Pre-filter by Entry Point

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `completed` |
| **Effort** | S |
| **Depends on** | func_template_hybrid_sheet ✅ |
| **Blocks** | exercise_picker_ui_consistency |
| **Touches** | `ui/exercises/ExercisesScreen.kt`, `ui/exercises/ExercisesViewModel.kt`, `navigation/PowerMeNavigation.kt`, `ui/workouts/AddBlockOrExerciseSheet.kt`, `ui/workouts/TemplateBuilderScreen.kt` |

---

## Overview

When the user opens the exercise picker from different entry points, the list should be pre-filtered to the exercise types that are relevant to that context. Currently "Add Strength Exercise" shows all exercises with no type filter, and "Add Functional Block" only filters to the `functional` tag (not Cardio or Plyometric). This creates noise — a functional block picker showing Barbell Bench Press, or a strength picker showing Box Jumps, is confusing.

---

## Behaviour

**Entry point → initial ExerciseType filter chips active:**

| Entry Point | Initial type filters active |
|---|---|
| Hybrid sheet "Add Strength Exercise" | `STRENGTH`, `TIMED` |
| Hybrid sheet "Add Functional Block" | functional tag filter ON + `CARDIO`, `PLYOMETRIC` types |
| PURE_GYM "Add Exercises" | No type filter (all types, current behaviour) |
| PURE_FUNCTIONAL "Add Block" → exercises | functional tag filter ON + `CARDIO`, `PLYOMETRIC` types |
| Template builder "Add exercise to block" overflow | functional tag filter ON + `CARDIO`, `PLYOMETRIC` types |

**Rules:**
- Pre-filters are additive initial state only — the user can freely toggle any chip off/on after opening.
- The functional tag filter (`initialFunctionalFilter`) remains the dedicated boolean it is today; ExerciseType filters are a separate set.
- `ExercisesScreen` gains a new parameter `initialTypeFilters: Set<ExerciseType> = emptySet()` used alongside `initialFunctionalFilter`.
- The `exercise_picker` navigation route gains an optional `typeFilters` query param (comma-separated enum names, e.g. `typeFilters=STRENGTH,TIMED`).
- `ExercisesViewModel` applies `initialTypeFilters` on first load (same pattern as `initialFunctionalFilter`).
- ActiveWorkoutScreen inline ModalBottomSheet pickers are unaffected — they always show all types.

---

## UI Changes

- No new UI components needed — the existing ExerciseType filter chips in `ExercisesScreen` are used.
- Chips matching the initial filter set start in a toggled-on state.
- Behaviour after toggle is identical to a user manually tapping chips.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — add `initialTypeFilters: Set<ExerciseType>` parameter; apply via `LaunchedEffect` on first composition (same pattern as `initialFunctionalFilter`)
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — expose a way to set initial type filters without overwriting user-toggled state
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` — extend `exercise_picker` route to parse optional `typeFilters` query param and pass to `ExercisesScreen`
- `app/src/main/java/com/powerme/app/ui/workouts/AddBlockOrExerciseSheet.kt` — pass different `typeFilters` to nav for each button
- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderScreen.kt` — pass `typeFilters` when navigating to exercise picker from functional block overflow and PURE_FUNCTIONAL "Add Block" flow

---

## How to QA

1. **Hybrid — Add Strength Exercise:** Set style to Hybrid → open template builder → tap "Add Exercise or Block" → tap "Add Strength Exercise". Verify picker opens with Strength + Timed filter chips active; other type chips (Cardio, Plyometric, Stretch) are off.
2. **Hybrid — Add Functional Block:** Same path → tap "Add Functional Block" → complete wizard → reach exercise picker. Verify functional tag filter AND Cardio + Plyometric chips are active; Strength/Timed chips are off.
3. **PURE_GYM — Add Exercises:** Set style to Pure Gym → tap "+". Verify picker opens with no type chips pre-selected (current behaviour unchanged).
4. **PURE_FUNCTIONAL — Add Block:** Set style to Pure Functional → tap "+" → complete wizard. Verify functional filter + Cardio + Plyometric chips active.
5. **Block overflow — Add exercise to block:** In a PURE_FUNCTIONAL or HYBRID routine with an existing block → tap block overflow → "Add exercise to block". Verify functional + Cardio + Plyometric chips active.
6. **User can toggle:** In any of the above entry points, tap a pre-selected chip to deactivate it. Verify the list updates accordingly.
