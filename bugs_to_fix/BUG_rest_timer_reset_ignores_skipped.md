# BUG: "Update Rest Timers" restores separators that were already skipped or finished

## Status
[x] Fixed

## Severity
P2 normal

## Description
When the user opens Management Hub → "Set Rest Timers" and confirms, the ViewModel clears ALL entries in `hiddenRestSeparators` for that exercise. This means separators that were hidden because their timer naturally expired or was explicitly skipped (via TimerControlsSheet → Skip) are incorrectly restored alongside those that were swiped away. Only separators hidden by a manual swipe-to-delete should be restored; finished/skipped ones should remain hidden.

## Steps to Reproduce
1. Start a workout, complete two sets — let the first rest timer run to zero (or skip it via TimerControlsSheet).
2. Open Management Hub → "Set Rest Timers" → change a duration → tap **UPDATE REST TIMERS**.
3. Observe: the finished/skipped rest separator reappears.

Expected: only manually swiped-away separators are restored. Finished/skipped ones stay hidden.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §9.1`

## Fix Notes
Introduced `finishedRestSeparators: Set<String>` alongside the existing `hiddenRestSeparators` in `ActiveWorkoutState`.
- Timer-expired/skipped keys go to `finishedRestSeparators`.
- Manually-swiped keys stay in `hiddenRestSeparators`.
- `updateExerciseRestTimers()` clears only `hiddenRestSeparators` for that exercise prefix.
- UI combines both sets when checking separator visibility.
- WORKOUT_SPEC.md §9.1 updated with the two-set distinction.
