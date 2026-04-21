# BUG: Warmup sets collapse simultaneously with rest separator instead of staggered

## Status
[x] Fixed

## Severity
P2 normal

## Description
When the last rest timer between warmup (WU) sets and work sets (WS) finishes, both the rest separator and the WU set rows collapse at the same moment. The desired behaviour is a staggered collapse: rest separator collapses first, then WU sets collapse 500ms later. This gives the user a visual cue that the warmup phase has ended before the sets disappear, reducing disorientation.

## Steps to Reproduce
1. Start a workout with an exercise that has warmup sets (W-type sets).
2. Complete all warmup sets; let the last warmup-to-work rest timer run to zero.
3. Observe: rest separator and warmup set rows all collapse simultaneously.

Expected: rest separator collapses first → 500ms pause → warmup set rows collapse.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §4.5, §5.1`

## Fix Notes
All three warmup-collapse code paths (`onTimerFinish`, in-process coroutine, `skipRestTimer`) were updated:
1. The rest separator key is added to `finishedRestSeparators` immediately.
2. A `delay(500)` coroutine then adds `exerciseId` to `collapsedWarmupExerciseIds`.

WORKOUT_SPEC.md §5.1 updated with staggered collapse description.
