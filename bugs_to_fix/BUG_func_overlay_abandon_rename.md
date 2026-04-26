# BUG: "Abandon" button in functional overlays should exit only the block, not the whole workout

## Status
[x] Fixed

## Severity
P2 normal
- Wrong behavior: abandoning a block exits the entire workout; user expects to return to the active workout screen

## Description
All four functional block overlays (AMRAP, RFT, EMOM, TABATA) have an "ABANDON" button that calls cancelWorkout() + onWorkoutFinished(), discarding the entire session. The expected behaviour is:
- "Abandon Block" stops only the current functional block (calls abandonFunctionalBlock()).
- The overlay closes and the user returns to the standard active workout screen.
- The overall workout continues — other blocks and exercises are unaffected.

## Steps to Reproduce
1. Start any functional block overlay (AMRAP / RFT / EMOM / TABATA).
2. Observe the bottom-left button labeled "ABANDON".
3. Compare with the rest timer cancel flow — it says "Cancel Workout".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
Two changes: (1) All four overlays now show "Abandon Block" on the button and in the confirmation dialog (title "Abandon block?", body "The block result won't be saved. You'll return to the workout."). (2) ActiveWorkoutScreen wired onAbandonClick to viewModel.abandonFunctionalBlock() instead of cancelWorkout() + onWorkoutFinished(), so only the functional block runner is stopped — the workout session continues and the UI falls back to the regular active workout view.
