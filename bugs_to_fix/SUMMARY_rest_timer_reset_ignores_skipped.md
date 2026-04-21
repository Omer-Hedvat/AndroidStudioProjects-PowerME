# Fix Summary: "Update Rest Timers" restores separators that were already skipped or finished

## Root Cause
`hiddenRestSeparators` was used for two distinct reasons — manual swipe AND timer-expired/skipped. `updateExerciseRestTimers()` cleared all entries for the exercise prefix indiscriminately, restoring separators that should have stayed hidden.

## Files Changed
| File | Change |
|---|---|
| `WorkoutViewModel.kt` | Added `finishedRestSeparators: Set<String>` to `ActiveWorkoutState`. Timer-expired (`onTimerFinish`, in-process coroutine) and skipped (`skipRestTimer`) keys now go to `finishedRestSeparators`. Manual-swipe keys (`deleteRestSeparator`, `deleteSet`) remain in `hiddenRestSeparators`. `updateExerciseRestTimers()` only clears `hiddenRestSeparators`. |
| `ActiveWorkoutScreen.kt` | Passes `hiddenRestSeparators + finishedRestSeparators` as the combined set for separator visibility checks. |
| `WorkoutViewModelTest.kt` | Updated `onTimerFinish` and `skipRestTimer` tests to assert correct set; added `updateExerciseRestTimers does NOT restore finishedRestSeparators` test. |
| `WORKOUT_SPEC.md` | §9.1 updated with two-set distinction; §4.5 updated to reference `finishedRestSeparators`. |

## Surfaces Fixed
- "Update Rest Timers" (Management Hub) no longer restores rest separators whose timers already expired naturally or were explicitly skipped. Only manually swiped-away separators reappear.

## How to QA
1. Start a workout, complete two sets — let the first rest timer run to zero (or skip it).
2. Open Management Hub → "Set Rest Timers" → change a duration → tap **UPDATE REST TIMERS**.
3. Verify: the finished/skipped rest separator does NOT reappear.
4. Separately, swipe a rest separator away, then repeat step 2 — that separator DOES reappear (manual swipe is restored correctly).
