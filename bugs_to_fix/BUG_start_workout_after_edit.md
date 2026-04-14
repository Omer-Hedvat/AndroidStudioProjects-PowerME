# BUG: Start Workout navigates to Workouts tab instead of Active Workout after editing a routine

## Status
[x] Fixed

## Description
After finishing a routine edit and being returned to the Workouts tab, pressing "Start Workout" on the same (or any) routine navigates to the Workouts tab instead of opening the active workout screen. The user must then manually press "Resume Workout" to enter the running workout.

## Steps to Reproduce
1. Open the Workouts tab → tap a routine → tap **Edit**
2. Make any change in edit mode → tap **Done** (saves the routine)
3. App returns to the Workouts tab (correct)
4. Tap the same routine → tap **Start Workout**
5. **Expected:** app opens the ActiveWorkoutScreen with the running workout
6. **Actual:** app stays on / returns to the Workouts tab; "Resume Workout" banner appears

## Assets
- Video: `bugs_to_fix/assets/start_workout_after_edit/recording.mp4`

## Root Cause
`saveRoutineEdits()` in `WorkoutViewModel` resets the state to a fresh `ActiveWorkoutState` with `editModeSaved = true`:

```kotlin
// WorkoutViewModel.kt ~line 632
_workoutState.update {
    ActiveWorkoutState(
        availableExercises = it.availableExercises,
        editModeSaved = true   // ← flag stays true after navigation back to WorkoutsScreen
    )
}
```

`ActiveWorkoutScreen` has a `LaunchedEffect` keyed on `editModeSaved`:

```kotlin
// ActiveWorkoutScreen.kt ~line 111
LaunchedEffect(workoutState.editModeSaved) {
    if (workoutState.editModeSaved) {
        onWorkoutFinished()   // pops the workout route
    }
}
```

When the user later presses "Start Workout", `startWorkoutFromRoutine()` is called. It launches a coroutine to build the workout, and `navController.navigate(Routes.WORKOUT)` fires immediately after (the function returns before the coroutine completes). The app navigates to `ActiveWorkoutScreen` while `editModeSaved` is **still `true`** in the shared ViewModel state. The LaunchedEffect fires instantly, calls `onWorkoutFinished()`, and pops the route back to Workouts — before the coroutine ever finishes setting up the workout.

## Fix
In `startWorkoutFromRoutine()` (and defensively `startWorkout()`), synchronously clear `editModeSaved` **before** launching the coroutine, so by the time `ActiveWorkoutScreen` is composed the flag is already `false`:

```kotlin
// WorkoutViewModel.kt — startWorkoutFromRoutine()
fun startWorkoutFromRoutine(routineId: String) {
    if (_workoutState.value.isMinimized || _workoutState.value.isActive) {
        maximizeWorkout()
        return
    }
    _workoutState.update { it.copy(editModeSaved = false) }  // ← ADD THIS LINE
    viewModelScope.launch {
        ...
    }
}
```

Same one-liner fix for `startWorkout()` if `editModeSaved` can be true when it's called.

**Files to change:**
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt`
  - `startWorkoutFromRoutine()` (~line 444)
  - `startWorkout()` (check if same issue applies)

**Tests to add/update:**
- `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt`
  - New test: `startWorkoutFromRoutine after saveRoutineEdits clears editModeSaved before coroutine runs`

## Fix Notes
Added `_workoutState.update { it.copy(editModeSaved = false) }` synchronously (before `viewModelScope.launch`) in both `startWorkoutFromRoutine()` and `startWorkout()` in `WorkoutViewModel.kt`. This ensures the flag is cleared before `navController.navigate(Routes.WORKOUT)` fires, so `ActiveWorkoutScreen`'s `LaunchedEffect` never sees a stale `true`. One regression test added to `WorkoutViewModelTest`. Video in assets can be deleted.
