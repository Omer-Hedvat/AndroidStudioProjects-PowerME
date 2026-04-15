# Fix Summary: Start workout button broken after editing a routine

## Root Cause
`saveRoutineEdits()` resets state with `editModeSaved = true`. When the user later tapped "Start Workout", `navController.navigate(Routes.WORKOUT)` fired before the coroutine building the workout completed. `ActiveWorkoutScreen` composed with `editModeSaved` still `true`, its `LaunchedEffect` immediately called `onWorkoutFinished()`, and the screen popped back to Workouts before the workout was ready.

## Files Changed
| File | Change |
|---|---|
| `ui/workout/WorkoutViewModel.kt` | Added `_workoutState.update { it.copy(editModeSaved = false) }` synchronously before `viewModelScope.launch` in both `startWorkoutFromRoutine()` and `startWorkout()` |
| `src/test/.../WorkoutViewModelTest.kt` | Added regression test: `startWorkoutFromRoutine after saveRoutineEdits clears editModeSaved before coroutine runs` |

## Surfaces Fixed
- Tapping "Start Workout" after exiting edit mode now opens the active workout screen directly instead of bouncing back to the Workouts tab

## How to QA
1. Open the Workouts tab → tap any routine → tap **Edit**
2. Make any change → tap **Done** (app returns to Workouts tab)
3. Tap the same routine → tap **Start Workout**
4. **Expected:** ActiveWorkoutScreen opens immediately with the workout running
5. **Not expected:** app stays on Workouts tab with a "Resume Workout" banner
