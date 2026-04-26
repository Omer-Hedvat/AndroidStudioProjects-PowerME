# BUG: Functional overlay exercise list shows name + reps separately instead of "10 Box Jump" format

## Status
[x] Fixed

## Severity
P2 normal
- Readability at arm's length: separate name/reps columns require eye movement; combined "10 Box Jump" line reads instantly

## Description
All four functional overlays show exercises as two separate text elements: exercise name left-aligned, reps/hold right-aligned. The desired format is a single left-aligned line: "10 Box Jump" (quantity first, then name), matching how athletes read a WOD on a whiteboard.

## Steps to Reproduce
1. Start any functional block with ≥2 exercises.
2. Observe BlockRecipeRow layout: "Box Jump" | "× 10" (two separate columns).
3. Expected: "10 Box Jump" as a single left-aligned line, large enough to read at 2–3 m.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/BlockRecipeRow.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
BlockRecipeRow rewritten: removed the SpaceBetween Row layout (name left, reps right). Replaced with a single Text composable showing "${reps} ${exerciseName}" (or "${holdSeconds}s ${exerciseName}") left-aligned at headlineMedium — matching the whiteboard-style "10 Box Jump" format requested.
