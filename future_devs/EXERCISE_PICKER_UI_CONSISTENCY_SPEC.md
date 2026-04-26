# Exercise Picker UI Consistency

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | func_template_wizard ✅ |
| **Blocks** | — |
| **Touches** | `ui/exercises/ExercisesScreen.kt`, `ui/workouts/TemplateBuilderScreen.kt`, `navigation/PowerMeNavigation.kt`, `ui/workout/ActiveWorkoutScreen.kt`, `ui/workouts/ai/AiWorkoutGenerationScreen.kt` |

---

## Overview

The app currently has multiple entry points that navigate to the exercise picker, but each entry point results in a subtly or significantly different UI. The best-looking and most functional version is the picker that opens when adding exercises to a functional block. This task standardises all picker entry points to use that same UI.

---

## Behaviour

The exercise picker (`ExercisesScreen` in `pickerMode = true`) is currently opened from **6 entry points** across 2 presentation shells:

### Navigation-route entry points (full-screen)
1. **Template builder "Add Exercises" button** (PURE_GYM / unblocked exercises) — navigates to `exercise_picker` route, multi-select
2. **FunctionalBlockWizard Step 3** — navigates to `exercise_picker?functionalFilter=true`, multi-select
3. **"Add exercise to block" overflow menu** — navigates to `exercise_picker?functionalFilter=true`, multi-select
4. **AI Workout screen "swap exercise"** — navigates to `exercise_picker` route, single selection used (swap one exercise)

### Inline ModalBottomSheet entry points
5. **ActiveWorkoutScreen "ADD EXERCISE"** — renders `ExercisesScreen(pickerMode=true)` inside a `ModalBottomSheet`, multi-select
6. **ActiveWorkoutScreen "Replace Exercise"** — renders `ExercisesScreen(pickerMode=true)` inside a `ModalBottomSheet`, only first selection used

All six must produce an identical picker experience (same filter chips visible, same search bar layout, same selection style).

**Standardisation rules:**
- All picker invocations use the same `ExercisesScreen(pickerMode = true, ...)` composable with no layout variations between call sites.
- The only permitted difference between call sites is the **initial state** of filter chips (functional filter pre-set vs. not).
- The inline `ModalBottomSheet` entries (#5, #6) should render identically to the full-screen navigation entries — confirm filter chips and the multi-select FAB are not clipped or hidden in the sheet layout.
- Remove any legacy picker navigation path that renders a different composable or layout.

---

## UI Changes

- Audit all 6 navigation paths and confirm they all render `ExercisesScreen(pickerMode = true, ...)`.
- Ensure filter chips, search bar, and multi-select FAB button are present and styled identically across all entry points.
- For the `ModalBottomSheet` entries (#5, #6): confirm the FAB is not hidden behind the sheet's drag handle, and that filter chips scroll correctly inside the constrained sheet height.
- If there are any conditional layouts or distinct composables used for different entry points, consolidate them.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — verify single composable handles all picker modes
- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderScreen.kt` — verify all "add" navigation calls use the same route
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` — verify single exercise_picker route definition
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — verify ModalBottomSheet pickers (#5, #6) render identically to full-screen entries
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt` — verify AI workout swap picker (#4) uses the same composable

---

## How to QA

1. Open a PURE_GYM routine → tap "+". Note the picker layout (search bar, filter chips, FAB).
2. Open a PURE_FUNCTIONAL routine → tap "+" → complete wizard → reach exercise picker. Verify identical layout to step 1.
3. In a PURE_FUNCTIONAL routine with an existing block → tap block overflow → "Add exercise to block". Verify identical layout.
4. In an active workout → tap "ADD EXERCISE". Verify the ModalBottomSheet picker shows search bar, filter chips, and FAB — all visible and not clipped.
5. In an active workout → long-press an exercise → "Replace Exercise". Verify the ModalBottomSheet picker looks identical to step 4.
6. In an AI-generated workout preview → tap a fuzzy-matched exercise to swap it. Verify the picker layout matches steps 1–3.
7. All 6 entries: verify search bar, filter chips, and "Add X exercises" FAB are all present and identically styled.
