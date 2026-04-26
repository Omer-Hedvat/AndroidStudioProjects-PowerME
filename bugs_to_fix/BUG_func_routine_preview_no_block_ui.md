# BUG: Routine preview and edit mode show flat exercise list instead of functional block structure

## Status
[x] Completed

## Severity
P2 — visual regression; functional routines are usable but lose all block structure context

## Description
When a user taps a routine card in the Workouts tab, the routine detail/preview composable renders all exercises as a flat list, identical to a pure strength routine. Functional blocks (AMRAP, RFT, EMOM, TABATA) have no block-type badge, no parameters (duration / rounds / time cap), and no grouped card layout. The same flat rendering also applies in edit mode (TemplateBuilderScreen when re-opened on an existing functional routine).

Affected screens: Workouts tab routine preview, TemplateBuilderScreen (edit mode).

## Steps to Reproduce
1. Create a routine with workout style Pure Functional or Hybrid.
2. Add at least one functional block (e.g. AMRAP 10 min with 2+ exercises).
3. Save the routine and return to the Workouts tab.
4. Tap the routine card to open the preview.
5. Observe: exercises are shown as a flat list with no block type, duration, or grouped card.

## Dependencies
- **Depends on:** func_active_block_card_ui ✅, func_block_card_layout ✅
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/workouts/WorkoutsScreen.kt`, `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `future_devs/FUNC_TEMPLATE_BUILDER_POLISH_SPEC.md` (Scope 1)

## Fix Notes
Three-layer fix:
1. `WorkoutsViewModel` — injected `RoutineBlockDao`; `loadRoutineDetails` now loads blocks alongside exercises into a new `routineBlocks: StateFlow`; `clearRoutineDetails` clears both.
2. `WorkoutsScreen` — `RoutineOverviewSheet` receives `blocks: List<RoutineBlock>`; new `buildRoutineSections` helper groups exercises by blockId (blocks first matching ActiveWorkoutScreen order, unblocked last); new `RoutineBlockPreviewHeader` composable renders type badge + params for functional blocks inside a `surfaceVariant` Card.
3. `WorkoutViewModel.startEditMode` — now loads `RoutineBlock` entries and synthesises in-memory `WorkoutBlock` objects (same IDs, no DB write), sets `blockId` on each `ExerciseWithSets`, and populates `workoutState.blocks` so `showBlockHeaders` fires correctly in `ActiveWorkoutScreen`.
