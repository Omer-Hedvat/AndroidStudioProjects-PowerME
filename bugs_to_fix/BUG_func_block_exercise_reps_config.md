# BUG: Functional block exercises default to 0 reps with no way to configure reps/time

## Status
[x] Fixed

## Severity
P1 high
- Blocks users from running functional blocks with correct rep/time prescriptions when exercises are added during an active workout

## Description
When exercises are added to a functional block (RFT, AMRAP, EMOM, TABATA) during an active workout via the "Add Functional Block" flow, they are initialised with `reps = "0"` and `timeSeconds = ""` (from the `addExercise` fallback path in WorkoutViewModel). There is also no UI in the active workout block card to change these values — the prescription text (`FunctionalExerciseRow` in `ActiveWorkoutScreen.kt`) is currently display-only.

Root causes:
1. `WorkoutViewModel.addExercise` (line ~1149): no-prior-session fallback creates `ActiveSet(reps = "0")` with no sensible default for functional block context.
2. `FunctionalBlockActiveCard` / `FunctionalExerciseRow` in `ActiveWorkoutScreen.kt`: shows prescription as a static text string with no editable field.

Fix requires:
- A sensible default (e.g. 10 reps / 30 s) when adding an exercise to a functional block with no prior session.
- Make the prescription number editable inline in the active workout block card (small input field or stepper) so users can set it before starting the block.

## Steps to Reproduce
1. Start a quick-start workout (Hybrid or Pure Functional style).
2. Tap Add → Add Functional Block → choose RFT.
3. Add exercises (e.g. American KB Swing, Air Squat).
4. Observe: both exercises show **0 reps** with no stepper or input to change the value.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`, `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
- `WorkoutViewModel.addExercise`: when `blockId != null` and no prior session exists, defaults to `ActiveSet(reps="10")` for rep-based exercises and `ActiveSet(timeSeconds="30")` for `ExerciseType.TIMED` instead of the old `reps="0"` fallback.
- `FunctionalExerciseRow`: replaced static `Text` with an inline editable `BasicTextField` inside a `Surface` box (52dp wide), showing the exercise name and "reps"/"sec" label alongside. Callbacks `onRepsChanged`/`onTimeChanged` wired through `FunctionalBlockActiveCard`.
- 3 new unit tests added to `WorkoutViewModelTest` covering strength+TIMED functional defaults and the unchanged strength-only fallback.
