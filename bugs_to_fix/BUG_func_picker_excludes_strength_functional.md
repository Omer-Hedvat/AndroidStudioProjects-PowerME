# BUG: Functional block exercise picker excludes STRENGTH-typed functional exercises

## Status
[x] Fixed

## Severity
P1 high

## Description
When adding exercises to a functional block (from any functional entry point — Pure Functional, Hybrid Add Block, or block overflow "Add exercise to block"), the exercise picker pre-filters to `functional tag ON + CARDIO + PLYOMETRIC` exercise types. This silently excludes every exercise whose `exerciseType=STRENGTH` — even if it carries the `"functional"` tag.

Classic CrossFit / Hyrox movements are all `exerciseType=STRENGTH` in the DB. Examples that are invisible in the functional picker:
- Power Clean, Squat Clean, Hang Power Clean, Clean and Jerk
- Barbell Snatch, Power Snatch, Hang Power Snatch, Dumbbell Snatch, Muscle Snatch, Snatch Balance
- Kettlebell Swing, American Kettlebell Swing, Kettlebell Clean, Kettlebell Snatch
- Front Squat, Dumbbell Front Squat, Thruster, Medicine Ball Clean
- (and many more Olympic / KB / gymnastics strength movements)

All of these have `tags: ["functional"]` or `["functional","olympic"]` but their `exerciseType=STRENGTH` means the CARDIO+PLYOMETRIC-only type gate hides them.

**Root cause:** `EXERCISE_PICKER_TYPE_PREFILTER_SPEC.md` specified `CARDIO, PLYOMETRIC` as the initial type filters for functional entry points, but the intent was to use the `functional` tag as the sole discriminator. The type chip restriction was over-engineered and contradicts actual exercise data.

**Fix:** For all functional-block entry points, drop the ExerciseType pre-filter entirely. Show all exercise types; rely only on `initialFunctionalFilter=true` to surface relevant exercises. The user can still toggle the functional chip off to see everything.

## Steps to Reproduce
1. Create a PURE_FUNCTIONAL or HYBRID routine.
2. Open the template builder → tap "+" to add an exercise to a functional block.
3. In the exercise picker, search "Power Clean".
4. Observe: no results (even though Power Clean is in the DB with `tags: ["functional"]`).
5. Also search "KB Swing", "Front Squat", "Snatch" — same result, all invisible.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/exercises/ExercisesViewModel.kt`, `ui/exercises/ExercisesScreen.kt`, `navigation/PowerMeNavigation.kt`, `ui/workouts/AddBlockOrExerciseSheet.kt`, `ui/workouts/TemplateBuilderScreen.kt`

## Assets
- Related spec: `future_devs/EXERCISE_PICKER_TYPE_PREFILTER_SPEC.md` (needs correction after fix), `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
Removed `&typeFilters=CARDIO,PLYOMETRIC` from the two functional-block exercise picker navigation calls in `TemplateBuilderScreen.kt`:
1. Functional block card's `BlockHeader.onAddExercise` (block overflow "Add exercise to block") — was `"exercise_picker?functionalFilter=true&typeFilters=CARDIO,PLYOMETRIC"`, now `"exercise_picker?functionalFilter=true"`.
2. `FunctionalBlockWizard.onBlockCreated` callback (PURE_FUNCTIONAL and Hybrid "Add Block" flows) — same change.

The `initialFunctionalFilter=true` is preserved as the sole discriminator. `EXERCISE_PICKER_TYPE_PREFILTER_SPEC.md` updated to correct the behaviour table and How to QA section.

New tests added to `ExerciseTagsFilterTest.kt`: verify STRENGTH-typed exercises with functional tag are visible when no type filter is set, document the old broken behaviour, and confirm STRENGTH+TIMED filter still works correctly for the strength entry point.
