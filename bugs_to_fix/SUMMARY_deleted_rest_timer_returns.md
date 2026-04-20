# Fix Summary: Deleted rest timers reappear after reopen

## Bug
`BUG_deleted_rest_timer_returns` — P1 high

## Root Cause
`deleteRestSeparator()` in `WorkoutViewModel` mutated only the in-memory `hiddenRestSeparators` set (part of `ActiveWorkoutState`). This set resets on every workout restart, so the rest timer returned to its original value on next load because `exercises.restDurationSeconds` in Room was never updated.

## Changes

### `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt`
- `deleteRestSeparator()`: when called for a passive separator (timer not active), now calls `exerciseDao.updateRestDuration(exerciseId, 0)` inside `viewModelScope.launch` to persist zero to Room.
- Also mirrors the zero into in-memory `ActiveWorkoutState.exercises` for immediate UI consistency.
- Active timer path unchanged: still calls `stopRestTimer()`.

### `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt`
Added 4 tests:
1. `deleteRestSeparator on passive separator persists restDuration zero to exercise` — verifies `exerciseDao.updateRestDuration(99L, 0)` called
2. `deleteRestSeparator on passive separator updates in-memory exercise restDurationSeconds to zero`
3. `deleteRestSeparator on passive separator adds key to hiddenRestSeparators`
4. `deleteRestSeparator on active timer does NOT persist rest duration zero`

## Tests
BUILD SUCCESSFUL — all 673+ tests pass including 4 new tests for this bug.
