# Fix Summary: Quick Start — Add Exercise only adds the first selected exercise

## Root Cause
`addExercise()` captured `_workoutState.value.exercises` into a local `currentExercises` val before
launching a coroutine that suspends on DB queries. When the exercise picker's `forEach` loop fired
multiple `addExercise()` calls in rapid succession, all coroutines captured the same stale snapshot.
Each one then executed `_workoutState.update { it.copy(exercises = currentExercises + newExercise) }`,
so only the last coroutine to finish survived — all prior adds were overwritten.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Changed `currentExercises + newExerciseWithSets` to `it.exercises + newExerciseWithSets` inside `_workoutState.update` so each atomic update appends to the live state |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added regression test: `addExercise called in rapid succession adds all exercises not just the first` |

## Surfaces Fixed
- Active Workout (Quick Start mode) — Add Exercise picker — selecting multiple exercises now adds all of them

## How to QA
1. Start a Quick Start workout (Workouts tab → Quick Start).
2. Tap **Add Exercise**, select 3 or more exercises, confirm.
3. Verify all selected exercises appear in the workout — not just the first one.
