# BUG: START BLOCK button has no effect for functional blocks

## Status
[x] Fixed

## Severity
P1 high — core functional training flow is completely blocked; users cannot start any AMRAP / RFT / EMOM / TABATA block

## Description
In `ActiveWorkoutScreen`, the `▶ START BLOCK` button rendered inside each functional block header does nothing when tapped. Per spec (§9.8), tapping it should call `WorkoutViewModel.startFunctionalBlock(blockId)` which launches `FunctionalBlockRunner`, which opens the appropriate full-screen overlay (AmrapOverlay / RftOverlay / EmomOverlay / TabataOverlay). The button is visually present but the click handler is either missing, wired to a no-op, or the navigation/show-overlay logic is not connected.

Reported against RFT; expected to affect all four functional block types.

## Steps to Reproduce
1. Create a routine with at least one functional block (e.g. RFT — 5 rounds).
2. Start a workout from that routine.
3. In `ActiveWorkoutScreen`, find the `BlockHeader` row for the RFT block.
4. Tap `▶ START BLOCK`.
5. Observe: nothing happens; no overlay opens, no timer starts.

## Dependencies
- **Depends on:** FUNC_ACTIVE_FUNCTIONAL_RUNNER (`completed`) — runner code must exist
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`, `ui/workout/FunctionalBlockRunner.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md §9.8` — pre-start state
- Related spec: `future_devs/FUNC_ACTIVE_FUNCTIONAL_RUNNER_SPEC.md`

## Fix Notes
Root cause: the functional block overlay composables (`AmrapOverlay`, `RftOverlay`, `EmomOverlay`, `TabataOverlay`) were placed **before** the main `Box(fillMaxSize)` in `ActiveWorkoutScreen`. In Compose, when multiple siblings share the same Box parent (the NavHost container), later children draw on top — so the main workout content was covering the overlay entirely, even though the `FunctionalBlockRunner` was starting correctly and emitting state.

Fix: moved the `if (fb != null) { when (fb.blockType) { ... } }` block to be the **last child** inside the main `Box(fillMaxSize)`, after the `SnackbarHost`. Being last in the Box ensures the overlay renders above all workout content and responds to touch events correctly.

No changes to ViewModel, runner, or overlay composable logic — the fix is purely a rendering-order correction in `ActiveWorkoutScreen.kt`.

5 new unit tests added to `WorkoutViewModelTest`: `startFunctionalBlock` sets `activeBlockId`, `startFunctionalBlock` delegates to runner, runner state propagates to `workoutState.functionalBlockState`, `abandonFunctionalBlock` clears `activeBlockId`, `finishFunctionalBlock` clears `activeBlockId`.
