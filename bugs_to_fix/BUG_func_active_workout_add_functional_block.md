# BUG: Active workout Add button always opens strength picker regardless of workout style

## Status
[x] Fixed

## Severity
P1 high
- Core functional training flow is broken: Pure Functional users cannot add a functional block mid-workout

## Description
When a workout's style is `PURE_FUNCTIONAL`, tapping the Add button in `ActiveWorkoutScreen` opens the strength exercise picker instead of the `FunctionalBlockWizard`. The Add button dispatch logic in the active workout screen does not check `workoutStyle`, so it always defaults to the strength picker path.

In `TemplateBuilderScreen`, the Add button correctly dispatches by style: PURE_GYM → exercise picker, PURE_FUNCTIONAL → FunctionalBlockWizard, HYBRID → AddBlockOrExerciseSheet. The same dispatch logic needs to be applied in `ActiveWorkoutScreen`.

## Steps to Reproduce
1. Go to Settings → Workout Style → set to "Pure Functional".
2. Start any workout (or create a new routine and start it).
3. In the active workout screen, tap the Add button (FAB or equivalent).
4. Observe: the strength exercise picker opens instead of the functional block wizard.
5. Expected: `FunctionalBlockWizard` opens, allowing the user to add a new functional block.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `WORKOUT_SPEC.md`

## Fix Notes
Exposed `workoutStyle: StateFlow<WorkoutStyle>` in `WorkoutViewModel` (was private). In `ActiveWorkoutScreen`, the "ADD EXERCISE" button now dispatches by style: PURE_GYM → strength exercise picker, PURE_FUNCTIONAL → `FunctionalBlockWizard`, HYBRID → `AddBlockOrExerciseSheet`. After the wizard creates a `DraftBlock`, the exercise picker opens with `pendingDraftBlock` set; on confirmation the selections are passed to `viewModel.addFunctionalBlock(draft, exercises)`. Added `addFunctionalBlock(DraftBlock, List<Exercise>)` to `WorkoutViewModel` — creates a `WorkoutBlock` row in the DB, appends it to `ActiveWorkoutState.blocks`, then calls `addExercise(exercise, blockId)` for each selection. `addExercise` gained a `blockId: String? = null` parameter propagated into `ExerciseWithSets.blockId`.
