# BUG: Routine summary and edit pages look identical to strength routines — no functional block structure shown

## Status
[ ] Open

## Severity
P2 normal
- The routine detail screen gives no visual indication of functional block type, duration, rounds, or structure — a PURE_FUNCTIONAL routine looks identical to a PURE_GYM routine

## Description
When the user taps on a routine card in the Workouts tab, the routine summary/preview page and its edit view render all exercises as flat lists with the same strength-training card UI — individual exercise rows with no block grouping, no block type badge (AMRAP / RFT / EMOM / TABATA), and no block parameters (duration, round count, time cap).

The template builder already has the correct grouped-card layout (via `func_block_card_layout`). The routine summary / detail view and the routine edit entry point need the same functional-aware rendering.

Design direction (use /ui-ux-pro-max):
- Functional blocks should render as a card with a colour-coded type badge + parameters at the top, exercises listed below.
- STRENGTH blocks / unblocked exercises keep the existing per-exercise card.
- The edit button on a functional block card should open the block params for editing (see BUG_func_no_block_edit).

## Steps to Reproduce
1. Create a PURE_FUNCTIONAL routine with at least one AMRAP block (2 exercises) and one EMOM block (2 exercises).
2. Save the routine and return to the Workouts tab.
3. Tap the routine card to open its summary/detail view.
4. Observe: exercises are listed flat with no block grouping, no AMRAP/EMOM badge, no block parameters.

## Dependencies
- **Depends on:** func_block_card_layout ✅
- **Blocks:** —
- **Touches:** `ui/workouts/WorkoutsScreen.kt`, `ui/workouts/TemplateBuilderScreen.kt` (routine detail/preview composable), `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `WORKOUT_SPEC.md`
- Design tool: invoke `/ui-ux-pro-max` when implementing

## Fix Notes
<!-- populated after fix is applied -->
