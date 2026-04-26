# BUG: No way to edit a functional block's parameters after routine creation

## Status
[ ] Open

## Severity
P1 high
- Users cannot change block type, duration, rounds, or time cap after the initial wizard — they must delete the block and recreate it from scratch

## Description
Once a functional block (AMRAP / RFT / EMOM / TABATA) has been created via the FunctionalBlockWizard, there is no way to edit its parameters (block type, duration/rounds, time cap). The only available overflow actions are "Add exercise to block" and "Delete block". There is no "Edit block" option that re-opens the wizard pre-filled with the current values.

Expected behaviour:
- A functional block's `BlockHeader` overflow menu (or a tap on the header itself) should offer an **Edit block** action.
- Tapping it re-opens the FunctionalBlockWizard with the current block parameters pre-filled (type, duration, rounds, time cap).
- Saving updates the existing block in-place; cancelling leaves it unchanged.

## Steps to Reproduce
1. Create a PURE_FUNCTIONAL routine with an AMRAP block (12 min, 2 exercises).
2. Save and reopen the routine in the template builder.
3. Look for a way to change the AMRAP duration from 12 to 15 minutes.
4. Observe: no "Edit block" option exists in the block's overflow menu.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt`, `ui/workouts/FunctionalBlockWizard.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `future_devs/FUNC_TEMPLATE_WIZARD_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
