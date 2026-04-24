# SUMMARY: BUG_exercise_card_form_cues_badge

## What was wrong
Every exercise card on the Exercises screen displayed a gold "ⓘ Form cues" badge when `setupNotes` was non-blank. The badge was never part of a UX spec and confused the user.

## Fix
Removed the `if (exercise.setupNotes?.isNotBlank() == true)` block and its `MetaItem("Form cues", ...)` call from `ExercisesScreen.kt`. Also removed the now-unused `FormCuesGold` import from that file.

## Files changed
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt`
