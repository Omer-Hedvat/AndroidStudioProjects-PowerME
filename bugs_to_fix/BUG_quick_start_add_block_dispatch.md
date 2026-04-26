# BUG: Quick-start "Add Exercise" button should be "Add Block" for Hybrid/Pure Functional workouts

## Status
[x] Fixed

## Severity
P1 high
- Users in Hybrid or Pure Functional workout styles hit an "Add Exercise" flow that doesn't surface the functional block path prominently enough; the correct first action for these styles is "Add Block"

## Description
In the active workout screen, the floating Add button (or equivalent CTA after quick-start) always says "Add Exercise". For Hybrid and Pure Functional workout styles, the first action should be "Add Block" — the same dispatch that already exists when the workout is started from a routine template. The button label and its tap behaviour need to be conditioned on `workoutStyle`:

- `PURE_GYM` → "Add Exercise" (current behaviour, correct)
- `HYBRID` → "Add Block or Exercise" sheet (AddBlockOrExerciseSheet) — already wired for routine-based workouts but not for quick-start
- `PURE_FUNCTIONAL` → "Add Block" → opens FunctionalBlockWizard directly

Root cause: the Add button dispatch in `ActiveWorkoutScreen.kt` likely defaults to the exercise picker regardless of style, or the style is not being passed correctly in the quick-start path.

## Steps to Reproduce
1. Go to Settings → set Workout Style to **Hybrid** (or Pure Functional).
2. Tap **Quick Start** to begin a free-form workout.
3. Tap the **Add Exercise** button.
4. Observe: opens the exercise picker directly instead of "Add Block or Exercise" / "Add Block" flow.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
- Added `import com.powerme.app.data.WorkoutStyle` to `ActiveWorkoutScreen.kt` (was using fully-qualified names).
- Added `workoutStyle: WorkoutStyle = WorkoutStyle.PURE_GYM` parameter to `activeWorkoutListItems`.
- Pass `workoutStyle` from `ActiveWorkoutScreen` down to `activeWorkoutListItems` at the call site.
- Button label now reads "ADD BLOCK" (PURE_FUNCTIONAL), "ADD BLOCK OR EXERCISE" (HYBRID), or "ADD EXERCISE" (PURE_GYM) to match style-aware dispatch already present in `onShowExerciseDialog`.
