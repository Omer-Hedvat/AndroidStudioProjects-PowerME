# Fix Summary: Warmup sets collapse simultaneously with rest separator instead of staggered

## Root Cause
All three warmup-collapse code paths (`onTimerFinish`, in-process coroutine, `skipRestTimer`) set both the rest separator hidden state AND `collapsedWarmupExerciseIds` in the same atomic `_workoutState.update`, so both animations fired simultaneously.

## Files Changed
| File | Change |
|---|---|
| `WorkoutViewModel.kt` | Added `finishedRestSeparators` field to `ActiveWorkoutState`. In all three collapse paths, separator hidden state is set immediately then a `delay(500)` coroutine triggers the warmup row collapse. |
| `ActiveWorkoutScreen.kt` | Pass `hiddenRestSeparators + finishedRestSeparators` to `ExerciseCard` (both pass-through sites). |
| `WorkoutViewModelTest.kt` | Updated existing stagger/skip tests; added new `onTimerFinish` stagger test. |
| `WORKOUT_SPEC.md` | §5.1 updated with staggered-collapse behavior; §4.5 updated to reference `finishedRestSeparators`. |

## Surfaces Fixed
- After the last warmup-to-work rest timer ends or is skipped, the rest separator collapses first, then 500ms later the warmup set rows animate out — instead of both collapsing simultaneously.

## How to QA
1. Start a workout with an exercise that has warmup sets (W-type sets).
2. Complete all warmup sets.
3. Let the last warmup-to-work rest timer run to zero (or tap Skip in TimerControlsSheet).
4. Observe: rest separator collapses first, then ~500ms later the warmup rows shrink and disappear.
