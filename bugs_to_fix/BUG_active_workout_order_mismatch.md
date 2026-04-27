# BUG: Active workout exercise order does not match organize workout order

## Status
[x] Fixed

## Severity
P1 high

## Description
When a user reorders exercises or blocks via the "Organize Workout" screen, the active workout screen does not reflect that order. The workout renders in a different sequence from what the user arranged.

## Steps to Reproduce
1. Start a workout from a saved routine (or start a blank workout and add multiple exercises).
2. Open "Organize Workout" and drag exercises/blocks into a custom order.
3. Save the order and return to the active workout screen.
4. Observe: exercise/block order in the active screen does not match the organized order.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
`reorderOrganizeItem` in `WorkoutViewModel` updated `state.exercises` order but never `state.blocks`. Normal-mode rendering iterates `workoutState.blocks` (not `exercises`), so blocks always appeared in their original order. After reordering exercises, the fix recomputes `blocks` order by taking the first-appearance of each blockId in the new exercises list and propagates it into the copied state.
