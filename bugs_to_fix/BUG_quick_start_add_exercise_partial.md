# BUG: Quick Start — Add Exercise only adds the first selected exercise

## Status
[x] Fixed

## Severity
P1 high — affects core workout flow (adding exercises to an active Quick Start workout)

## Description
When in a Quick Start (no-routine) workout, opening the "Add Exercise" picker and selecting multiple exercises only adds the first one to the workout. Subsequent selections are silently ignored.

Likely root cause area: the exercise picker callback or `WorkoutViewModel.addExercise()` may be called once and then the selection list is not iterated, OR the ViewModel state is refreshed after the first add in a way that discards remaining pending additions.

Affected screens: Active Workout screen (Quick Start mode) → Add Exercise sheet.

## Steps to Reproduce
1. Tap **Workouts** tab → **Quick Start** to begin a free workout.
2. Tap **Add Exercise** (or the + button).
3. In the exercise picker, select 3 or more exercises.
4. Confirm / close the picker.
5. Observe: only the first selected exercise appears in the workout; the rest are missing.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `WorkoutsScreen.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `addExercise()` captured `currentExercises` before launching a coroutine for DB queries. When the picker's `forEach` fired multiple adds in rapid succession, all coroutines captured the same stale snapshot. Each one then wrote `staleList + itsExercise`, so only the last exercise survived.

Fix: changed the `_workoutState.update` lambda from `currentExercises + newExerciseWithSets` to `it.exercises + newExerciseWithSets`, so each atomic update appends to the live state rather than the captured snapshot.

Test added: `addExercise called in rapid succession adds all exercises not just the first` in `WorkoutViewModelTest.kt`.
