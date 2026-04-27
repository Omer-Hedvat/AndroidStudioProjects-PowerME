# BUG: Active workout kebab menus — missing options and strength card inconsistency

## Status
[x] Fixed

## Severity
P1 high
- Functional block cards expose no way to add session/sticky notes, warmups, or replace/organize; strength card organise option is inconsistently hidden for superset exercises.

## Description
Two related issues in the active workout screen's kebab (⋮) menus:

**1. Functional block kebab missing options**
The `FunctionalBlockActiveCard` kebab currently only shows "Edit Block" and "Remove Block".
It should also expose:
- Session Note
- Sticky Note
- Add Warmups (conditional: same eligibility logic as strength card)
- Replace Block (swap entire block type / params, or replace the block in the routine)
- Organize Exercises (reorder / manage exercises within the block)
- Organize Blocks (reorder all blocks in the workout)

**2. Strength card "Organize Exercises" → "Organize Blocks" + always visible**
In `ManagementHubSheet` (strength exercise kebab):
- Rename "Organize Exercises" → "Organize Blocks"
- Remove the `isInSuperset` conditional that currently swaps it for "Remove from Superset" — always show "Organize Blocks" as a standalone item
- "Remove from Superset" can remain as a separate item when the exercise is in a superset

## Steps to Reproduce
1. Start an active workout with a functional block (AMRAP/RFT/EMOM/TABATA).
2. Tap ⋮ on the block card.
3. Observe: only "Edit Block" and "Remove Block" — no note, warmup, or organise options.

4. Start a workout with a superset (two exercises linked).
5. Tap ⋮ on either superset exercise.
6. Observe: "Organize Exercises" becomes "Remove from Superset" — organise option disappears.

## Dependencies
- **Depends on:** BUG_active_workout_kebab_nav_overlap ✅
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
