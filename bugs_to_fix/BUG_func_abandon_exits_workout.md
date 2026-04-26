# BUG: "Abandon" exits block but does not cancel the workout session

## Status
[x] Fixed

## Severity
P1 high

## Description
Tapping "Abandon" in a functional block overlay currently calls `FunctionalBlockRunner.abandon()`, which stops the timer and clears block state without saving a block result — correct so far. However, the user expects "Abandon" to also exit the entire workout session (equivalent to "Cancel Workout"), leaving nothing in history.

Currently, after abandoning a block, the user is returned to the active workout screen with the block still listed but no result. The workout session itself is NOT cancelled — it can be finished normally afterwards, writing a partial workout to history that the user never wanted saved.

Expected behaviour: tapping "Abandon" in any functional overlay should cancel the whole workout session and navigate back (same as tapping "Cancel Workout" on the main active workout screen). The existing `AlertDialog` wording ("Abandon block?") should be updated to reflect this broader scope.

## Steps to Reproduce
1. Start any functional block workout.
2. Launch the block overlay.
3. Tap "Abandon" → confirm.
4. Observe: returned to active workout screen (not discarded).
5. The workout can still be finished and written to history — not the expected result.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`, `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `WORKOUT_SPEC.md`

## Fix Notes
`cancelWorkout()` in `WorkoutViewModel` now calls `functionalBlockRunner.abandon()` first when the runner is active, then proceeds with its existing full teardown. All four overlay `onAbandonClick` lambdas in `ActiveWorkoutScreen` now call `viewModel.cancelWorkout(); onWorkoutFinished()` instead of `viewModel.abandonFunctionalBlock()`. Dialog wording updated to "Abandon workout?" / "The entire workout session will be discarded."
