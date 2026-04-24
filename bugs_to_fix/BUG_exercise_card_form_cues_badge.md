# BUG: Exercise card shows spurious "Form cues" badge

## Status
[x] Fixed

## Severity
P3 low — cosmetic; the badge is confusing and adds no actionable UX value at this time

## Description
Every exercise card on the Exercises screen that has `setupNotes` populated shows a gold "Form cues" badge (info icon + label). The badge was never part of any UX spec and the user didn't recognise it. It should be removed.

## Steps to Reproduce
1. Open Exercises tab.
2. Observe any exercise card that has setup notes (e.g. Battle Ropes, Bear Crawl, Belt Squat).
3. A gold "ⓘ Form cues" label appears below the rest-timer indicator.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt`

## Assets
- Related spec: `EXERCISES_SPEC.md`

## Fix Notes
Remove the `if (exercise.setupNotes?.isNotBlank() == true)` block (and its `MetaItem` call) from the exercise card composable in `ExercisesScreen.kt`.
